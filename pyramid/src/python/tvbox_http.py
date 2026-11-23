"""Requests-compatible fallback to the Android HTTP client."""
import base64
from datetime import timedelta
from http.client import HTTPMessage
from io import BytesIO
import json
import time
from types import SimpleNamespace
from urllib.parse import urlsplit, urljoin

import requests
from requests.adapters import BaseAdapter
from requests.cookies import extract_cookies_to_jar
from requests.structures import CaseInsensitiveDict
from requests.utils import get_encoding_from_headers

try:
    from com.undcover.freedom.pyramid import PythonHttp as _PythonHttp
except ImportError:
    _PythonHttp = None

_NATIVE_REQUEST = requests.sessions.Session.request
_FALLBACK_STATUS = (403, 429, 495, 496, 497, 525, 526, 527)


def _timeout_ms(timeout):
    if isinstance(timeout, tuple):
        if len(timeout) != 2:
            raise ValueError("timeout must contain connect and read values")
        parts = timeout
    else:
        parts = (timeout, timeout)
    result = []
    for value in parts:
        if value is None:
            result.append(0)
        elif float(value) <= 0:
            raise ValueError("timeout must be positive")
        else:
            result.append(min(2147483647, max(1, int(float(value) * 1000))))
    return tuple(result)


class _ResponseBody(BytesIO):
    def __init__(self, content, message):
        super().__init__(content)
        self._original_response = SimpleNamespace(msg=message)


class JavaHttpAdapter(BaseAdapter):
    def __init__(self, bridge=None):
        self.bridge = bridge if bridge is not None else _PythonHttp

    def send(self, request, stream=False, timeout=None, verify=True, cert=None, proxies=None):
        if stream:
            raise requests.RequestException("Java fallback does not support streaming")
        body = request.body
        if body is None:
            body = b""
        elif isinstance(body, str):
            body = body.encode("utf-8")
        elif isinstance(body, bytearray):
            body = bytes(body)
        elif not isinstance(body, bytes):
            raise requests.RequestException("Java fallback requires a replayable body")
        headers = {key: value for key, value in request.headers.items()
                   if key.lower() not in ("accept-encoding", "content-length")}
        connect_ms, read_ms = _timeout_ms(timeout)
        started = time.monotonic()
        payload = json.loads(str(self.bridge.request(
            request.method, request.url, json.dumps(headers, ensure_ascii=False),
            base64.b64encode(body).decode("ascii"), False, connect_ms, read_ms)))
        error = payload.get("error")
        if error:
            exception = requests.Timeout if "Timeout" in str(error) else requests.ConnectionError
            raise exception("Java HTTP transport failed", request=request)
        response = requests.Response()
        response.status_code = int(payload["status_code"])
        response.url = payload.get("url") or request.url
        response.reason = payload.get("reason", "")
        pairs = payload.get("header_pairs")
        if pairs is None:
            pairs = list((payload.get("headers") or {}).items())
        message = HTTPMessage()
        response.headers = CaseInsensitiveDict()
        for key, value in pairs:
            message.add_header(str(key), str(value))
            if key in response.headers:
                response.headers[key] += ", " + str(value)
            else:
                response.headers[key] = str(value)
        content = base64.b64decode(payload.get("body_base64", ""))
        response._content = content
        response._content_consumed = True
        response.raw = _ResponseBody(content, message)
        response.encoding = get_encoding_from_headers(response.headers)
        response.request = request
        response.connection = self
        response.elapsed = timedelta(seconds=time.monotonic() - started)
        extract_cookies_to_jar(response.cookies, request, response.raw)
        print("[tvbox-http] backend=java method=%s host=%s status=%d elapsed_ms=%d" % (
            request.method, urlsplit(request.url).hostname, response.status_code,
            int(response.elapsed.total_seconds() * 1000)))
        return response

    def close(self):
        pass


def java_request(session, method, url, kwargs, bridge=None):
    # A separate session avoids mutating an active spider's adapter table. Requests
    # still owns preparation, redirects, cookie policy and response hooks.
    with requests.Session() as fallback:
        for attribute in ("headers", "params", "proxies", "hooks"):
            setattr(fallback, attribute, getattr(session, attribute).copy())
        for attribute in ("auth", "verify", "cert", "trust_env", "max_redirects"):
            setattr(fallback, attribute, getattr(session, attribute))
        fallback.cookies.update(session.cookies)
        adapter = JavaHttpAdapter(bridge)
        fallback.mount("https://", adapter)
        fallback.mount("http://", adapter)
        response = _NATIVE_REQUEST(fallback, method, url, **kwargs)
        session.cookies.update(fallback.cookies)
        return response


class _GitHubStream:
    """Forward native streaming unchanged, marking a node only if transport reading fails."""
    def __init__(self, raw, origin, candidate):
        self.raw, self.origin, self.candidate = raw, origin, candidate

    def __getattr__(self, name):
        return getattr(self.raw, name)

    def read(self, *args, **kwargs):
        try:
            return self.raw.read(*args, **kwargs)
        except Exception:
            _PythonHttp.githubResult(self.origin, self.candidate, 0, False)
            raise

    def read1(self, *args, **kwargs):
        try:
            return self.raw.read1(*args, **kwargs)
        except Exception:
            _PythonHttp.githubResult(self.origin, self.candidate, 0, False)
            raise

    def stream(self, *args, **kwargs):
        try:
            yield from self.raw.stream(*args, **kwargs)
        except Exception:
            _PythonHttp.githubResult(self.origin, self.candidate, 0, False)
            raise


def github_request(session, method, url, kwargs):
    """Public GitHub reads use the app's bounded, health-aware catalogue, without new UI."""
    if (_PythonHttp is None or not hasattr(_PythonHttp, "githubCandidates")
            or str(method).upper() not in ("GET", "HEAD") or kwargs.get("data") is not None
            or kwargs.get("files") or kwargs.get("proxies") or kwargs.get("cert") or session.cert):
        return None
    prepared = session.prepare_request(requests.Request(
        method, url, headers=kwargs.get("headers"), params=kwargs.get("params"),
        auth=kwargs.get("auth"), cookies=kwargs.get("cookies")))
    urls = json.loads(str(_PythonHttp.githubCandidates(
        prepared.method, prepared.url, json.dumps(dict(prepared.headers), ensure_ascii=False))))
    if urls == [prepared.url]:
        return None
    origin = urls[-1]
    original_headers = {k: v for k, v in prepared.headers.items() if k.lower() != "host"}
    failure = None
    for candidate in urls:
        mirror = candidate != origin
        options = dict(kwargs, headers=original_headers, params=None, cookies=None, auth=None)
        if mirror:
            options["verify"] = True  # A stale/expired CDN certificate is never trusted.
            timeout = kwargs.get("timeout")
            connect, read = timeout if isinstance(timeout, tuple) else (timeout, timeout)
            options["timeout"] = (min(4, connect) if connect is not None else 4,
                                  min(8, read) if read is not None else 8)
        # A separate session prevents proxy cookies/params from leaking to the logical origin.
        with requests.Session() as transport:
            transport.trust_env = session.trust_env
            transport.proxies = session.proxies.copy()
            transport.verify = session.verify
            transport.max_redirects = session.max_redirects
            transport.hooks = session.hooks.copy()
            response = None
            try:
                response = _NATIVE_REQUEST(transport, method, candidate, **options)
                if mirror:
                    redirected = not _PythonHttp.githubRedirectAllowed(candidate, response.url)
                    path = urlsplit(origin).path.lower()
                    prefix = "" if kwargs.get("stream") else response.content[:512].decode("utf-8", "ignore").lstrip("\ufeff \r\n\t").lower()
                    html = (not path.endswith((".html", ".htm", ".svg")) and
                            ("text/html" in response.headers.get("Content-Type", "").lower()
                             or prefix.startswith(("<html", "<!doctype html"))))
                    if redirected or html:
                        _PythonHttp.githubResult(origin, candidate, response.status_code, True)
                        response.close()
                        continue
                    if response.status_code >= 400:
                        _PythonHttp.githubResult(origin, candidate, response.status_code, False)
                        response.close()
                        continue
                    _PythonHttp.githubResult(origin, candidate, response.status_code, False)
                    response.headers.pop("Set-Cookie", None)
                    response.cookies.clear()
                    location = response.headers.get("Location")
                    if location:
                        target = urljoin(response.url, location)
                        if not _PythonHttp.githubRedirectAllowed(candidate, target):
                            _PythonHttp.githubResult(origin, candidate, response.status_code, True)
                            response.close()
                            continue
                        next_urls = json.loads(str(_PythonHttp.githubCandidates("GET", target, "{}")))
                        response.headers["Location"] = next_urls[-1]
                    if kwargs.get("stream"):
                        response.raw = _GitHubStream(response.raw, origin, candidate)
                response.url = origin
                response.request = prepared
                return response
            except requests.RequestException as error:
                if response is not None:
                    response.close()
                if not mirror:
                    raise
                _PythonHttp.githubResult(origin, candidate, 0, isinstance(error, requests.exceptions.SSLError))
                failure = error
    if failure:
        raise failure
    return None


def install_fallback():
    current = requests.sessions.Session.request
    if _PythonHttp is None or getattr(current, "_tvbox_java_http_fallback", False):
        return

    def request(session, method, url, **kwargs):
        try:
            accelerated = github_request(session, method, url, kwargs)
        except requests.RequestException:
            # Every public mirror failed: keep the existing native → Java transport ladder.
            accelerated = None
        if accelerated is not None:
            if accelerated.status_code in _FALLBACK_STATUS:
                accelerated.close()
            else:
                return accelerated
        data = kwargs.get("data")
        replayable = data is None or isinstance(data, (str, bytes, bytearray, dict, list, tuple))
        if (not str(url).lower().startswith("https://") or kwargs.get("stream")
                or kwargs.get("files") or kwargs.get("proxies") or not replayable):
            return _NATIVE_REQUEST(session, method, url, **kwargs)
        response = None
        try:
            response = _NATIVE_REQUEST(session, method, url, **kwargs)
            if response.status_code not in _FALLBACK_STATUS:
                return response
        except requests.RequestException:
            pass
        try:
            fallback = java_request(session, method, url, kwargs)
        except requests.RequestException:
            if response is not None:
                return response
            raise
        if response is None or 200 <= fallback.status_code < 400:
            if response is not None:
                response.close()
            return fallback
        fallback.close()
        return response

    request._tvbox_java_http_fallback = True
    requests.sessions.Session.request = request
