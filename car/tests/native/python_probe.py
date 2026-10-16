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
executor = ThreadPoolExecutor(max_workers=1)
try:
    config_url = "https://clun.top/box.json"
    with requests.get(config_url, timeout=(5, 12)) as response:
        response.raise_for_status()
        config = response.json()
    config_sites = config.get("sites", [])
    RESULT["subscription"] = {"url": config_url, "sites": len(config_sites),
        "python_sites": sum(1 for site in config_sites if ".py" in site.get("api", ""))}
    by_key = {site.get("key"): site for site in config_sites}
    for key in SITE_KEYS:
        site = by_key.get(key)
        if not site:
            RESULT["sites"].append({"key": key, "stage": "config", "error": "missing"})
            continue
        observation = {"key": key, "phases": []}
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
        home = phase("home", lambda: json.loads(str(spider.homeContent(True))))
        if not isinstance(home, dict):
            continue
        classes = home.get("class") or []
        observation["categories"] = len(classes)
        observation["home_items"] = len(home.get("list") or [])
        if classes:
            tid = str(classes[0].get("type_id", ""))
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
        save()
except Exception as error:
    RESULT["probe_error"] = type(error).__name__
finally:
    executor.shutdown(wait=False)
    RESULT["finished"] = time.time()
    save()
