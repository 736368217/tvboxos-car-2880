"""
generate_ux_redesign_mockups.py
Full photographic rendering of 3 complete in-car UI design schemes referencing
dpearson2699/swift-ios-skills (SwiftUI Liquid Glass, CarPlay ergonomics, AVKit transport controls,
Apple SF Symbols vector iconography, and mature multi-layer translucent styling).
"""

import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = "car/evidence/redesign"
COMPARE_DIR = "car/reports/ux_redesign"
ASSET_DIR = "car/evidence/redesign/assets"
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(COMPARE_DIR, exist_ok=True)

# Fonts
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"

def get_font(bold=False, size=32):
    f_path = FONT_BOLD if bold else FONT_REG
    try:
        return ImageFont.truetype(f_path, size)
    except Exception:
        return ImageFont.load_default()

# ----------------------------------------------------------------------
# COLOR TOKENS (Apple Dark Glassmorphism + Dynamic Luminous Accents)
# ----------------------------------------------------------------------
BG_OBSIDIAN = (11, 15, 22)             # Deep obsidian base
BG_CARD_GLASS = (20, 27, 40, 225)       # Liquid Glass material
BORDER_GLASS = (55, 75, 105, 200)       # Glass dual border
SPECULAR_TOP = (255, 255, 255, 90)     # Apple Liquid Glass top specular reflection

ACCENT_CYAN = (0, 210, 255)            # Electric Cyan (Active / SGSR1 / Highlights)
ACCENT_BLUE = (37, 99, 235)            # System Primary Blue (Actions / Selected)
ACCENT_AMBER = (255, 179, 71)          # Warm Amber Gold (Ratings / Audio / Highlights)
ACCENT_RED = (239, 68, 68)             # Live Badge / Hot Top 1
SUCCESS_GREEN = (16, 185, 129)         # Green (Connected / Low Latency)

TEXT_PRIMARY = (255, 255, 255)         # 100% Contrast White
TEXT_SECONDARY = (165, 178, 196)       # Secondary Subtext
TEXT_MUTED = (110, 122, 138)           # Tertiary Meta Text

# ----------------------------------------------------------------------
# LIQUID GLASS & ASSET RENDERING HELPERS
# ----------------------------------------------------------------------

def draw_glass_panel(draw, box, radius, fill=BG_CARD_GLASS, border=BORDER_GLASS, width=2, specular=True):
    """Renders an Apple Liquid Glass style panel with dual-layer border and top specular reflection."""
    x0, y0, x1, y1 = box
    # 1. Main glass container fill and border
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=border, width=width)
    # 2. Specular top rim highlight (physical light reflection on glass edge)
    if specular and (x1 - x0) > radius * 2:
        draw.line([x0 + radius, y0 + 1, x1 - radius, y0 + 1], fill=SPECULAR_TOP, width=2)

def draw_glass_pill(draw, box, fill=None, border=None, width=1, specular=True):
    x0, y0, x1, y1 = box
    radius = (y1 - y0) // 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=border, width=width)
    if specular and (x1 - x0) > radius * 2:
        draw.line([x0 + radius, y0 + 1, x1 - radius, y0 + 1], fill=SPECULAR_TOP, width=1)

def load_and_crop(filename, target_size, radius=0):
    """Loads a real asset image, centers-crops to aspect ratio, resizes with LANCZOS, and rounds corners."""
    p = os.path.join(ASSET_DIR, filename)
    tw, th = target_size
    if not os.path.exists(p):
        return Image.new("RGBA", (tw, th), (32, 42, 60))
    try:
        orig = Image.open(p).convert("RGBA")
        ow, oh = orig.size
        target_aspect = tw / th
        orig_aspect = ow / oh

        if orig_aspect > target_aspect:
            new_w = int(oh * target_aspect)
            x_offset = (ow - new_w) // 2
            cropped = orig.crop((x_offset, 0, x_offset + new_w, oh))
        else:
            new_h = int(ow / target_aspect)
            y_offset = (oh - new_h) // 2
            cropped = orig.crop((0, y_offset, ow, y_offset + new_h))

        resized = cropped.resize((tw, th), Image.Resampling.LANCZOS)

        if radius > 0:
            mask = Image.new("L", (tw, th), 0)
            mdraw = ImageDraw.Draw(mask)
            mdraw.rounded_rectangle([0, 0, tw, th], radius=radius, fill=255)
            resized.putalpha(mask)

        return resized
    except Exception as e:
        print(f"Error loading {filename}: {e}")
        return Image.new("RGBA", (tw, th), (32, 42, 60))

def paste_image(canvas, im, pos):
    canvas.paste(im, pos, im if im.mode == "RGBA" else None)

# ----------------------------------------------------------------------
# SF SYMBOLS INSPIRED PRECISION VECTOR ICON DRAWERS
# ----------------------------------------------------------------------

def draw_sf_star(draw, center, size, color=ACCENT_CYAN):
    cx, cy = center
    r = size // 2
    pts = []
    for i in range(10):
        angle = i * math.pi / 5 - math.pi / 2
        curr_r = r if i % 2 == 0 else r * 0.45
        pts.append((cx + curr_r * math.cos(angle), cy + curr_r * math.sin(angle)))
    draw.polygon(pts, fill=color)

def draw_sf_grid(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    hs = size // 2 - 2
    gap = 3
    # 4 rounded rectangles
    w = (hs * 2 - gap) // 2
    # top-left
    draw.rounded_rectangle([cx - hs, cy - hs, cx - hs + w, cy - hs + w], radius=3, fill=color)
    # top-right
    draw.rounded_rectangle([cx - hs + w + gap, cy - hs, cx + hs, cy - hs + w], radius=3, fill=color)
    # bottom-left
    draw.rounded_rectangle([cx - hs, cy - hs + w + gap, cx - hs + w, cy + hs], radius=3, fill=color)
    # bottom-right
    draw.rounded_rectangle([cx - hs + w + gap, cy - hs + w + gap, cx + hs, cy + hs], radius=3, fill=color)

def draw_sf_tv(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    w = size
    h = int(size * 0.7)
    draw.rounded_rectangle([cx - w//2, cy - h//2 + 3, cx + w//2, cy + h//2 + 3], radius=5, outline=color, width=3)
    # stand
    draw.line([cx - w//4, cy + h//2 + 6, cx + w//4, cy + h//2 + 6], fill=color, width=3)
    draw.line([cx, cy + h//2 + 3, cx, cy + h//2 + 6], fill=color, width=3)
    # antenna
    draw.line([cx, cy - h//2 + 3, cx - w//3, cy - h//2 - 6], fill=color, width=2)
    draw.line([cx, cy - h//2 + 3, cx + w//3, cy - h//2 - 6], fill=color, width=2)

def draw_sf_clock(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    r = size // 2
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=3)
    draw.line([cx, cy, cx, cy - int(r * 0.6)], fill=color, width=3)
    draw.line([cx, cy, cx + int(r * 0.5), cy], fill=color, width=3)

def draw_sf_heart(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    r = size // 2
    draw.ellipse([cx - r, cy - r + 3, cx, cy + 3], fill=color)
    draw.ellipse([cx, cy - r + 3, cx + r, cy + 3], fill=color)
    draw.polygon([(cx - r, cy - r//3 + 4), (cx + r, cy - r//3 + 4), (cx, cy + r)], fill=color)

def draw_sf_airplay(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    w = size
    h = int(size * 0.7)
    draw.rounded_rectangle([cx - w//2, cy - h//2 - 2, cx + w//2, cy + h//2 - 6], radius=4, outline=color, width=2)
    # triangle at bottom
    tw = size // 3
    draw.polygon([(cx - tw, cy + h//2 + 4), (cx + tw, cy + h//2 + 4), (cx, cy + 1)], fill=color)

def draw_sf_gear(draw, center, size, color=TEXT_SECONDARY):
    cx, cy = center
    r = size // 2
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    for i in range(6):
        a = i * math.pi / 3
        draw.line([cx - (r+4)*math.cos(a), cy - (r+4)*math.sin(a), cx + (r+4)*math.cos(a), cy + (r+4)*math.sin(a)], fill=color, width=6)
    draw.ellipse([cx - r*0.4, cy - r*0.4, cx + r*0.4, cy + r*0.4], fill=BG_OBSIDIAN)

def draw_sf_search(draw, center, size, color=TEXT_MUTED):
    cx, cy = center
    r = size // 3
    draw.ellipse([cx - r - 2, cy - r - 2, cx + r - 2, cy + r - 2], outline=color, width=3)
    draw.line([cx + int(r * 0.7) - 2, cy + int(r * 0.7) - 2, cx + r + 8, cy + r + 8], fill=color, width=4)

def draw_sf_mic(draw, center, size, color=TEXT_PRIMARY):
    cx, cy = center
    w = size // 3
    h = size // 2
    draw.rounded_rectangle([cx - w//2, cy - h, cx + w//2, cy], radius=5, fill=color)
    draw.arc([cx - w - 2, cy - h//2, cx + w + 2, cy + h//2], start=0, end=180, fill=color, width=2)
    draw.line([cx, cy + h//2, cx, cy + h//2 + 6], fill=color, width=2)
    draw.line([cx - 8, cy + h//2 + 6, cx + 8, cy + h//2 + 6], fill=color, width=2)

def draw_sf_play(draw, center, size, color=TEXT_PRIMARY):
    cx, cy = center
    r = size // 2
    points = [(cx - r//2 + 2, cy - r), (cx - r//2 + 2, cy + r), (cx + r, cy)]
    draw.polygon(points, fill=color)

def draw_sf_pause(draw, center, size, color=TEXT_PRIMARY):
    cx, cy = center
    w = size // 4
    h = size
    draw.rounded_rectangle([cx - w - 4, cy - h//2, cx - 4, cy + h//2], radius=3, fill=color)
    draw.rounded_rectangle([cx + 4, cy - h//2, cx + w + 4, cy + h//2], radius=3, fill=color)

def draw_sf_replay_15(draw, center, size, color=ACCENT_CYAN):
    cx, cy = center
    r = size // 2
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=60, end=330, fill=color, width=3)
    # arrow tip
    draw.polygon([(cx - r + 4, cy - 8), (cx - r - 6, cy - 8), (cx - r - 1, cy - 16)], fill=color)
    font_s = get_font(True, int(size * 0.38))
    draw.text((cx - 14, cy - 12), "15", fill=color, font=font_s)

def draw_sf_forward_15(draw, center, size, color=ACCENT_CYAN):
    cx, cy = center
    r = size // 2
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=210, end=120, fill=color, width=3)
    draw.polygon([(cx + r - 4, cy - 8), (cx + r + 6, cy - 8), (cx + r + 1, cy - 16)], fill=color)
    font_s = get_font(True, int(size * 0.38))
    draw.text((cx - 14, cy - 12), "15", fill=color, font=font_s)

def draw_sf_sparkles(draw, center, size, color=ACCENT_CYAN):
    cx, cy = center
    r = size // 2
    draw.line([cx, cy - r, cx, cy + r], fill=color, width=3)
    draw.line([cx - r, cy, cx + r, cy], fill=color, width=3)
    draw.line([cx - r//2, cy - r//2, cx + r//2, cy + r//2], fill=color, width=2)
    draw.line([cx - r//2, cy + r//2, cx + r//2, cy - r//2], fill=color, width=2)

def draw_sf_dolby(draw, center, size, color=ACCENT_AMBER):
    cx, cy = center
    r = size // 2
    # Two back-to-back D-shapes
    draw.rectangle([cx - r, cy - r + 4, cx - r + 5, cy + r - 4], fill=color)
    draw.arc([cx - r, cy - r + 4, cx - 2, cy + r - 4], start=270, end=90, fill=color, width=4)
    draw.rectangle([cx + r - 5, cy - r + 4, cx + r, cy + r - 4], fill=color)
    draw.arc([cx + 2, cy - r + 4, cx + r, cy + r - 4], start=90, end=270, fill=color, width=4)


# ==============================================================================
# 方案一：【深空黑曜微光版 (Obsidian Glass)】(真海报/真剧照/第一版完整还原)
# ==============================================================================

def create_scheme1_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")
    draw.ellipse([800, -200, 2200, 600], fill=(20, 35, 60, 120))

    # 1. Left Vertical Dock (x: 0 ~ 240 px)
    draw.rectangle([0, 0, 240, 1620], fill=(16, 22, 32, 245))
    draw.line([240, 0, 240, 1620], fill=(42, 54, 75), width=2)
    draw.text((45, 45), "TVBOX", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((48, 95), "CAR 3K", fill=TEXT_MUTED, font=get_font(True, 24))

    nav_items = [
        ("精选推荐", True, draw_sf_star),
        ("分类专题", False, draw_sf_grid),
        ("电视直播", False, draw_sf_tv),
        ("播放历史", False, draw_sf_clock),
        ("我的收藏", False, draw_sf_heart),
        ("多端推送", False, draw_sf_airplay),
        ("系统设置", False, draw_sf_gear),
    ]
    cur_y = 175
    for title, active, icon_func in nav_items:
        if active:
            draw_glass_panel(draw, [20, cur_y, 220, cur_y + 105], radius=18, fill=(30, 58, 100, 240), border=ACCENT_CYAN, width=3)
            draw.rectangle([20, cur_y + 26, 28, cur_y + 78], fill=ACCENT_CYAN)
            icon_func(draw, (55, cur_y + 52), 26, ACCENT_CYAN)
            draw.text((80, cur_y + 35), title, fill=TEXT_PRIMARY, font=get_font(True, 26))
        else:
            draw_glass_panel(draw, [20, cur_y, 220, cur_y + 105], radius=18, fill=(24, 30, 42, 160), border=(38, 48, 68), width=1)
            icon_func(draw, (55, cur_y + 52), 24, TEXT_MUTED)
            draw.text((80, cur_y + 36), title, fill=TEXT_SECONDARY, font=get_font(False, 25))
        cur_y += 125

    draw_glass_panel(draw, [20, 1470, 220, 1570], radius=18, fill=(24, 34, 50, 220), border=(45, 60, 85))
    draw.text((42, 1492), "4K 引擎就绪", fill=SUCCESS_GREEN, font=get_font(True, 24))
    draw.text((42, 1526), "IJK 硬解 · SGSR1", fill=TEXT_MUTED, font=get_font(False, 18))

    # 2. Top Header Bar
    draw_glass_pill(draw, [280, 40, 800, 130], fill=(26, 34, 48, 210), border=(50, 68, 92), width=2)
    draw_sf_sparkles(draw, (315, 85), 24, ACCENT_CYAN)
    draw.text((345, 62), "4K 臻彩超清已就绪 · 杜比全景声 7.1", fill=ACCENT_CYAN, font=get_font(False, 26))

    draw_glass_pill(draw, [840, 40, 2300, 130], fill=(26, 36, 52, 220), border=ACCENT_CYAN, width=2)
    draw_sf_search(draw, (880, 85), 26, TEXT_MUTED)
    draw.text((915, 60), "搜索片名 / 首字母简拼 (例如: QYN2 庆余年)", fill=TEXT_MUTED, font=get_font(False, 28))
    
    draw_glass_pill(draw, [2020, 50, 2280, 120], fill=ACCENT_BLUE, border=None)
    draw_sf_mic(draw, (2060, 85), 26, TEXT_PRIMARY)
    draw.text((2090, 62), "按住语音", fill=TEXT_PRIMARY, font=get_font(True, 26))

    draw_glass_pill(draw, [2340, 40, 2800, 130], fill=(26, 34, 48, 210), border=(50, 68, 92), width=2)
    draw.text((2400, 60), "15:42  5G 📶 100%", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # 3. Main Stage: Left Hero Resume Card with REAL Qingyunian Still!
    draw_glass_panel(draw, [280, 170, 1380, 680], radius=32, fill=(24, 32, 48, 240), border=(55, 75, 110), width=3)
    qyn_still = load_and_crop("qingyunian_still.jpg", (440, 470), radius=22)
    paste_image(img, qyn_still, (300, 190))

    draw_glass_pill(draw, [760, 200, 970, 255], fill=(37, 99, 235, 200), border=(96, 165, 250))
    draw.text((785, 212), "上次未看完", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((760, 275), "庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((760, 335), "第 18 集 · 4K 臻彩超清 (已播 72% / 42分15秒)", fill=ACCENT_CYAN, font=get_font(False, 26))
    
    draw.rounded_rectangle([760, 400, 1340, 416], radius=8, fill=(45, 55, 72))
    draw.rounded_rectangle([760, 400, 1180, 416], radius=8, fill=ACCENT_CYAN)

    draw_glass_pill(draw, [760, 470, 1070, 595], fill=ACCENT_BLUE, border=(96, 165, 250), width=2)
    draw_sf_play(draw, (810, 532), 26, TEXT_PRIMARY)
    draw.text((840, 508), "继续播放", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_glass_pill(draw, [1100, 470, 1340, 595], fill=(45, 55, 75), border=(75, 85, 105), width=2)
    draw_sf_grid(draw, (1145, 532), 24, TEXT_SECONDARY)
    draw.text((1175, 510), "选集列表", fill=TEXT_SECONDARY, font=get_font(True, 28))

    # 4. Right Stage: Today Hot Feature Card with REAL Fanxiang Poster!
    draw_glass_panel(draw, [1420, 170, 2800, 680], radius=32, fill=(28, 38, 56, 240), border=(60, 80, 115), width=2)
    fx_img = load_and_crop("fanxiang_poster.jpg", (420, 470), radius=22)
    paste_image(img, fx_img, (1440, 190))

    draw_glass_pill(draw, [1890, 200, 2150, 255], fill=(220, 38, 38, 220), border=None)
    draw.text((1915, 212), "今日热榜 TOP 1", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((1890, 275), "繁花 (Blossoms Shanghai)", fill=TEXT_PRIMARY, font=get_font(True, 42))
    draw.text((1890, 335), "王家卫导演作品 · 沪语4K原画 · 杜比全景声", fill=ACCENT_AMBER, font=get_font(False, 26))
    draw.text((1890, 395), "九十年代上海黄河路商海浮沉，阿宝与李李的商战传奇...", fill=TEXT_SECONDARY, font=get_font(False, 24))
    
    draw_glass_pill(draw, [1890, 470, 2240, 595], fill=SUCCESS_GREEN, border=None)
    draw_sf_play(draw, (1940, 532), 26, TEXT_PRIMARY)
    draw.text((1970, 508), "立即观看", fill=TEXT_PRIMARY, font=get_font(True, 34))

    # 5. Bottom Carousel Cards with REAL POSTERS!
    tabs = ["🔥 热播推荐", "🎬 4K臻彩大片", "📺 热门电视剧", "✨ 动漫追番", "🎞 纪录片专区", "📻 经典相声曲艺"]
    cur_tx = 280
    for idx, tab_name in enumerate(tabs):
        if idx == 0:
            draw_glass_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=ACCENT_CYAN, border=None)
            draw.text((cur_tx + 35, 742), tab_name, fill=(10, 20, 35), font=get_font(True, 28))
        else:
            draw_glass_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=(28, 36, 50, 180), border=(50, 60, 80), width=2)
            draw.text((cur_tx + 35, 742), tab_name, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_tx += 265

    poster_items = [
        ("wandering_earth2_poster.jpg", "流浪地球 2", "评分 9.1", "科幻 / 硬核 / 4K"),
        ("santi_poster.jpg", "三体 (电视剧版)", "评分 8.8", "剧情 / 悬疑 / 臻彩"),
        ("zhouchu_poster.jpg", "周处除三害", "评分 8.5", "动作 / 犯罪 / HDR"),
        ("kuangbiao_poster.jpg", "狂飙", "评分 9.0", "反腐 / 扫黑 / 原画"),
        ("dune2_poster.jpg", "沙丘 2", "评分 8.7", "科幻 / 巨制 / 杜比"),
    ]
    cur_cx = 280
    for img_file, title, score, tag in poster_items:
        draw_glass_panel(draw, [cur_cx, 840, cur_cx + 475, 1560], radius=24, fill=(24, 30, 44), border=(65, 85, 120), width=2)
        real_p = load_and_crop(img_file, (435, 500), radius=18)
        paste_image(img, real_p, (cur_cx + 20, 860))

        draw_glass_pill(draw, [cur_cx + 35, 880, cur_cx + 195, 940], fill=(234, 88, 12, 230), border=None)
        draw.text((cur_cx + 55, 895), score, fill=TEXT_PRIMARY, font=get_font(True, 24))

        draw.text((cur_cx + 30, 1380), title, fill=TEXT_PRIMARY, font=get_font(True, 32))
        draw.text((cur_cx + 30, 1435), tag, fill=TEXT_SECONDARY, font=get_font(False, 24))

        draw_glass_pill(draw, [cur_cx + 30, 1485, cur_cx + 445, 1545], fill=(37, 99, 235, 200), border=(96, 165, 250))
        draw.text((cur_cx + 170, 1498), "进入详情", fill=TEXT_PRIMARY, font=get_font(True, 24))

        cur_cx += 505

    img.save(os.path.join(OUTPUT_DIR, "scheme1-01-home.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-01-home.png"))
    print("Generated Scheme 1 Home with real photographic assets & Liquid Glass")


def create_scheme1_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_glass_pill(draw, [60, 40, 240, 130], fill=(30, 40, 58, 220), border=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((280, 58), "影片详情  /  庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 36))

    draw_glass_pill(draw, [2340, 40, 2820, 130], fill=(28, 38, 56, 200), border=(60, 75, 105), width=2)
    draw_sf_sparkles(draw, (2370, 85), 22, ACCENT_CYAN)
    draw.text((2400, 62), "SGSR1 超分就绪  ·  臻彩滤镜", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Real Qingyunian Video Preview Window!
    draw_glass_panel(draw, [60, 160, 1300, 840], radius=28, fill=(15, 20, 30), border=(50, 70, 100), width=3)
    qyn_preview = load_and_crop("qingyunian_still.jpg", (1220, 640), radius=22)
    paste_image(img, qyn_preview, (70, 170))

    # Play circle on video
    draw.ellipse([620, 430, 760, 570], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=3)
    draw_sf_play(draw, (695, 500), 40, ACCENT_CYAN)
    draw.text((545, 590), "轻触直接全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 28))

    draw_glass_pill(draw, [90, 190, 310, 250], fill=(16, 185, 129, 230), border=None)
    draw.text((115, 205), "4K 60FPS 极速", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Action buttons: Play, Switch Source, Collect, Push
    draw_glass_pill(draw, [60, 870, 540, 1010], fill=ACCENT_BLUE, border=(96, 165, 250), width=3)
    draw_sf_play(draw, (120, 940), 30, TEXT_PRIMARY)
    draw.text((150, 912), "立即全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 38))

    draw_glass_pill(draw, [570, 870, 840, 1010], fill=(28, 38, 56, 220), border=ACCENT_CYAN, width=2)
    draw_sf_sparkles(draw, (615, 940), 26, ACCENT_CYAN)
    draw.text((645, 918), "智能换源", fill=ACCENT_CYAN, font=get_font(True, 32))

    draw_glass_pill(draw, [870, 870, 1070, 1010], fill=(28, 38, 56, 220), border=(70, 85, 115), width=2)
    draw_sf_heart(draw, (915, 940), 26, TEXT_PRIMARY)
    draw.text((945, 918), "收藏", fill=TEXT_PRIMARY, font=get_font(True, 32))

    draw_glass_pill(draw, [1100, 870, 1300, 1010], fill=(45, 35, 70, 220), border=ACCENT_AMBER, width=2)
    draw_sf_airplay(draw, (1140, 940), 26, ACCENT_AMBER)
    draw.text((1170, 918), "投屏/推送", fill=ACCENT_AMBER, font=get_font(True, 32))

    # Clean Metadata Card
    draw_glass_panel(draw, [60, 1040, 1300, 1560], radius=24, fill=(24, 32, 46, 220), border=(50, 65, 95), width=2)
    draw.text((100, 1075), "庆余年 第二季 (Joy of Life 2)", fill=TEXT_PRIMARY, font=get_font(True, 40))
    draw.text((100, 1135), "评分 ★ 8.8  ·  古装 / 权谋 / 奇幻  ·  共 36 集全", fill=ACCENT_AMBER, font=get_font(True, 28))
    draw.text((100, 1185), "主演：张若昀 / 李沁 / 陈道明 / 吴刚 / 田雨", fill=TEXT_SECONDARY, font=get_font(False, 26))

    synopsis = (
        "剧情简介：该剧改编自猫腻同名畅销小说，讲述了拥有神秘身世的青年范闲，历经家族、"
        "江湖、庙堂的种种考验与锤炼，书写出一段不同寻常的人生传奇。第二季中范闲回归南庆，"
        "直面内库亏空与朝堂纷争，展开更波澜壮阔的智勇博弈。"
    )
    draw.text((100, 1250), synopsis[:48], fill=TEXT_SECONDARY, font=get_font(False, 26))
    draw.text((100, 1295), synopsis[48:96], fill=TEXT_SECONDARY, font=get_font(False, 26))
    draw.text((100, 1340), synopsis[96:], fill=TEXT_SECONDARY, font=get_font(False, 26))

    draw_glass_pill(draw, [100, 1450, 340, 1515], fill=(30, 58, 95, 200), border=ACCENT_CYAN, width=1)
    draw.text((130, 1468), "SGSR1 边缘锐化", fill=ACCENT_CYAN, font=get_font(True, 22))

    draw_glass_pill(draw, [360, 1450, 600, 1515], fill=(30, 58, 95, 200), border=ACCENT_CYAN, width=1)
    draw.text((390, 1468), "座舱环绕声场", fill=ACCENT_CYAN, font=get_font(True, 22))

    # RIGHT HALF: Sources & Episodes
    draw_glass_panel(draw, [1360, 160, 2820, 1560], radius=28, fill=(22, 30, 44, 220), border=(50, 65, 95), width=2)
    draw.text((1400, 225), "播放源：", fill=TEXT_MUTED, font=get_font(True, 30))
    sources = [("专线·4K臻彩 (当前)", True), ("备线·蓝光HDR", False), ("极速·原画", False), ("官方解析", False)]
    cur_sx = 1530
    for s_name, s_act in sources:
        if s_act:
            draw_glass_pill(draw, [cur_sx, 205, cur_sx + 290, 285], fill=ACCENT_BLUE, border=(96, 165, 250), width=2)
            draw.text((cur_sx + 30, 225), s_name, fill=TEXT_PRIMARY, font=get_font(True, 26))
        else:
            draw_glass_pill(draw, [cur_sx, 205, cur_sx + 260, 285], fill=(30, 40, 58), border=(55, 70, 95), width=1)
            draw.text((cur_sx + 35, 225), s_name, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_sx += 310

    draw.text((1400, 345), "剧集分段：", fill=TEXT_MUTED, font=get_font(True, 28))
    ranges = [("1-10", False), ("11-20 (当前)", True), ("21-30", False), ("31-36", False)]
    cur_rx = 1550
    for r_name, r_act in ranges:
        if r_act:
            draw_glass_pill(draw, [cur_rx, 330, cur_rx + 220, 400], fill=ACCENT_CYAN)
            draw.text((cur_rx + 30, 348), r_name, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_glass_pill(draw, [cur_rx, 330, cur_rx + 180, 400], fill=(30, 40, 58))
            draw.text((cur_rx + 35, 348), r_name, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_rx += 240

    ep_x0, ep_y0 = 1400, 450
    ep_w, ep_h = 155, 135
    spacing_x, spacing_y = 22, 22
    for row in range(7):
        for col in range(8):
            ep_num = 11 + row * 8 + col
            if ep_num > 36:
                break
            bx0 = ep_x0 + col * (ep_w + spacing_x)
            by0 = ep_y0 + row * (ep_h + spacing_y)
            bx1 = bx0 + ep_w
            by1 = by0 + ep_h

            if ep_num == 18:
                draw_glass_panel(draw, [bx0, by0, bx1, by1], radius=18, fill=ACCENT_BLUE, border=ACCENT_CYAN, width=3)
                draw.text((bx0 + 40, by0 + 25), f"{ep_num}", fill=TEXT_PRIMARY, font=get_font(True, 42))
                draw.text((bx0 + 25, by0 + 85), "正在播放", fill=ACCENT_CYAN, font=get_font(True, 20))
            else:
                draw_glass_panel(draw, [bx0, by0, bx1, by1], radius=18, fill=(28, 38, 54, 200), border=(50, 65, 90), width=2)
                draw.text((bx0 + 55, by0 + 42), f"{ep_num}", fill=TEXT_SECONDARY, font=get_font(True, 36))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-02-detail.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-02-detail.png"))
    print("Generated Scheme 1 Detail with real video frame & Liquid Glass")


def create_scheme1_03_player():
    img = Image.new("RGBA", (2880, 1620), (10, 12, 16))
    draw = ImageDraw.Draw(img, "RGBA")

    # Load REAL 4K Cinematic Background Video Frame!
    real_bg = load_and_crop("cinematic_player_bg.jpg", (2880, 1620))
    paste_image(img, real_bg, (0, 0))

    # Dark gradient overlays for readability
    for y in range(0, 320):
        alpha = int(220 * (1 - y / 320))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))
    for y in range(1080, 1620):
        alpha = int(240 * ((y - 1080) / 540))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))

    # Top Control Bar
    draw_glass_pill(draw, [60, 40, 240, 130], fill=(20, 28, 40, 220), border=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 退出", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((280, 60), "沙丘 2 (Dune: Part Two) - 4K 60FPS IMAX 原画", fill=TEXT_PRIMARY, font=get_font(True, 36))
    
    draw_glass_pill(draw, [1120, 55, 1380, 115], fill=(16, 185, 129, 200), border=None)
    draw.text((1145, 70), "IJK 硬解 · SGSR1", fill=TEXT_PRIMARY, font=get_font(True, 22))

    draw_glass_pill(draw, [1920, 45, 2280, 125], fill=(30, 40, 58, 220), border=ACCENT_CYAN, width=2)
    draw.text((1955, 65), "🎧 后台播放：开", fill=ACCENT_CYAN, font=get_font(True, 26))

    draw_glass_pill(draw, [2320, 45, 2580, 125], fill=(30, 40, 58, 220), border=ACCENT_AMBER, width=2)
    draw.text((2360, 65), "🔒 锁屏防触", fill=TEXT_SECONDARY, font=get_font(False, 26))

    draw_glass_pill(draw, [2620, 45, 2820, 125], fill=(30, 40, 58, 220), border=(60, 75, 100), width=2)
    draw.text((2675, 65), "15:43", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # Gesture Badges
    draw_glass_panel(draw, [60, 500, 180, 900], radius=30, fill=(20, 30, 45, 130), border=(50, 70, 100, 140), width=2)
    draw.text((85, 540), "☼\n\n左\n屏\n上\n下\n滑\n\n调\n亮\n度", fill=TEXT_SECONDARY, font=get_font(False, 24))

    draw_glass_panel(draw, [2700, 500, 2820, 900], radius=30, fill=(20, 30, 45, 130), border=(50, 70, 100, 140), width=2)
    draw.text((2725, 540), "🔊\n\n右\n屏\n上\n下\n滑\n\n调\n音\n量", fill=TEXT_SECONDARY, font=get_font(False, 24))

    draw_glass_pill(draw, [1220, 500, 1660, 580], fill=(0, 0, 0, 190), border=ACCENT_CYAN, width=2)
    draw.text((1260, 522), "⚡ 长按屏幕 2.0x 极速快进", fill=ACCENT_CYAN, font=get_font(True, 24))

    # Scrubber Bar
    draw.text((60, 1240), "01:24:15 / 02:46:30", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.rounded_rectangle([400, 1250, 2820, 1270], radius=10, fill=(60, 75, 95))
    draw.rounded_rectangle([400, 1250, 1620, 1270], radius=10, fill=ACCENT_CYAN)
    draw.ellipse([1600, 1237, 1646, 1283], fill=(255, 255, 255), outline=ACCENT_CYAN, width=4)

    # Bottom Control Island (Left) - Precision SF Symbols Icons!
    draw_glass_pill(draw, [60, 1360, 320, 1550], fill=ACCENT_BLUE, border=(96, 165, 250), width=3)
    draw_sf_pause(draw, (140, 1455), 36, TEXT_PRIMARY)
    draw.text((180, 1425), "暂停", fill=TEXT_PRIMARY, font=get_font(True, 38))

    draw_glass_pill(draw, [350, 1360, 560, 1550], fill=(28, 38, 54, 230), border=ACCENT_CYAN, width=2)
    draw_sf_replay_15(draw, (455, 1455), 48, ACCENT_CYAN)

    draw_glass_pill(draw, [590, 1360, 800, 1550], fill=(28, 38, 54, 230), border=ACCENT_CYAN, width=2)
    draw_sf_forward_15(draw, (695, 1455), 48, ACCENT_CYAN)

    draw_glass_pill(draw, [830, 1360, 1190, 1550], fill=(28, 48, 70, 240), border=(0, 210, 255), width=3)
    draw_sf_sparkles(draw, (880, 1455), 32, ACCENT_CYAN)
    draw.text((920, 1400), "画面预设", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((920, 1445), "臻彩高对比", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_glass_pill(draw, [1220, 1360, 1580, 1550], fill=(45, 36, 65, 240), border=ACCENT_AMBER, width=3)
    draw_sf_dolby(draw, (1265, 1455), 34, ACCENT_AMBER)
    draw.text((1305, 1400), "座舱音效", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((1305, 1445), "影院环绕声", fill=ACCENT_AMBER, font=get_font(True, 34))

    # Secondary Tools (Right)
    sec_buttons = [("倍速 1.25x", 1920), ("选集 (36)", 2160), ("比例 21:9", 2400), ("换源 (32ms)", 2640)]
    for btn_text, bx in sec_buttons:
        draw_glass_pill(draw, [bx, 1380, bx + 215, 1530], fill=(26, 34, 48, 220), border=(60, 75, 100), width=2)
        draw.text((bx + 28, 1435), btn_text, fill=TEXT_SECONDARY, font=get_font(True, 26))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-03-player.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-03-player.png"))
    print("Generated Scheme 1 Player with real 4K cinematic background & SF transport controls")


def create_scheme1_04_search():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_glass_pill(draw, [60, 40, 220, 180], fill=(28, 36, 50, 220), border=ACCENT_CYAN, width=2)
    draw.text((95, 88), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw_glass_pill(draw, [250, 40, 2350, 180], fill=(24, 34, 52, 240), border=ACCENT_CYAN, width=3)
    draw_sf_search(draw, (300, 110), 32, ACCENT_CYAN)
    draw.text((345, 85), "庆余年|", fill=TEXT_PRIMARY, font=get_font(True, 42))

    draw_glass_pill(draw, [1920, 55, 2310, 165], fill=ACCENT_BLUE, border=None)
    draw_sf_mic(draw, (1965, 110), 30, TEXT_PRIMARY)
    draw.text((2005, 88), "按住语音", fill=TEXT_PRIMARY, font=get_font(True, 32))

    draw_glass_pill(draw, [2390, 40, 2600, 180], fill=SUCCESS_GREEN, border=None)
    draw.text((2445, 88), "搜索", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_glass_pill(draw, [2630, 40, 2820, 180], fill=(45, 55, 75), border=None)
    draw.text((2685, 88), "清空", fill=TEXT_SECONDARY, font=get_font(False, 30))

    # LEFT HALF: Restored Large Virtual Keyboard (36 Keys, High Spacing, Retained as user wanted!)
    draw_glass_panel(draw, [60, 230, 1350, 1560], radius=28, fill=(22, 30, 44, 220), border=(50, 65, 95), width=2)
    draw.text((100, 270), "车载防颠簸大键盘 (支持简拼/全拼/首字母)", fill=TEXT_MUTED, font=get_font(True, 26))

    draw_glass_pill(draw, [960, 255, 1310, 315], fill=(30, 50, 80), border=ACCENT_CYAN, width=1)
    draw.text((990, 272), "⌨ 唤起系统输入法", fill=ACCENT_CYAN, font=get_font(True, 22))

    kb_rows = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"],
        ["A", "S", "D", "F", "G", "H", "J", "K", "L", "Del"],
        ["Z", "X", "C", "V", "B", "N", "M", "，", "。", "空格"]
    ]
    cur_ky = 350
    for r_idx, row in enumerate(kb_rows):
        cur_kx = 100
        for key in row:
            kw = 160 if key in ["Del", "空格"] else 105
            kh = 110
            if key == "Del":
                draw_glass_panel(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(60, 30, 40), border=(150, 50, 70), width=2)
                draw.text((cur_kx + 45, cur_ky + 32), key, fill=(255, 180, 190), font=get_font(True, 32))
            elif key == "空格":
                draw_glass_panel(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(35, 50, 75), border=ACCENT_CYAN, width=2)
                draw.text((cur_kx + 50, cur_ky + 32), key, fill=ACCENT_CYAN, font=get_font(True, 30))
            else:
                draw_glass_panel(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(30, 42, 60), border=(60, 75, 105), width=2)
                draw.text((cur_kx + 38, cur_ky + 30), key, fill=TEXT_PRIMARY, font=get_font(True, 36))
            cur_kx += kw + 15
        cur_ky += 135

    pinyin_suffixes = ["庆", "余", "年", "第二季", "电影", "电视剧", "动漫"]
    cur_px = 100
    for ps in pinyin_suffixes:
        draw_glass_pill(draw, [cur_px, 920, cur_px + 155, 1000], fill=(35, 55, 85), border=(70, 95, 135), width=1)
        draw.text((cur_px + 30, 945), ps, fill=TEXT_PRIMARY, font=get_font(True, 24))
        cur_px += 175

    # RIGHT HALF: Recent Searches & Real-Time Results with Real Poster Thumbnails!
    draw_glass_panel(draw, [1400, 230, 2820, 1560], radius=28, fill=(22, 30, 44, 220), border=(50, 65, 95), width=2)
    draw.text((1440, 270), "最近历史搜索：", fill=TEXT_MUTED, font=get_font(True, 28))
    hist_tags = ["庆余年2", "繁花", "周处除三害", "三体", "飞驰人生2"]
    cur_hx = 1660
    for ht in hist_tags:
        draw_glass_pill(draw, [cur_hx, 255, cur_hx + 190, 325], fill=(30, 40, 58), border=(60, 75, 100), width=1)
        draw.text((cur_hx + 30, 275), ht, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_hx += 210

    draw.text((1440, 370), "搜索结果 (联想匹配到 5 条结果)：", fill=ACCENT_CYAN, font=get_font(True, 30))
    results = [
        ("qingyunian_poster.jpg", "庆余年 第二季 (2024)", "36集全 · 4K 臻彩超清 · 张若昀 / 陈道明", "立即播放 ▶"),
        ("fanxiang_poster.jpg", "繁花 (2023)", "30集全 · 沪语4K原画 · 王家卫导演", "立即播放 ▶"),
        ("zhouchu_poster.jpg", "周处除三害 (2024)", "电影 · 4K HDR · 阮经天 / 袁富华", "立即播放 ▶"),
        ("wandering_earth2_poster.jpg", "流浪地球 2 (2023)", "电影 · 4K 60FPS · 吴京 / 刘德华", "立即播放 ▶"),
        ("kuangbiao_poster.jpg", "狂飙 (2023)", "39集全 · 扫黑反腐高分大剧", "立即播放 ▶")
    ]
    cur_ry = 430
    for poster_fn, r_title, r_desc, r_action in results:
        draw_glass_panel(draw, [1440, cur_ry, 2780, cur_ry + 195], radius=20, fill=(28, 38, 56, 220), border=(60, 80, 115), width=2)
        thumb = load_and_crop(poster_fn, (130, 165), radius=14)
        paste_image(img, thumb, (1460, cur_ry + 15))

        draw.text((1620, cur_ry + 35), r_title, fill=TEXT_PRIMARY, font=get_font(True, 34))
        draw.text((1620, cur_ry + 95), r_desc, fill=TEXT_SECONDARY, font=get_font(False, 26))
        draw_glass_pill(draw, [2500, cur_ry + 45, 2740, cur_ry + 150], fill=SUCCESS_GREEN, border=None)
        draw.text((2530, cur_ry + 78), r_action, fill=TEXT_PRIMARY, font=get_font(True, 26))
        cur_ry += 220

    img.save(os.path.join(OUTPUT_DIR, "scheme1-04-search.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-04-search.png"))
    print("Generated Scheme 1 Search with real poster thumbnails & Liquid Glass")


def create_scheme1_05_settings():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_glass_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), border=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((260, 60), "播放器设置中心  /  核心解码与音画调优", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # LEFT COLUMN: Category Sidebar
    draw_glass_panel(draw, [60, 160, 680, 1560], radius=28, fill=(20, 28, 40, 240), border=(50, 65, 90), width=2)
    settings_cats = [
        ("画面色彩与超分引擎", True, draw_sf_sparkles),
        ("座舱声场与杜比音效", False, draw_sf_dolby),
        ("播放解码与后台行为", False, draw_sf_tv),
        ("触控手势与长按倍速", False, draw_sf_play),
        ("双模应用内返回控件", False, draw_sf_clock),
        ("订阅源与多仓管理", False, draw_sf_airplay),
        ("关于 TVBox Car.7-r2", False, draw_sf_gear)
    ]
    cur_scy = 200
    for c_title, c_act, c_icon in settings_cats:
        if c_act:
            draw_glass_panel(draw, [80, cur_scy, 660, cur_scy + 130], radius=20, fill=(35, 65, 110), border=ACCENT_CYAN, width=3)
            c_icon(draw, (115, cur_scy + 65), 26, ACCENT_CYAN)
            draw.text((150, cur_scy + 45), c_title, fill=TEXT_PRIMARY, font=get_font(True, 28))
        else:
            draw_glass_panel(draw, [80, cur_scy, 660, cur_scy + 130], radius=20, fill=(26, 34, 48, 160), border=(45, 58, 80), width=1)
            c_icon(draw, (115, cur_scy + 65), 24, TEXT_MUTED)
            draw.text((150, cur_scy + 45), c_title, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_scy += 155

    # RIGHT COLUMN: Restored Detailed Settings Cards (minus fake D-gear)
    # CARD 1: Picture Enhancement Presets
    draw_glass_panel(draw, [720, 160, 2820, 500], radius=26, fill=(24, 34, 50, 220), border=(55, 75, 105), width=2)
    draw.text((760, 200), "SDR / HDR 画面调优预设", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 250), "针对 17.3 英寸 3K 屏幕特别调校的 GLSL 着色器矩阵，实时渲染不重启解码器", fill=TEXT_MUTED, font=get_font(False, 26))

    p_presets = ["原始自然", "臻彩高饱和 (推荐)", "动漫高对比", "影院胶片", "夜间护眼琥珀", "日间强光防反光"]
    cur_px = 760
    for idx, p_name in enumerate(p_presets):
        pw = 320
        if idx == 1:
            draw_glass_pill(draw, [cur_px, 320, cur_px + pw, 440], fill=ACCENT_CYAN, border=None)
            draw.text((cur_px + 30, 360), p_name, fill=(10, 20, 35), font=get_font(True, 26))
        else:
            draw_glass_pill(draw, [cur_px, 320, cur_px + pw, 440], fill=(30, 42, 60), border=(60, 75, 100), width=2)
            draw.text((cur_px + 35, 360), p_name, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_px += pw + 18

    # CARD 2: Snapdragon Super Resolution (SGSR1)
    draw_glass_panel(draw, [720, 530, 2820, 750], radius=26, fill=(24, 34, 50, 220), border=(55, 75, 105), width=2)
    draw.text((760, 570), "高通 SGSR1 硬件级边缘超分辨率算法", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 620), "通过单通道自适应边缘检测锐化算法，将 720P/1080P 片源智能放大至接近 3K 物理分辨率清晰度", fill=TEXT_MUTED, font=get_font(False, 26))
    draw_glass_pill(draw, [2540, 590, 2760, 680], fill=SUCCESS_GREEN, border=None)
    draw.ellipse([2670, 595, 2750, 675], fill=(255, 255, 255))
    draw.text((2570, 618), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 3: Temporal Smoothing (时域平滑补帧)
    draw_glass_panel(draw, [720, 780, 2820, 1000], radius=26, fill=(24, 34, 50, 220), border=(55, 75, 105), width=2)
    draw.text((760, 820), "时域平滑防抖算法 (Temporal Smoothing)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 870), "针对低帧率动漫和老剧集，消除运动残影与画面抖动，保持车内观影流畅体验", fill=TEXT_MUTED, font=get_font(False, 26))
    draw_glass_pill(draw, [2540, 840, 2760, 930], fill=SUCCESS_GREEN, border=None)
    draw.ellipse([2670, 845, 2750, 925], fill=(255, 255, 255))
    draw.text((2570, 868), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 4: Touch Gestures & Background Play
    draw_glass_panel(draw, [720, 1030, 2820, 1550], radius=26, fill=(28, 40, 60, 240), border=ACCENT_CYAN, width=2)
    draw.text((760, 1070), "👆 17.3 英寸触控交互与切后台音频配置", fill=ACCENT_CYAN, font=get_font(True, 34))
    draw.text((760, 1120), "个性化触控习惯：支持全屏长按倍速、边缘滑动手势与后台音频混音播放", fill=TEXT_MUTED, font=get_font(False, 26))

    touch_options = [
        ("全屏长按触发极速快进：长按屏幕任意位置触发 2.0x 播放，松手恢复原速", True),
        ("切出车机看导航时保持后台音频：剧集声音不中断，与导航播报平稳混音", True),
        ("左右分屏垂直滑动手势：左侧滑动调节应用内亮度，右侧滑动调节媒体音量", True),
    ]
    cur_osy = 1190
    for o_text, o_val in touch_options:
        draw.text((760, cur_osy + 25), o_text, fill=TEXT_PRIMARY, font=get_font(False, 28))
        if o_val:
            draw_glass_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=SUCCESS_GREEN, border=None)
            draw.ellipse([2690, cur_osy + 15, 2750, cur_osy + 75], fill=(255, 255, 255))
        else:
            draw_glass_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=(60, 70, 90), border=None)
            draw.ellipse([2570, cur_osy + 15, 2630, cur_osy + 75], fill=(160, 170, 190))
        cur_osy += 105

    img.save(os.path.join(OUTPUT_DIR, "scheme1-05-settings.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-05-settings.png"))
    print("Generated Scheme 1 Settings with Liquid Glass")


def create_scheme1_06_live():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_glass_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), border=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((260, 60), "电视直播  /  央视·卫视高清频道组", fill=TEXT_PRIMARY, font=get_font(True, 36))

    draw_glass_pill(draw, [2360, 40, 2820, 130], fill=(26, 36, 52, 200), border=(55, 75, 105), width=2)
    draw.text((2400, 62), "⚡ 极速解码 · 0秒切台", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Channel Groups & Channel List
    draw_glass_panel(draw, [60, 160, 1100, 1560], radius=28, fill=(20, 28, 40, 240), border=(50, 65, 90), width=2)
    
    c_groups = ["央视频道", "热门卫视", "高清电影", "体育赛事", "地方频道"]
    cur_gx = 90
    for idx, g_name in enumerate(c_groups):
        gw = 180
        if idx == 0:
            draw_glass_pill(draw, [cur_gx, 190, cur_gx + gw, 260], fill=ACCENT_CYAN, border=None)
            draw.text((cur_gx + 30, 210), g_name, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_glass_pill(draw, [cur_gx, 190, cur_gx + gw, 260], fill=(28, 38, 54), border=(45, 58, 80), width=1)
            draw.text((cur_gx + 30, 210), g_name, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_gx += gw + 15

    channels = [
        ("01", "CCTV-1 综合超清", "正在播出：新闻联播", True),
        ("02", "CCTV-5 体育赛事", "正在播出：天下足球", False),
        ("03", "CCTV-6 电影高清", "正在播出：经典译制大片", False),
        ("04", "湖南卫视 HD", "正在播出：热门综艺晚会", False),
        ("05", "浙江卫视 HD", "正在播出：奔跑吧", False),
        ("06", "东方卫视 HD", "正在播出：东方剧场", False),
        ("07", "江苏卫视 HD", "正在播出：非诚勿扰", False),
    ]
    cur_cy = 290
    for c_id, c_name, c_epg, c_act in channels:
        if c_act:
            draw_glass_panel(draw, [90, cur_cy, 1070, cur_cy + 155], radius=20, fill=(35, 65, 110), border=ACCENT_CYAN, width=3)
            draw.text((120, cur_cy + 30), c_id, fill=ACCENT_CYAN, font=get_font(True, 40))
            draw.text((200, cur_cy + 30), c_name, fill=TEXT_PRIMARY, font=get_font(True, 34))
            draw.text((200, cur_cy + 90), f"▶ {c_epg}", fill=ACCENT_AMBER, font=get_font(False, 26))
            draw_glass_pill(draw, [920, cur_cy + 45, 1040, cur_cy + 110], fill=SUCCESS_GREEN, border=None)
            draw.text((945, cur_cy + 62), "播放中", fill=TEXT_PRIMARY, font=get_font(True, 20))
        else:
            draw_glass_panel(draw, [90, cur_cy, 1070, cur_cy + 155], radius=20, fill=(26, 34, 48, 180), border=(45, 58, 80), width=1)
            draw.text((120, cur_cy + 30), c_id, fill=TEXT_MUTED, font=get_font(True, 40))
            draw.text((200, cur_cy + 30), c_name, fill=TEXT_PRIMARY, font=get_font(True, 32))
            draw.text((200, cur_cy + 90), c_epg, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_cy += 175

    # RIGHT HALF: Live TV Screen Preview & Quick Actions with REAL CCTV-1 Still!
    draw_glass_panel(draw, [1140, 160, 2820, 1560], radius=28, fill=(16, 22, 32), border=(50, 68, 98), width=3)
    cctv_im = load_and_crop("cctv1_still.jpg", (1640, 970), radius=22)
    paste_image(img, cctv_im, (1160, 180))

    draw.ellipse([1920, 620, 2040, 740], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=3)
    draw_sf_play(draw, (1985, 680), 36, ACCENT_CYAN)
    draw.text((1820, 770), "轻触唤起全屏沉浸直播", fill=TEXT_PRIMARY, font=get_font(True, 28))

    draw_glass_pill(draw, [1190, 210, 1420, 275], fill=(220, 38, 38, 220), border=None)
    draw.text((1220, 228), "● 正在直播", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Bottom Actions for Live TV
    draw_glass_panel(draw, [1160, 1180, 2800, 1520], radius=24, fill=(24, 34, 50, 220), border=(50, 65, 95), width=2)
    draw.text((1200, 1220), "当前频道：CCTV-1 综合频道 (1080P 50FPS 极速源)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((1200, 1280), "节目流预告：19:00 新闻联播  |  19:35 焦点访谈  |  20:00 黄金档剧场", fill=TEXT_SECONDARY, font=get_font(False, 28))

    draw_glass_pill(draw, [1200, 1360, 1620, 1480], fill=ACCENT_BLUE, border=(96, 165, 250), width=2)
    draw_sf_play(draw, (1245, 1420), 24, TEXT_PRIMARY)
    draw.text((1280, 1395), "全屏沉浸直播", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_glass_pill(draw, [1660, 1360, 1960, 1480], fill=(30, 42, 60), border=ACCENT_CYAN, width=2)
    draw_sf_sparkles(draw, (1705, 1420), 22, ACCENT_CYAN)
    draw.text((1735, 1400), "切换源 (3条备用)", fill=ACCENT_CYAN, font=get_font(True, 28))

    draw_glass_pill(draw, [2000, 1360, 2300, 1480], fill=(30, 42, 60), border=(60, 75, 100), width=2)
    draw_sf_heart(draw, (2045, 1420), 22, TEXT_SECONDARY)
    draw.text((2075, 1400), "收藏该频道", fill=TEXT_SECONDARY, font=get_font(True, 28))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-06-live.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-06-live.png"))
    print("Generated Scheme 1 Live TV with real CCTV studio still & Liquid Glass")



def generate_comparison_card(before_path, after_path, out_path, title_text, problem_desc, solution_desc):
    comp_w, comp_h = 2880, 1800
    comp = Image.new("RGBA", (comp_w, comp_h), (11, 15, 22))
    draw = ImageDraw.Draw(comp, "RGBA")

    draw.text((60, 40), title_text, fill=TEXT_PRIMARY, font=get_font(True, 46))
    draw_glass_pill(draw, [2340, 40, 2820, 120], fill=ACCENT_BLUE, border=None)
    draw.text((2380, 62), "17.3寸 3K 视频播放重构", fill=TEXT_PRIMARY, font=get_font(True, 26))

    img_before = Image.open(before_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)
    img_after = Image.open(after_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)

    # Left: Before
    draw_glass_panel(draw, [55, 175, 1445, 965], radius=20, fill=(35, 25, 25), border=(220, 38, 38), width=3)
    comp.paste(img_before, (60, 180))
    draw_glass_pill(draw, [80, 200, 420, 270], fill=(220, 38, 38, 230), border=None)
    draw.text((105, 218), "原版问题状态 (BEFORE)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Right: After
    draw_glass_panel(draw, [1475, 175, 2825, 965], radius=20, fill=(20, 35, 30), border=SUCCESS_GREEN, width=3)
    comp.paste(img_after, (1480, 180))
    draw_glass_pill(draw, [1500, 200, 1860, 270], fill=(16, 185, 129, 230), border=None)
    draw.text((1525, 218), "整改后建议稿 (AFTER)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Bottom Diagnosis Box
    draw_glass_panel(draw, [55, 990, 1445, 1740], radius=24, fill=(25, 18, 22, 240), border=(180, 40, 40), width=2)
    draw.text((95, 1030), "🚨 原版交互与排版缺陷诊断", fill=(248, 113, 113), font=get_font(True, 34))
    cur_py = 1100
    for line in problem_desc:
        draw.text((95, cur_py), line, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_py += 55

    draw_glass_panel(draw, [1475, 990, 2825, 1740], radius=24, fill=(18, 30, 28, 240), border=SUCCESS_GREEN, width=2)
    draw.text((1515, 1030), "✨ 优化设计方案要点 (保留全部核心功能)", fill=(52, 211, 153), font=get_font(True, 34))
    cur_sy = 1100
    for line in solution_desc:
        draw.text((1515, cur_sy), line, fill=TEXT_PRIMARY, font=get_font(False, 26))
        cur_sy += 55

    comp.save(out_path)
    print(f"Generated comparison: {out_path}")

def main():
    print("=== Generating Scheme 1 (Obsidian Glass / Restored & Refined V1) ===")
    create_scheme1_01_home()
    create_scheme1_02_detail()
    create_scheme1_03_player()
    create_scheme1_04_search()
    create_scheme1_05_settings()
    create_scheme1_06_live()

    print("\n=== Generating 6 Before vs After Side-by-Side Comparisons ===")
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-home.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-01-home.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-01-home.png"),
        title_text="01. 主界面流媒体中心与无缝续播设计 (Home & Media Center)",
        problem_desc=[
            "• 设置按钮被排在屏幕最右侧边缘 (x: 2302px / 30.6cm)，驾驶座伸手极其困难；",
            "• 顶部日期独占 1918px (25.5cm) 宽，信息利用率低下，属于电视远视距残余；",
            "• 分类 Tab 条高度仅 101px (13.4mm)，手指滑动极易误触；",
            "• 缺乏快速继续播放卡片，每次打开应用找上次未看完的剧集链路过长；",
            "• 存在刺眼的电视白色聚焦框，触摸屏缺少平滑按压反馈。"
        ],
        solution_desc=[
            "• 设立【左侧垂直流媒体导航 Rail】(x: 0~240px)，设置/影视/直播/推送一触即达；",
            "• 新增【上次播放无缝续播 Hero 卡片】，直观展示剧集进度与【继续播放】大按键；",
            "• 顶部集成搜索栏与分类快速过滤标签（电影/剧集/4K HDR/动漫）；",
            "• 海报触控卡片重构为 475×720px 大规格，彻底废除 TV 焦点白框；",
            "• 深空黑曜微光质感，白天高清晰防反光，夜间护眼不刺眼。"
        ]
    )

    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-detail.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-02-detail.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-02-detail.png"),
        title_text="02. 影片详情与剧集选集触控矩阵 (Detail & Episodes)",
        problem_desc=[
            "• 全屏、快速搜索、收藏、换源等按键全部沦陷在右侧 (x: 1600~2700px)；",
            "• 界面中央直接暴露 1552px 长的未格式化原始 HTTP play.m3u8 字符串；",
            "• 存在 previewPlayerBlock 遥控器阻断假层，阻碍小窗直接点击全屏；",
            "• 剧集列表下边距仅 1mm，长剧集反复滑动极度繁琐；",
            "• 换源按钮距离过远，操作不顺手。"
        ],
        solution_desc=[
            "• 黄金操作按键群【全屏播放】、【智能换源】、【收藏】、【投屏推送】移至左侧近端；",
            "• 隐藏原始 URL 字符串，重构为整洁优雅的演职员、评分与剧情简介卡片；",
            "• 剧集重构为 155×135px 超大方块矩阵，带 22px 充足安全间距与正在播放光标；",
            "• 提供 1-10、11-20、21-30 分段快速跳选胶囊，长剧选集秒直达；",
            "• 移除遥控假层，16:9 预览小窗支持轻触直接全屏。"
        ]
    )

    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-player-controls-enabled.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-03-player.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-03-player.png"),
        title_text="03. 全屏播放器与现代触控播控岛 (Fullscreen Player)",
        problem_desc=[
            "• 底部平铺 18 个细碎小按钮，单键宽 17.6mm，间距仅 3.6mm，极易误触；",
            "• 时间显示框宽度仅 22mm，长视频时间戳 (01:23:45) 发生严重截断；",
            "• 右侧音效胶囊落在 x: 2120px，主驾必须欠身；",
            "• 缺乏屏幕触控长按倍速与滑动手势，调音量调亮度必须费力点小按钮；",
            "• 电视快进黑色弹窗居中遮挡视频主体画面。"
        ],
        solution_desc=[
            "• 打造左下角 5 大高容错播控岛：暂停/播放、±15s 快进退、臻彩预设、杜比音效；",
            "• 进度条加粗至 20px，配合 46px 吸附手柄与放大的 42:15 / 58:30 无截断时间气泡；",
            "• 新增【长按屏幕 2.0x 极速快进】：手指长按即加速、松手恢复，体验极佳；",
            "• 建立车载手势层：左侧垂直滑调亮度、右侧垂直滑调音量、双击暂停；",
            "• 右下角收拢倍速选择、选集列表、画面比例（适应/填充/21:9 无黑边）。"
        ]
    )

    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-search.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-04-search.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-04-search.png"),
        title_text="04. 搜索大键盘与多端快速检索 (Search & Virtual Keyboard)",
        problem_desc=[
            "• 原版 TV 软键盘按键密集，且代码中主动调用 hiddenImm() 封杀原生输入法；",
            "• 右半屏 50% 面积 (18.5cm) 全黑空白，大屏显示空间严重浪费；",
            "• 单列纵向热词排至 y: 1934px 跌出屏幕底端，查看成本极高；",
            "• 缺少车载语音搜片按键。"
        ],
        solution_desc=[
            "• 保留并重构车载大软键盘（单键 115×110px），提供简拼/全拼与拼音后缀大按键；",
            "• 解除 hiddenImm() 封杀，支持一键唤起系统原生输入法与车载手写板；",
            "• 顶部集成【🎙 按住语音】按键，支持语音直接搜片；",
            "• 右侧 50% 空间打造最近历史搜索标签与双列富媒体搜索结果列表，带立即播放。"
        ]
    )

    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-settings-top.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-05-settings.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-05-settings.png"),
        title_text="05. 双栏系统设置中心与画质调优 (Settings & Optimization)",
        problem_desc=[
            "• 左侧分类导航菜单被代码写死 visibility='gone'，40+ 设置项只能单列硬滑；",
            "• 高级设置项通栏排布撕裂：标题在 x: 124px，开关在 x: 2576px，视距拉扯 32cm；",
            "• 缺乏关于全屏长按倍速速率、手势调节、后台音频播放等现代配置项。"
        ],
        solution_desc=[
            "• 恢复左侧垂直分类导航 Rail (x: 60~680px)，清晰划分为 7 大实用业务板块；",
            "• 紧凑成组卡片排布，消除 32cm 视距撕裂，开关紧挨配置说明右侧；",
            "• 画面预设升级为大号分段式胶囊调节条（原始、臻彩、动漫、影院、护眼、防眩）；",
            "• 完整保留高通 SGSR1 边缘超分辨率与时域平滑补帧配置；",
            "• 增加长按倍速速率设置（2.0x / 3.0x）与切后台音频保活开关。"
        ]
    )

    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-live-channels.png",
        after_path=os.path.join(OUTPUT_DIR, "scheme1-06-live.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-06-live.png"),
        title_text="06. 电视直播与 EPG 节目流重构 (Live TV & EPG Stream)",
        problem_desc=[
            "• 原版直播中台号浮层字号高达 100mm (225px)，在近距车机前粗糙突兀；",
            "• 频道列表与节目单缺乏大触控方块，在行车颠簸中极易点错频道；",
            "• 缺乏快速换源与多频道组（央视、卫视、体育、影视）清晰标签分类。"
        ],
        solution_desc=[
            "• 左侧建立清晰的电视频道分类滑轨与大号频道列表（单项高 155px，防误触）；",
            "• 实时显示 EPG 节目流（正在播出 / 下个预告），带科技绿“播放中”光标；",
            "• 右侧 16:9 实时超清小窗预览，带轻触一键全屏与多线路（3条备用源）秒切；",
            "• 彻底替换原有黑屏伪需求，完整呈现 TVBox 核心直播能力。"
        ]
    )

    print("\nAll Schemes and Comparisons Successfully Generated!")

if __name__ == "__main__":
    main()
