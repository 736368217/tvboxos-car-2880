"""
download_real_assets.py
Searches and downloads real, high-resolution photographic posters and video stills
for sample movies and television series to replace all placeholder elements.
"""

import os
import re
import urllib.request
import urllib.parse
from PIL import Image

ASSET_DIR = "car/evidence/redesign/assets"
os.makedirs(ASSET_DIR, exist_ok=True)

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'image/avif,image/webp,image/apng,image/*,*/*;q=0.8'
}

TARGETS = [
    ("qingyunian_poster", "庆余年第二季 竖版 海报 高清"),
    ("qingyunian_still", "庆余年第二季 范闲 剧照 高清 4K"),
    ("fanxiang_poster", "繁花 王家卫 电视剧 竖版 海报"),
    ("fanxiang_still", "繁花 宝总 李李 黄河路 剧照"),
    ("wandering_earth2_poster", "流浪地球2 竖版 电影 海报"),
    ("wandering_earth2_still", "流浪地球2 太空电梯 剧照 4K"),
    ("santi_poster", "三体 电视剧 竖版 海报"),
    ("zhouchu_poster", "周处除三害 电影 竖版 海报"),
    ("kuangbiao_poster", "狂飙 张译 张颂文 竖版 海报"),
    ("dune2_poster", "沙丘2 电影 竖版 海报"),
    ("dune2_still", "沙丘2 保罗 沙漠 剧照 4K"),
    ("cctv1_still", "CCTV1 新闻联播 演播室 画面 高清"),
]

def search_bing_img(query):
    url = 'https://www.bing.com/images/search?q=' + urllib.parse.quote(query) + '&first=1'
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            m = re.findall(r'murl&quot;:&quot;(https?://[^&]+?)&quot;', html)
            return m
    except Exception as e:
        print(f"Error searching {query}: {e}")
        return []

def download_best_image(urls, out_path, min_size=(400, 400)):
    for u in urls[:10]:
        try:
            req = urllib.request.Request(u, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = resp.read()
                if len(data) < 25000:
                    continue
                temp_path = out_path + ".tmp"
                with open(temp_path, 'wb') as f:
                    f.write(data)
                with Image.open(temp_path) as im:
                    w, h = im.size
                    if w >= min_size[0] or h >= min_size[1]:
                        im.convert('RGB').save(out_path, 'JPEG', quality=90)
                        print(f"[OK] {os.path.basename(out_path)}: {w}x{h}, {len(data)/1024:.1f} KB from {u[:50]}...")
                        if os.path.exists(temp_path):
                            os.remove(temp_path)
                        return True
                if os.path.exists(temp_path):
                    os.remove(temp_path)
        except Exception:
            continue
    return False

def main():
    print("Starting automated download of real photographic movie posters & stills...")
    for filename, query in TARGETS:
        out_path = os.path.join(ASSET_DIR, f"{filename}.jpg")
        if os.path.exists(out_path) and os.path.getsize(out_path) > 30000:
            print(f"[Exists] {filename}.jpg")
            continue
        print(f"Fetching {filename} with query '{query}'...")
        urls = search_bing_img(query)
        if not download_best_image(urls, out_path):
            print(f"[Warn] Failed to download {filename}, will keep fallback")

if __name__ == "__main__":
    main()
