"""Offline tests of the shipped requests wrapper; no network or extra Python install."""
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
try:
    import requests
except ImportError:
    sys.path.insert(0, str(ROOT / 'pyramid/build/pip/python/release/common'))
    import requests
spec = importlib.util.spec_from_file_location('tvbox_http_under_test', ROOT / 'pyramid/src/python/tvbox_http.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
RAW = 'https://raw.githubusercontent.com/o/r/main/config.json'
MIRRORS = ['https://v4.gh-proxy.org/', 'https://gh-proxy.com/', 'https://gh-proxy.org/']
class Bridge:
    disabled = set()
    reports = []
    @classmethod
    def githubCandidates(cls, method, url, headers):
        h = json.loads(headers)
        if (urlsplit(url).hostname != 'raw.githubusercontent.com' or
                any(k.lower() in ('authorization','cookie') for k in h) or 'token=' in url):
            return json.dumps([url])
        return json.dumps([p + url.split('://')[1] for p in MIRRORS if p not in cls.disabled] + [url])
    @classmethod
    def githubResult(cls, origin, candidate, status, retired):
        cls.reports.append((origin,candidate,status,retired))
        if retired or status == 0:
            cls.disabled.add(next((p for p in MIRRORS if candidate.startswith(p)), ''))
    @staticmethod
    def githubRedirectAllowed(candidate, target):
        return urlsplit(target).hostname in ('raw.githubusercontent.com','v4.gh-proxy.org','gh-proxy.com','gh-proxy.org')

def response(url, body=b'{"sites":[]}', status=200, content_type='text/plain', stream=False):
    out = requests.Response()
    out.status_code = status
    out.url = url
    out.headers['Content-Type'] = content_type
    out.headers['Set-Cookie'] = 'proxy=untrusted'
    if stream:
        out.raw = BytesIO(body)
        out.raw.stream = lambda amount, decode_content=True: iter([body[:3],body[3:]])
    else:
        out._content = body
        out._content_consumed = True
    return out

class TestGitHub(unittest.TestCase):
    def setUp(self):
        Bridge.disabled = set()
        Bridge.reports = []
        module._PythonHttp = Bridge
        self.session = requests.Session()
    def tearDown(self): self.session.close()
    def test_normal_preserves_query_headers_and_origin(self):
        self.session.params = {'ref':'main'}
        dispatched = []
        def send(s, method, url, **kwargs):
            dispatched.append((s,method,url,kwargs))
            return response(url)
        with patch.object(module,'_NATIVE_REQUEST',side_effect=send):
            got = module.github_request(self.session,'GET',RAW,{'headers':{'Range':'bytes=0-9'},'timeout':2})
        self.assertEqual(got.content,b'{"sites":[]}')
        self.assertEqual(got.url,RAW+'?ref=main')
        self.assertEqual(got.request.headers['Range'],'bytes=0-9')
        self.assertEqual(dispatched[0][3]['timeout'],(2,2))
        self.assertTrue(dispatched[0][3]['verify'])
        self.assertIsNone(dispatched[0][3]['params'])
        self.assertNotIn('Set-Cookie',got.headers)
    def test_expired_and_parked_node_not_reused(self):
        calls = []
        def send(s, method, url, **kwargs):
            calls.append(url)
            if url.startswith(MIRRORS[0]): raise requests.exceptions.SSLError('expired')
            if url.startswith(MIRRORS[1]): return response(url,b'<html>parked',content_type='text/html')
            return response(url)
        with patch.object(module,'_NATIVE_REQUEST',side_effect=send):
            module.github_request(self.session,'GET',RAW,{})
            calls.clear()
            module.github_request(self.session,'GET',RAW,{})
        self.assertEqual(len(calls),1)
        self.assertTrue(calls[0].startswith(MIRRORS[2]))
        self.assertEqual(len(Bridge.disabled),2)
    def test_sensitive_write_other_origin_unchanged(self):
        for method,url,kwargs in [('POST',RAW,{}),('GET','https://dl.736.de5.net/p/app.apk',{}),
                                  ('GET',RAW,{'headers':{'Authorization':'private'}}),
                                  ('GET',RAW,{'headers':{'Cookie':'session=x'}}),
                                  ('GET',RAW+'?token=private',{}),('GET',RAW,{'data':'body'})]:
            with patch.object(module,'_NATIVE_REQUEST') as send:
                self.assertIsNone(module.github_request(self.session,method,url,kwargs))
                send.assert_not_called()
    def test_stream_keeps_native_bytes(self):
        data = b'PK\x03\x04\x00\x01\xffbinary'
        with patch.object(module,'_NATIVE_REQUEST',side_effect=lambda s,m,u,**kw: response(u,data,stream=True)):
            got = module.github_request(self.session,'GET',RAW,{'stream':True})
        self.assertEqual(b''.join(got.iter_content(3)),data)
        self.assertEqual(got.url,RAW)
        got.close()
    def test_fallback_keeps_java_ladder(self):
        """All public routes failing must not swallow the existing native → Java fallback."""
        java_response = response('https://raw.githubusercontent.com/o/r/main/config.json', b'from-java')
        def send(s, m, u, **kw):
            if 'gh-proxy' in u:
                raise requests.exceptions.ConnectionError('blocked')
            return response(u, b'forbidden', status=403)
        calls = []
        def fallback(session, method, url, kwargs, bridge=None):
            calls.append(url)
            return java_response
        original = requests.sessions.Session.request
        try:
            module.install_fallback()
            with patch.object(module,'_NATIVE_REQUEST',side_effect=send), patch.object(module,'java_request',side_effect=fallback):
                got = requests.sessions.Session.request(self.session,'GET',RAW)
        finally:
            requests.sessions.Session.request = original
        self.assertEqual(got.content,b'from-java')
        self.assertEqual(calls,[RAW])

    def test_stream_failure_cools_node(self):
        class Broken:
            def stream(self,*a,**kw): raise IOError('connection lost')
            def close(self): pass
        with patch.object(module,'_NATIVE_REQUEST',side_effect=lambda s,m,u,**kw: response(u,stream=True)):
            got = module.github_request(self.session,'GET',RAW,{'stream':True})
        got.raw.raw = Broken()
        with self.assertRaises(IOError): list(got.iter_content(2))
        self.assertIn(MIRRORS[0],Bridge.disabled)
    def test_direct_404_after_mirrors_is_preserved(self):
        with patch.object(module,'_NATIVE_REQUEST',side_effect=lambda s,m,u,**kw: response(u,b'missing',status=404)):
            got = module.github_request(self.session,'GET',RAW,{})
        self.assertEqual(got.status_code,404)
        self.assertEqual(got.content,b'missing')
        self.assertFalse(Bridge.disabled)
    def test_200_html_disguised_as_text_is_rejected(self):
        def send(s,m,u,**kw):
            return response(u,b'<!doctype html>parked') if u.startswith(MIRRORS[0]) else response(u)
        with patch.object(module,'_NATIVE_REQUEST',side_effect=send):
            got = module.github_request(self.session,'GET',RAW,{})
        self.assertEqual(got.content,b'{"sites":[]}')
        self.assertIn(MIRRORS[0],Bridge.disabled)

if __name__ == '__main__': unittest.main()
