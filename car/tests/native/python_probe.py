"""Executed only by Android instrumentation in the target app process."""
import base64
from concurrent.futures import ThreadPoolExecutor, TimeoutError
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import threading
import time
from urllib.parse import urljoin

import app
import requests
from tvbox_http import java_request
from com.undcover.freedom.pyramid import PythonLoader
from java.util import HashMap

RESULT = {"transport": {}, "sites": [], "started": time.time()}
PLAYBACK_TARGET = None

def save():
    with open(OUT + "/python-results.json.tmp", "w", encoding="utf-8") as stream:
        json.dump(RESULT, stream, ensure_ascii=True, indent=2)
    os.replace(OUT + "/python-results.json.tmp", OUT + "/python-results.json")


class Echo(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path == "/redirect":
            self.send_response(302)
            self.send_header("Location", "/cookies")
            self.send_header("Set-Cookie", "a=1; Path=/")
            self.send_header("Set-Cookie", "b=2; Path=/")
            self.end_headers()
        elif self.path == "/bytes":
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.end_headers()
            self.wfile.write(bytes(range(256)))
        else:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"cookie": self.headers.get("Cookie", "")}).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"body": base64.b64encode(body).decode(),
                                    "content_type": self.headers.get("Content-Type", "")}).encode())


server = ThreadingHTTPServer(("127.0.0.1", 0), Echo)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    origin = "http://127.0.0.1:%d" % server.server_port
    with requests.Session() as session:
        response = java_request(session, "POST", origin + "/echo", {"data": {"q": "a b"}, "timeout": (2, 2)})
        echo = response.json()
        RESULT["transport"]["form"] = base64.b64decode(echo["body"]) == b"q=a+b" and echo["content_type"].startswith("application/x-www-form-urlencoded")
        response = java_request(session, "POST", origin + "/echo", {"data": bytes(range(256)), "timeout": 2})
        RESULT["transport"]["binary_post"] = base64.b64decode(response.json()["body"]) == bytes(range(256))
        response = java_request(session, "GET", origin + "/bytes", {"timeout": 2})
        RESULT["transport"]["binary_response"] = response.content == bytes(range(256))
        response = java_request(session, "GET", origin + "/redirect", {"timeout": 2})
        cookie = response.json()["cookie"]
        RESULT["transport"]["redirect_cookies"] = len(response.history) == 1 and "a=1" in cookie and "b=2" in cookie
        RESULT["transport"]["response_contract"] = hasattr(response, "apparent_encoding") and response.status_code == 200
        response.raise_for_status()
        response = java_request(session, "GET", "https://clun.top/py/douban.py", {"timeout": (5, 10)})
        RESULT["transport"]["real_https_status"] = response.status_code
        RESULT["transport"]["real_https_bytes"] = len(response.content)
        RESULT["transport"]["real_https_sha256"] = hashlib.sha256(response.content).hexdigest()
finally:
    server.shutdown()
    server.server_close()
    thread.join(2)
    save()

loader = PythonLoader.getInstance()
original_request = requests.sessions.Session.request
observation = None

def diagnostic_request(session, method, url, *args, **kwargs):
    started = time.monotonic()
    info = {"method": str(method).upper()}
    try:
        response = original_request(session, method, url, *args, **kwargs)
        # No headers, cookies, credentials, full URLs or remote bodies enter evidence.
        payload = response.content
        text = response.text
        info.update(status=response.status_code, bytes=len(payload),
                    sha256=hashlib.sha256(payload).hexdigest(),
                    security_verify="系统安全验证" in text or "验证后继续" in text,
                    module_items=text.count('module-item'), public_list_items=text.count('public-list-exp'),
                    detail_links=text.count('/detail/'))
        info["phase"] = observation["phases"][-1]["phase"] if observation and observation.get("phases") else "config"
        info["mime"] = response.headers.get("Content-Type", "").split(";", 1)[0]
        info["content_encoding"] = response.headers.get("Content-Encoding", "")
        info["gzip_magic"] = payload.startswith(b'\x1f\x8b')
        info["looks_html"] = "<html" in text.lower() or "<!doctype" in text.lower()
        info["access_denied"] = any(s in text.lower() for s in ("forbidden", "access denied", "captcha", "restricted"))
        info["server_error_page"] = any(s in text.lower() for s in ("404 not found", "403 forbidden", "502 bad gateway", "503 service unavailable"))
        info["error_404"] = "404 not found" in text.lower()
        info["error_403"] = "403 forbidden" in text.lower()
        info["nginx_marker"] = "nginx" in text.lower()
        if observation and observation.get("key") == "py_maple" and 0 < len(payload) < 512:
            with requests.Session() as alternate_session:
                alternate = java_request(alternate_session, method, url, kwargs)
                alt_text = alternate.text
                info["java_comparison"] = {"status": alternate.status_code, "bytes": len(alternate.content),
                    "sha256": hashlib.sha256(alternate.content).hexdigest(),
                    "module_items": alt_text.count("module-item"), "public_list_items": alt_text.count("public-list-exp"),
                    "detail_links": alt_text.count("/detail/")}
                alternate.close()
        return response
    except Exception as error:
        info["error_class"] = type(error).__name__
        raise
    finally:
        info["elapsed_ms"] = int((time.monotonic() - started) * 1000)
        if observation is not None:
            records = observation.setdefault("http", [])
            if len(records) < 100:
                records.append(info)

requests.sessions.Session.request = diagnostic_request
executor = ThreadPoolExecutor(max_workers=1)
try:
    config_url = "https://clun.top/box.json"
    with requests.get(config_url, timeout=(5, 12)) as response:
        response.raise_for_status()
        config = response.json()
    config_sites = config.get("sites", [])
    RESULT["subscription"] = {"url": config_url, "sites": len(config_sites),
        "python_sites": sum(1 for site in config_sites if ".py" in site.get("api", "")),
        "config_sha256": hashlib.sha256(json.dumps(config, sort_keys=True, ensure_ascii=False).encode()).hexdigest()}
    by_key = {site.get("key"): site for site in config_sites}
    for key in SITE_KEYS:
        site = by_key.get(key)
        if not site:
            RESULT["sites"].append({"key": key, "stage": "config", "error": "missing"})
            continue
        observation = {"key": key, "phases": []}
        videos = []
        RESULT["sites"].append(observation)
        api = urljoin(config_url, site["api"])
        ext = site.get("ext", "")
        if not isinstance(ext, str):
            ext = json.dumps(ext, ensure_ascii=False)

        def phase(name, action, seconds=25):
            started = time.monotonic()
            item = {"phase": name}
            observation["phases"].append(item)
            save()
            try:
                value = executor.submit(action).result(seconds)
                item["ok"] = True
                return value
            except TimeoutError:
                item["ok"] = False
                item["error"] = "Timeout"
                RESULT["aborted_outstanding_call"] = key + ":" + name
                raise
            except Exception as error:
                item["ok"] = False
                item["error"] = type(error).__name__
                return None
            finally:
                item["elapsed_ms"] = int((time.monotonic() - started) * 1000)
                save()

        spider = phase("load_import_init", lambda: loader.getSpider(key, api, ext), 35)
        if spider is None or not hasattr(spider, "isLoadSuccess") or not spider.isLoadSuccess():
            observation["load_success"] = False
            save()
            continue
        observation["load_success"] = True
        for alias, script_path in app.gParam.get("SpiderPath", {}).items():
            if alias == key or alias == api.rsplit("/", 1)[-1].split(".")[0]:
                try:
                    with open(script_path, "rb") as script:
                        payload = script.read()
                    observation["script_sha256"] = hashlib.sha256(payload).hexdigest()
                    observation["script_bytes"] = len(payload)
                except OSError:
                    observation["script_snapshot_unavailable"] = True
        home = phase("home", lambda: json.loads(str(spider.homeContent(True))))
        if not isinstance(home, dict):
            continue
        classes = home.get("class") or []
        observation["categories"] = len(classes)
        observation["home_items"] = len(home.get("list") or [])
        if classes:
            tid = str(classes[0].get("type_id", ""))
            observation["category_tid"] = tid
            category = phase("category", lambda: json.loads(str(spider.categoryContent(tid, "1", True, HashMap()))))
            if isinstance(category, dict):
                videos = category.get("list") or []
                observation["category_items"] = len(videos)
                if videos and key != "py_douban":
                    ids = __import__("java.util", fromlist=["ArrayList"]).ArrayList()
                    ids.add(str(videos[0].get("vod_id", "")))
                    detail = phase("detail", lambda: json.loads(str(spider.detailContent(ids))))
                    if isinstance(detail, dict):
                        observation["detail_items"] = len(detail.get("list") or [])
                        details = detail.get("list") or []
                        if details:
                            first_detail = details[0]
                            flags = str(first_detail.get("vod_play_from", "")).split("$$$")
                            groups = str(first_detail.get("vod_play_url", "")).split("$$$")
                            if groups and "$" in groups[0]:
                                play_id = groups[0].split("#", 1)[0].split("$", 1)[1]
                                vip = __import__("java.util", fromlist=["ArrayList"]).ArrayList()
                                player = phase("player", lambda: json.loads(str(spider.playerContent(flags[0], play_id, vip))), 30)
                                if isinstance(player, dict):
                                    resolved = player.get("url") or ""
                                    observation["player_url_present"] = bool(resolved)
                                    observation["player_parse"] = player.get("parse", 0)
                                    observation["player_scheme"] = resolved.split(":", 1)[0] if ":" in resolved else "opaque"
                                    # Keep the resolved URL only in target-private memory for a bounded EXO probe.
                                    if resolved and PLAYBACK_TARGET is None and str(player.get("parse", 0)) == "0" and resolved.startswith(("http://", "https://")):
                                        headers = player.get("header") or {}
                                        if isinstance(headers, str):
                                            try:
                                                headers = json.loads(headers)
                                            except (TypeError, ValueError):
                                                headers = {}
                                        PLAYBACK_TARGET = {"key": key, "url": resolved, "headers": headers}
        if site.get("searchable", 1) and key != "py_douban":
            search = phase("search", lambda: json.loads(str(spider.searchContent("\u6f5c\u4f0f", False))))
            if isinstance(search, dict):
                observation["search_items"] = len(search.get("list") or [])
        if key == "py_maple":
            observation["alternate_categories"] = []
            for candidate in classes[3:5]:
                alt_tid = str(candidate.get("type_id", ""))
                alternate = phase("category_alternate", lambda: json.loads(str(spider.categoryContent(alt_tid, "1", True, HashMap()))))
                observation["alternate_categories"].append({"tid": alt_tid, "items": len((alternate or {}).get("list") or [])})
        if videos and key != "py_douban":
            positive_title = str(videos[0].get("vod_name", ""))
            if positive_title:
                positive = phase("search_positive", lambda: json.loads(str(spider.searchContent(positive_title, False))))
                matches = (positive or {}).get("list") or []
                observation["positive_search_items"] = len(matches)
                observation["positive_search_matches_known_id"] = any(str(v.get("vod_id")) == str(videos[0].get("vod_id")) for v in matches)
                observation["positive_search_matches_known_title"] = any(str(v.get("vod_name", "")).strip() == positive_title.strip() for v in matches)
        save()
except Exception as error:
    RESULT["probe_error"] = type(error).__name__
finally:
    requests.sessions.Session.request = original_request
    executor.shutdown(wait=False)
    RESULT["finished"] = time.time()
    save()
