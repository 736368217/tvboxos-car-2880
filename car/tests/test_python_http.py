"""Host/guest tests for local transport code, never remote spider execution."""
import base64
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
PYTHON = ROOT / "pyramid/src/python"
sys.path.insert(0, str(PYTHON))
import requests
import tvbox_http as transport


class Bridge:
    def __init__(self, replies=None):
        self.calls = []
        self.replies = list(replies or [{}])

    def request(self, method, url, headers, body, redirects, connect, read):
        self.calls.append({"method": method, "url": url, "headers": json.loads(headers),
                           "body": base64.b64decode(body), "redirects": redirects,
                           "timeout": (connect, read)})
        reply = self.replies.pop(0) if self.replies else {}
        payload = {"status_code": 200, "reason": "OK", "url": url,
                   "header_pairs": [], "body_base64": base64.b64encode(b"ok").decode("ascii")}
        payload.update(reply)
        return json.dumps(payload)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.session = requests.Session()
        self.session.trust_env = False
        self.original = requests.sessions.Session.request
        self.capture = contextlib.redirect_stdout(io.StringIO())
        self.capture.__enter__()

    def tearDown(self):
        requests.sessions.Session.request = self.original
        self.session.close()
        self.capture.__exit__(None, None, None)

    def send(self, bridge, method="GET", url="https://example.test/start", **kwargs):
        return transport.java_request(self.session, method, url, kwargs, bridge)

    def test_form_and_query(self):
        bridge = Bridge()
        self.session.params["session"] = "one"
        self.send(bridge, "POST", data={"q": "a b", "x": ["1", "2"]}, params={"p": "x y"})
        call = bridge.calls[0]
        self.assertEqual(call["body"], b"q=a+b&x=1&x=2")
        self.assertEqual(call["headers"]["Content-Type"], "application/x-www-form-urlencoded")
        self.assertIn("session=one&p=x+y", call["url"])

    def test_binary_round_trip(self):
        content = bytes(range(256))
        bridge = Bridge([{"body_base64": base64.b64encode(content).decode("ascii")}])
        response = self.send(bridge, "POST", data=content)
        self.assertEqual(bridge.calls[0]["body"], content)
        self.assertEqual(response.content, content)
        self.assertEqual(b"".join(response.iter_content(7)), content)
        self.assertEqual(response.raw.read(), content)

    def test_json(self):
        bridge = Bridge([{"header_pairs": [["content-type", "application/json"]],
                          "body_base64": base64.b64encode(b'{"ok":true}').decode("ascii")}])
        response = self.send(bridge, "POST", json={"n": 1})
        self.assertEqual(json.loads(bridge.calls[0]["body"]), {"n": 1})
        self.assertEqual(response.json(), {"ok": True})

    def test_response_contract(self):
        content = "\u4e2d\u6587".encode("gb18030")
        bridge = Bridge([{"header_pairs": [["Content-Type", "text/html; charset=gb18030"]],
                          "body_base64": base64.b64encode(content).decode("ascii")}])
        response = self.send(bridge)
        self.assertIsInstance(response, requests.Response)
        self.assertEqual(response.text, "\u4e2d\u6587")
        self.assertTrue(response.apparent_encoding)
        self.assertEqual(response.headers["content-type"], "text/html; charset=gb18030")
        response.raise_for_status()
        self.assertEqual(response.request.method, "GET")
        response.close()

    def test_error_status(self):
        response = self.send(Bridge([{"status_code": 404, "reason": "Not Found"}]))
        with self.assertRaises(requests.HTTPError):
            response.raise_for_status()

    def test_timeout_tuple(self):
        bridge = Bridge()
        self.send(bridge, timeout=(1.25, 2.5))
        self.assertEqual(bridge.calls[0]["timeout"], (1250, 2500))

    def test_timeout_none_and_validation(self):
        self.assertEqual(transport._timeout_ms(None), (0, 0))
        with self.assertRaises(ValueError):
            self.send(Bridge(), timeout=0)

    def test_transport_timeout(self):
        with self.assertRaises(requests.Timeout):
            self.send(Bridge([{"error": "SocketTimeoutException"}]))

    def test_duplicate_cookie_and_domain_policy(self):
        bridge = Bridge([{"header_pairs": [
            ["Set-Cookie", "a=1; Path=/"],
            ["Set-Cookie", "b=2; Expires=Wed, 09 Jun 2038 10:18:14 GMT; Path=/"],
            ["Set-Cookie", "wrong=3; Domain=other.test; Path=/"]]}])
        response = self.send(bridge)
        self.assertEqual(response.cookies.get("a"), "1")
        self.assertEqual(self.session.cookies.get("b"), "2")
        self.assertIsNone(self.session.cookies.get("wrong"))

    def test_request_cookies_and_session_headers(self):
        bridge = Bridge()
        self.session.headers["X-Session"] = "yes"
        self.session.cookies.set("s", "1", domain="example.test", path="/")
        self.send(bridge, cookies={"per": "2"})
        headers = bridge.calls[0]["headers"]
        self.assertIn("s=1", headers["Cookie"])
        self.assertIn("per=2", headers["Cookie"])
        self.assertEqual(headers["X-Session"], "yes")
        self.assertFalse(bridge.calls[0]["redirects"])
        self.assertNotIn("Accept-Encoding", headers)

    def test_relative_redirect_cookie_and_history(self):
        bridge = Bridge([{"status_code": 302, "header_pairs": [["Location", "/done"],
                           ["Set-Cookie", "redirect=1; Path=/"]]}, {}])
        response = self.send(bridge)
        self.assertEqual(response.url, "https://example.test/done")
        self.assertEqual(len(response.history), 1)
        self.assertIn("redirect=1", bridge.calls[1]["headers"]["Cookie"])

    def test_redirect_post_302_becomes_get(self):
        bridge = Bridge([{"status_code": 302, "header_pairs": [["Location", "/done"]]}, {}])
        self.send(bridge, "POST", data={"a": 1})
        self.assertEqual(bridge.calls[1]["method"], "GET")
        self.assertEqual(bridge.calls[1]["body"], b"")

    def test_redirect_post_307_keeps_form(self):
        bridge = Bridge([{"status_code": 307, "header_pairs": [["Location", "/done"]]}, {}])
        self.send(bridge, "POST", data={"a": 1})
        self.assertEqual(bridge.calls[1]["method"], "POST")
        self.assertEqual(bridge.calls[1]["body"], b"a=1")

    def test_redirect_disabled(self):
        bridge = Bridge([{"status_code": 302, "header_pairs": [["Location", "/done"]]}])
        response = self.send(bridge, allow_redirects=False)
        self.assertEqual(response.status_code, 302)
        self.assertEqual(len(bridge.calls), 1)

    def test_redirect_limit(self):
        self.session.max_redirects = 1
        bridge = Bridge([{"status_code": 302, "header_pairs": [["Location", "/1"]]},
                         {"status_code": 302, "header_pairs": [["Location", "/2"]]}])
        with self.assertRaises(requests.TooManyRedirects):
            self.send(bridge)

    def test_no_session_adapter_mutation(self):
        adapters = dict(self.session.adapters)
        self.send(Bridge())
        self.assertEqual(dict(self.session.adapters), adapters)

    def test_install_and_native_success_or_fallback(self):
        native = transport._NATIVE_REQUEST
        bridge = Bridge()
        good = requests.Response()
        good.status_code = 200
        good._content = b"native"
        def dispatch(session, method, url, **kwargs):
            if isinstance(session.adapters.get("https://"), transport.JavaHttpAdapter):
                return native(session, method, url, **kwargs)
            if url.endswith("native"):
                return good
            raise requests.exceptions.SSLError("test TLS failure")
        with patch.object(transport, "_PythonHttp", bridge), patch.object(transport, "_NATIVE_REQUEST", dispatch):
            transport.install_fallback()
            installed = requests.sessions.Session.request
            transport.install_fallback()
            self.assertIs(requests.sessions.Session.request, installed)
            self.assertIs(self.session.get("https://example.test/native"), good)
            self.assertEqual(self.session.get("https://example.test/fallback").text, "ok")
            self.assertEqual(len(bridge.calls), 1)


class DownloadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sys.modules.setdefault("crypto_protocol_dh", types.ModuleType("crypto_protocol_dh"))
        spec = importlib.util.spec_from_file_location("tvbox_app_download_test", PYTHON / "app.py")
        cls.app = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.app)

    def response(self, body=b"class Spider: pass", status=200, headers=None):
        response = requests.Response()
        response.status_code = status
        response._content = body
        response._content_consumed = True
        response.headers.update(headers or {})
        return response

    def test_success_and_invalid_preserve_old_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "spider.py"
            target.write_bytes(b"old=1")
            for response in (self.response(status=404), self.response(body=b"<html>error</html>"),
                             self.response(body=b""), self.response(status=304)):
                with patch.object(self.app, "redirectResponse", return_value=response):
                    with self.assertRaises(RuntimeError):
                        self.app.downloadFile(str(target), "https://example.test/spider.py")
                self.assertEqual(target.read_bytes(), b"old=1")
                self.assertFalse(Path(str(target) + ".tmp").exists())
            with patch.object(self.app, "redirectResponse", return_value=self.response()):
                self.app.downloadFile(str(target), "https://example.test/spider.py")
            self.assertEqual(target.read_bytes(), b"class Spider: pass")

    def test_relative_redirect(self):
        with patch.object(self.app.requests, "get", side_effect=[self.response(status=302, headers={"Location": "../p.py"}), self.response()]) as get:
            self.app.redirectResponse("https://example.test/scripts/start.py")
            self.assertEqual(get.call_args_list[1].args[0], "https://example.test/p.py")

    def test_redirect_cycle(self):
        with patch.object(self.app.requests, "get", side_effect=lambda *a, **k: self.response(status=302, headers={"Location": "/same.py"})):
            with self.assertRaises(requests.TooManyRedirects):
                self.app.redirectResponse("https://example.test/same.py")


if __name__ == "__main__":
    unittest.main(verbosity=2)
