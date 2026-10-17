"""
generate_ux_redesign_mockups.py
Full photographic rendering of 3 complete in-car UI design schemes:
- Scheme 1: Obsidian Glass (Restored & Refined Version 1, 100% TVBox features, real posters/stills)
- Scheme 2: Cinematic Infinity (Apple TV / Infuse style borderless panoramic design, real posters/stills)
- Scheme 3: Studio Cockpit (Dual-zone pro media workstation design, real posters/stills)
Plus 6 Before vs After side-by-side comparison graphics.
"""

import os
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

# Color Palettes
BG_OBSIDIAN = (13, 17, 23)          # Scheme 1: Deep obsidian
BG_CINEMATIC = (8, 10, 15)          # Scheme 2: Ultra dark cinema
BG_STUDIO = (15, 20, 28)            # Scheme 3: Pro slate

ACCENT_CYAN = (0, 210, 255)         # Neon cyan
ACCENT_BLUE = (37, 99, 235)         # Royal blue
ACCENT_AMBER = (255, 179, 71)       # Amber gold
ACCENT_PURPLE = (168, 85, 247)      # Studio purple
TEXT_PRIMARY = (255, 255, 255)      # White
TEXT_SECONDARY = (160, 172, 190)    # Subtext
TEXT_MUTED = (105, 115, 130)        # Dim text
SUCCESS_GREEN = (16, 185, 129)      # Green

# ----------------------------------------------------------------------
# IMAGE & DRAWING HELPERS
# ----------------------------------------------------------------------

def draw_rounded_rect(draw, box, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

def draw_pill(draw, box, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    radius = (y1 - y0) // 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

def load_and_crop(filename, target_size, radius=0):
    """Loads a real asset image, centers-crops to aspect ratio, resizes with LANCZOS, and optionally rounds corners."""
    p = os.path.join(ASSET_DIR, filename)
    tw, th = target_size
    if not os.path.exists(p):
        # Fallback colored card
        im = Image.new("RGBA", (tw, th), (35, 45, 65))
        return im
    try:
        orig = Image.open(p).convert("RGBA")
        ow, oh = orig.size
        target_aspect = tw / th
        orig_aspect = ow / oh

        if orig_aspect > target_aspect:
            # Crop width
            new_w = int(oh * target_aspect)
            x_offset = (ow - new_w) // 2
            cropped = orig.crop((x_offset, 0, x_offset + new_w, oh))
        else:
            # Crop height
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
        return Image.new("RGBA", (tw, th), (35, 45, 65))

def paste_image(canvas, im, pos):
    canvas.paste(im, pos, im if im.mode == "RGBA" else None)

def draw_icon_play(draw, center, size, color=(255, 255, 255)):
    cx, cy = center
    r = size // 2
    points = [(cx - r // 2, cy - r), (cx - r // 2, cy + r), (cx + r, cy)]
    draw.polygon(points, fill=color)

def draw_icon_pause(draw, center, size, color=(255, 255, 255)):
    cx, cy = center
    w = size // 4
    h = size
    draw.rectangle([cx - w - 4, cy - h // 2, cx - 4, cy + h // 2], fill=color)
    draw.rectangle([cx + 4, cy - h // 2, cx + w + 4, cy + h // 2], fill=color)

def draw_icon_search(draw, center, size, color=(255, 255, 255)):
    cx, cy = center
    r = size // 3
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=3)
    draw.line([cx + int(r * 0.7), cy + int(r * 0.7), cx + r + 8, cy + r + 8], fill=color, width=4)

def draw_icon_mic(draw, center, size, color=(255, 255, 255)):
    cx, cy = center
    w = size // 3
    h = size // 2
    draw.rounded_rectangle([cx - w // 2, cy - h, cx + w // 2, cy], radius=6, fill=color)
    draw.arc([cx - w - 2, cy - h // 2, cx + w + 2, cy + h // 2], start=0, end=180, fill=color, width=2)
    draw.line([cx, cy + h // 2, cx, cy + h // 2 + 6], fill=color, width=2)


# ==============================================================================
# 方案一：【深空黑曜微光版 (Obsidian Glass)】(真海报/真剧照/第一版完整还原)
# ==============================================================================

def create_scheme1_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")
    draw.ellipse([800, -200, 2200, 600], fill=(20, 35, 60, 120))

    # 1. Left Vertical Dock
    draw.rectangle([0, 0, 240, 1620], fill=(18, 24, 34, 240))
    draw.line([240, 0, 240, 1620], fill=(45, 55, 72, 255), width=2)
    draw.text((45, 45), "TVBOX", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((48, 95), "CAR 3K", fill=TEXT_MUTED, font=get_font(True, 24))

    nav_items = [
        ("★ 精选推荐", True),
        ("▦ 分类专题", False),
        ("📺 电视直播", False),
        ("⏱ 播放历史", False),
        ("♥ 我的收藏", False),
        ("📲 多端推送", False),
        ("⚙ 系统设置", False),
    ]
    cur_y = 180
    for title, active in nav_items:
        if active:
            draw_rounded_rect(draw, [20, cur_y, 220, cur_y + 105], radius=18, fill=(30, 58, 100, 240), outline=ACCENT_CYAN, width=3)
            draw.rectangle([20, cur_y + 28, 28, cur_y + 78], fill=ACCENT_CYAN)
            draw.text((45, cur_y + 32), title, fill=TEXT_PRIMARY, font=get_font(True, 28))
        else:
            draw_rounded_rect(draw, [20, cur_y, 220, cur_y + 105], radius=18, fill=(24, 30, 42, 160))
            draw.text((45, cur_y + 32), title, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_y += 125

    draw_rounded_rect(draw, [20, 1470, 220, 1570], radius=18, fill=(26, 36, 52, 220))
    draw.text((45, 1492), "4K 引擎就绪", fill=SUCCESS_GREEN, font=get_font(True, 24))
    draw.text((45, 1526), "IJK 硬解 · SGSR1", fill=TEXT_MUTED, font=get_font(False, 18))

    # 2. Top Header Bar
    draw_pill(draw, [280, 40, 800, 130], fill=(26, 34, 48, 200), outline=(50, 60, 80), width=2)
    draw.text((320, 62), "✨ 4K 臻彩超清已就绪 · 杜比全景声 7.1", fill=ACCENT_CYAN, font=get_font(False, 26))

    draw_pill(draw, [840, 40, 2300, 130], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((890, 60), "🔍 语音搜片 / 首字母搜剧 (例如: QYN2 庆余年)", fill=TEXT_MUTED, font=get_font(False, 28))
    draw_pill(draw, [2020, 50, 2280, 120], fill=(37, 99, 235))
    draw.text((2055, 62), "🎙 按住语音", fill=TEXT_PRIMARY, font=get_font(True, 26))

    draw_pill(draw, [2340, 40, 2800, 130], fill=(26, 34, 48, 200), outline=(50, 60, 80), width=2)
    draw.text((2400, 60), "15:42  5G 📶 100%", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # 3. Main Stage: Left Hero Resume Card with REAL Qingyunian Still!
    draw_rounded_rect(draw, [280, 170, 1380, 680], radius=32, fill=(24, 32, 48, 240), outline=(55, 75, 110), width=3)
    # Load and embed real Qingyunian still in the hero card
    qyn_still = load_and_crop("qingyunian_still.jpg", (440, 470), radius=22)
    paste_image(img, qyn_still, (300, 190))

    draw_pill(draw, [760, 200, 970, 255], fill=(37, 99, 235, 200))
    draw.text((785, 212), "上次未看完", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((760, 275), "庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((760, 335), "第 18 集 · 4K 臻彩超清 (已播 72% / 42分15秒)", fill=ACCENT_CYAN, font=get_font(False, 26))
    
    draw_rounded_rect(draw, [760, 400, 1340, 416], radius=8, fill=(45, 55, 72))
    draw_rounded_rect(draw, [760, 400, 1180, 416], radius=8, fill=ACCENT_CYAN)

    draw_pill(draw, [760, 470, 1070, 595], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((810, 508), "▶ 继续播放", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_pill(draw, [1100, 470, 1340, 595], fill=(45, 55, 75), outline=(75, 85, 105), width=2)
    draw.text((1140, 510), "▦ 选集列表", fill=TEXT_SECONDARY, font=get_font(True, 28))

    # 4. Right Stage: Today Hot Feature Card with REAL Fanxiang Backdrop!
    draw_rounded_rect(draw, [1420, 170, 2800, 680], radius=32, fill=(28, 38, 56, 240), outline=(60, 80, 115), width=2)
    fx_img = load_and_crop("fanxiang_poster.jpg", (420, 470), radius=22)
    paste_image(img, fx_img, (1440, 190))

    draw_pill(draw, [1890, 200, 2140, 255], fill=(220, 38, 38, 200))
    draw.text((1915, 212), "今日热榜 TOP 1", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((1890, 275), "繁花 (Blossoms Shanghai)", fill=TEXT_PRIMARY, font=get_font(True, 42))
    draw.text((1890, 335), "王家卫导演作品 · 沪语4K原画 · 杜比全景声", fill=ACCENT_AMBER, font=get_font(False, 26))
    draw.text((1890, 395), "九十年代上海黄河路商海浮沉，阿宝与李李的商战传奇...", fill=TEXT_SECONDARY, font=get_font(False, 24))
    
    draw_pill(draw, [1890, 470, 2240, 595], fill=(16, 185, 129))
    draw.text((1940, 508), "▶ 立即观看", fill=TEXT_PRIMARY, font=get_font(True, 34))

    # 5. Bottom Carousel Cards with REAL POSTERS!
    tabs = ["🔥 热播推荐", "🎬 4K臻彩大片", "📺 热门电视剧", "✨ 动漫追番", "🎞 纪录片专区", "📻 经典相声曲艺"]
    cur_tx = 280
    for idx, tab_name in enumerate(tabs):
        if idx == 0:
            draw_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=ACCENT_CYAN)
            draw.text((cur_tx + 35, 742), tab_name, fill=(10, 20, 35), font=get_font(True, 28))
        else:
            draw_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=(28, 36, 50, 180), outline=(50, 60, 80), width=2)
            draw.text((cur_tx + 35, 742), tab_name, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_tx += 265

    # 5 real posters
    poster_items = [
        ("wandering_earth2_poster.jpg", "流浪地球 2", "评分 9.1", "科幻 / 硬核 / 4K"),
        ("santi_poster.jpg", "三体 (电视剧版)", "评分 8.8", "剧情 / 悬疑 / 臻彩"),
        ("zhouchu_poster.jpg", "周处除三害", "评分 8.5", "动作 / 犯罪 / HDR"),
        ("kuangbiao_poster.jpg", "狂飙", "评分 9.0", "反腐 / 扫黑 / 原画"),
        ("dune2_poster.jpg", "沙丘 2", "评分 8.7", "科幻 / 巨制 / 杜比"),
    ]
    cur_cx = 280
    for img_file, title, score, tag in poster_items:
        draw_rounded_rect(draw, [cur_cx, 840, cur_cx + 475, 1560], radius=24, fill=(24, 30, 44), outline=(65, 85, 120), width=2)
        real_p = load_and_crop(img_file, (435, 500), radius=18)
        paste_image(img, real_p, (cur_cx + 20, 860))

        # Score badge
        draw_pill(draw, [cur_cx + 35, 880, cur_cx + 195, 940], fill=(234, 88, 12, 230))
        draw.text((cur_cx + 55, 895), score, fill=TEXT_PRIMARY, font=get_font(True, 24))

        draw.text((cur_cx + 30, 1380), title, fill=TEXT_PRIMARY, font=get_font(True, 32))
        draw.text((cur_cx + 30, 1435), tag, fill=TEXT_SECONDARY, font=get_font(False, 24))

        draw_pill(draw, [cur_cx + 30, 1485, cur_cx + 445, 1545], fill=(37, 99, 235, 200))
        draw.text((cur_cx + 170, 1498), "进入详情", fill=TEXT_PRIMARY, font=get_font(True, 24))

        cur_cx += 505

    img.save(os.path.join(OUTPUT_DIR, "scheme1-01-home.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-01-home.png"))
    print("Generated Scheme 1 Home with real photographic assets")


def create_scheme1_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_pill(draw, [60, 40, 240, 130], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((280, 58), "影片详情  /  庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 36))

    draw_pill(draw, [2340, 40, 2820, 130], fill=(28, 38, 56, 200), outline=(60, 75, 105), width=2)
    draw.text((2380, 62), "SGSR1 超分就绪  ·  臻彩滤镜", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Real Qingyunian Video Preview Window!
    draw_rounded_rect(draw, [60, 160, 1300, 840], radius=28, fill=(15, 20, 30), outline=(50, 70, 100), width=3)
    qyn_preview = load_and_crop("qingyunian_still.jpg", (1220, 640), radius=22)
    paste_image(img, qyn_preview, (70, 170))

    # Play circle on video
    draw.ellipse([620, 430, 760, 570], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=3)
    draw_icon_play(draw, (695, 500), 40, ACCENT_CYAN)
    draw.text((545, 590), "轻触直接全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 28))

    draw_pill(draw, [90, 190, 310, 250], fill=(16, 185, 129, 230))
    draw.text((115, 205), "4K 60FPS 极速", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Action buttons: Play, Switch Source, Collect, Push
    draw_pill(draw, [60, 870, 540, 1010], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((130, 912), "▶ 立即全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 38))

    draw_pill(draw, [570, 870, 840, 1010], fill=(28, 38, 56, 220), outline=ACCENT_CYAN, width=2)
    draw.text((620, 918), "⚡ 智能换源", fill=ACCENT_CYAN, font=get_font(True, 32))

    draw_pill(draw, [870, 870, 1070, 1010], fill=(28, 38, 56, 220), outline=(70, 85, 115), width=2)
    draw.text((915, 918), "♥ 收藏", fill=TEXT_PRIMARY, font=get_font(True, 32))

    draw_pill(draw, [1100, 870, 1300, 1010], fill=(45, 35, 70, 220), outline=ACCENT_AMBER, width=2)
    draw.text((1130, 918), "📲 投屏/推送", fill=ACCENT_AMBER, font=get_font(True, 32))

    # Clean Metadata Card
    draw_rounded_rect(draw, [60, 1040, 1300, 1560], radius=24, fill=(24, 32, 46, 220), outline=(50, 65, 95), width=2)
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

    draw_pill(draw, [100, 1450, 340, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((130, 1468), "SGSR1 边缘锐化", fill=ACCENT_CYAN, font=get_font(True, 22))

    draw_pill(draw, [360, 1450, 600, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((390, 1468), "座舱环绕声场", fill=ACCENT_CYAN, font=get_font(True, 22))

    # RIGHT HALF: Sources & Episodes
    draw_rounded_rect(draw, [1360, 160, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((1400, 225), "播放源：", fill=TEXT_MUTED, font=get_font(True, 30))
    sources = [("专线·4K臻彩 (当前)", True), ("备线·蓝光HDR", False), ("极速·原画", False), ("官方解析", False)]
    cur_sx = 1530
    for s_name, s_act in sources:
        if s_act:
            draw_pill(draw, [cur_sx, 205, cur_sx + 290, 285], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
            draw.text((cur_sx + 30, 225), s_name, fill=TEXT_PRIMARY, font=get_font(True, 26))
        else:
            draw_pill(draw, [cur_sx, 205, cur_sx + 260, 285], fill=(30, 40, 58), outline=(55, 70, 95), width=1)
            draw.text((cur_sx + 35, 225), s_name, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_sx += 310

    draw.text((1400, 345), "剧集分段：", fill=TEXT_MUTED, font=get_font(True, 28))
    ranges = [("1-10", False), ("11-20 (当前)", True), ("21-30", False), ("31-36", False)]
    cur_rx = 1550
    for r_name, r_act in ranges:
        if r_act:
            draw_pill(draw, [cur_rx, 330, cur_rx + 220, 400], fill=ACCENT_CYAN)
            draw.text((cur_rx + 30, 348), r_name, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_pill(draw, [cur_rx, 330, cur_rx + 180, 400], fill=(30, 40, 58))
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
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=18, fill=(37, 99, 235), outline=ACCENT_CYAN, width=3)
                draw.text((bx0 + 40, by0 + 25), f"{ep_num}", fill=TEXT_PRIMARY, font=get_font(True, 42))
                draw.text((bx0 + 25, by0 + 85), "正在播放", fill=ACCENT_CYAN, font=get_font(True, 20))
            else:
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=18, fill=(28, 38, 54, 200), outline=(50, 65, 90), width=2)
                draw.text((bx0 + 55, by0 + 42), f"{ep_num}", fill=TEXT_SECONDARY, font=get_font(True, 36))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-02-detail.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-02-detail.png"))
    print("Generated Scheme 1 Detail with real video frame")


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
    draw_pill(draw, [60, 40, 240, 130], fill=(20, 28, 40, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 退出", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((280, 60), "沙丘 2 (Dune: Part Two) - 4K 60FPS IMAX 原画", fill=TEXT_PRIMARY, font=get_font(True, 36))
    
    draw_pill(draw, [1120, 55, 1380, 115], fill=(16, 185, 129, 200))
    draw.text((1145, 70), "IJK 硬解 · SGSR1", fill=TEXT_PRIMARY, font=get_font(True, 22))

    draw_pill(draw, [1920, 45, 2280, 125], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((1955, 65), "🎧 后台播放：开", fill=ACCENT_CYAN, font=get_font(True, 26))

    draw_pill(draw, [2320, 45, 2580, 125], fill=(30, 40, 58, 220), outline=ACCENT_AMBER, width=2)
    draw.text((2360, 65), "🔒 锁屏防触", fill=TEXT_SECONDARY, font=get_font(False, 26))

    draw_pill(draw, [2620, 45, 2820, 125], fill=(30, 40, 58, 220), outline=(60, 75, 100), width=2)
    draw.text((2675, 65), "15:43", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # Gesture Badges
    draw_rounded_rect(draw, [60, 500, 180, 900], radius=30, fill=(20, 30, 45, 130), outline=(50, 70, 100, 140), width=2)
    draw.text((85, 540), "☼\n\n左\n屏\n上\n下\n滑\n\n调\n亮\n度", fill=TEXT_SECONDARY, font=get_font(False, 24))

    draw_rounded_rect(draw, [2700, 500, 2820, 900], radius=30, fill=(20, 30, 45, 130), outline=(50, 70, 100, 140), width=2)
    draw.text((2725, 540), "🔊\n\n右\n屏\n上\n下\n滑\n\n调\n音\n量", fill=TEXT_SECONDARY, font=get_font(False, 24))

    draw_pill(draw, [1220, 500, 1660, 580], fill=(0, 0, 0, 190), outline=ACCENT_CYAN, width=2)
    draw.text((1260, 522), "⚡ 长按屏幕 2.0x 极速快进", fill=ACCENT_CYAN, font=get_font(True, 24))

    # Scrubber Bar
    draw.text((60, 1240), "01:24:15 / 02:46:30", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw_rounded_rect(draw, [400, 1250, 2820, 1270], radius=10, fill=(60, 75, 95))
    draw_rounded_rect(draw, [400, 1250, 1620, 1270], radius=10, fill=ACCENT_CYAN)
    draw.ellipse([1600, 1237, 1646, 1283], fill=(255, 255, 255), outline=ACCENT_CYAN, width=4)

    # Bottom Control Island (Left)
    draw_pill(draw, [60, 1360, 320, 1550], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((120, 1425), "⏸ 暂停", fill=TEXT_PRIMARY, font=get_font(True, 38))

    draw_pill(draw, [350, 1360, 560, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((385, 1430), "⏪ -15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [590, 1360, 800, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((625, 1430), "⏩ +15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [830, 1360, 1190, 1550], fill=(28, 48, 70, 240), outline=(0, 210, 255), width=3)
    draw.text((880, 1400), "🎨 画面预设", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((880, 1445), "臻彩高对比", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [1220, 1360, 1580, 1550], fill=(45, 36, 65, 240), outline=ACCENT_AMBER, width=3)
    draw.text((1260, 1400), "🔊 座舱音效", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((1260, 1445), "影院环绕声", fill=ACCENT_AMBER, font=get_font(True, 34))

    # Secondary Tools (Right)
    sec_buttons = [("倍速 1.25x", 1920), ("选集 (36)", 2160), ("比例 21:9", 2400), ("换源 (32ms)", 2640)]
    for btn_text, bx in sec_buttons:
        draw_pill(draw, [bx, 1380, bx + 215, 1530], fill=(26, 34, 48, 220), outline=(60, 75, 100), width=2)
        draw.text((bx + 28, 1435), btn_text, fill=TEXT_SECONDARY, font=get_font(True, 26))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-03-player.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-03-player.png"))
    print("Generated Scheme 1 Player with real 4K cinematic background")

def create_scheme1_04_search():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_pill(draw, [60, 40, 220, 180], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 88), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw_pill(draw, [250, 40, 2350, 180], fill=(24, 34, 52, 240), outline=ACCENT_CYAN, width=3)
    draw.text((320, 85), "🔍 庆余年|", fill=TEXT_PRIMARY, font=get_font(True, 42))

    draw_pill(draw, [1920, 55, 2310, 165], fill=(37, 99, 235))
    draw.text((1975, 88), "🎙 按住语音", fill=TEXT_PRIMARY, font=get_font(True, 32))

    draw_pill(draw, [2390, 40, 2600, 180], fill=(16, 185, 129))
    draw.text((2445, 88), "搜索", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_pill(draw, [2630, 40, 2820, 180], fill=(45, 55, 75))
    draw.text((2685, 88), "清空", fill=TEXT_SECONDARY, font=get_font(False, 30))

    # LEFT HALF: Restored Large Virtual Keyboard (36 Keys, High Spacing, Retained as user wanted!)
    draw_rounded_rect(draw, [60, 230, 1350, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((100, 270), "车载防颠簸大键盘 (支持简拼/全拼/首字母)", fill=TEXT_MUTED, font=get_font(True, 26))

    draw_pill(draw, [960, 255, 1310, 315], fill=(30, 50, 80), outline=ACCENT_CYAN, width=1)
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
                draw_rounded_rect(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(60, 30, 40), outline=(150, 50, 70), width=2)
                draw.text((cur_kx + 45, cur_ky + 32), key, fill=(255, 180, 190), font=get_font(True, 32))
            elif key == "空格":
                draw_rounded_rect(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(35, 50, 75), outline=ACCENT_CYAN, width=2)
                draw.text((cur_kx + 50, cur_ky + 32), key, fill=ACCENT_CYAN, font=get_font(True, 30))
            else:
                draw_rounded_rect(draw, [cur_kx, cur_ky, cur_kx + kw, cur_ky + kh], radius=18, fill=(30, 42, 60), outline=(60, 75, 105), width=2)
                draw.text((cur_kx + 38, cur_ky + 30), key, fill=TEXT_PRIMARY, font=get_font(True, 36))
            cur_kx += kw + 15
        cur_ky += 135

    pinyin_suffixes = ["庆", "余", "年", "第二季", "电影", "电视剧", "动漫"]
    cur_px = 100
    for ps in pinyin_suffixes:
        draw_pill(draw, [cur_px, 920, cur_px + 155, 1000], fill=(35, 55, 85), outline=(70, 95, 135), width=1)
        draw.text((cur_px + 30, 945), ps, fill=TEXT_PRIMARY, font=get_font(True, 24))
        cur_px += 175

    # RIGHT HALF: Recent Searches & Real-Time Results with Real Poster Thumbnails!
    draw_rounded_rect(draw, [1400, 230, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((1440, 270), "最近历史搜索：", fill=TEXT_MUTED, font=get_font(True, 28))
    hist_tags = ["庆余年2", "繁花", "周处除三害", "三体", "飞驰人生2"]
    cur_hx = 1660
    for ht in hist_tags:
        draw_pill(draw, [cur_hx, 255, cur_hx + 190, 325], fill=(30, 40, 58), outline=(60, 75, 100), width=1)
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
        draw_rounded_rect(draw, [1440, cur_ry, 2780, cur_ry + 195], radius=20, fill=(28, 38, 56, 220), outline=(60, 80, 115), width=2)
        # Real Poster Thumbnail
        thumb = load_and_crop(poster_fn, (130, 165), radius=14)
        paste_image(img, thumb, (1460, cur_ry + 15))

        draw.text((1620, cur_ry + 35), r_title, fill=TEXT_PRIMARY, font=get_font(True, 34))
        draw.text((1620, cur_ry + 95), r_desc, fill=TEXT_SECONDARY, font=get_font(False, 26))
        draw_pill(draw, [2500, cur_ry + 45, 2740, cur_ry + 150], fill=(16, 185, 129))
        draw.text((2530, cur_ry + 78), r_action, fill=TEXT_PRIMARY, font=get_font(True, 26))
        cur_ry += 220

    img.save(os.path.join(OUTPUT_DIR, "scheme1-04-search.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-04-search.png"))
    print("Generated Scheme 1 Search with real poster thumbnails")

def create_scheme1_05_settings():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((260, 60), "播放器设置中心  /  核心解码与音画调优", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # LEFT COLUMN: Category Sidebar
    draw_rounded_rect(draw, [60, 160, 680, 1560], radius=28, fill=(20, 28, 40, 240), outline=(50, 65, 90), width=2)
    settings_cats = [
        ("🎨 画面色彩与超分引擎", True),
        ("🔊 座舱声场与杜比音效", False),
        ("⚙ 播放解码与后台行为", False),
        ("👆 触控手势与长按倍速", False),
        ("↩ 双模应用内返回控件", False),
        ("📦 订阅源与多仓管理", False),
        ("ℹ 关于 TVBox Car.7-r2", False)
    ]
    cur_scy = 200
    for c_title, c_act in settings_cats:
        if c_act:
            draw_rounded_rect(draw, [80, cur_scy, 660, cur_scy + 130], radius=20, fill=(35, 65, 110), outline=ACCENT_CYAN, width=3)
            draw.text((115, cur_scy + 45), c_title, fill=TEXT_PRIMARY, font=get_font(True, 30))
        else:
            draw_rounded_rect(draw, [80, cur_scy, 660, cur_scy + 130], radius=20, fill=(26, 34, 48, 160), outline=(45, 58, 80), width=1)
            draw.text((115, cur_scy + 45), c_title, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_scy += 155

    # RIGHT COLUMN: Restored Detailed Settings Cards (minus fake D-gear)
    # CARD 1: Picture Enhancement Presets
    draw_rounded_rect(draw, [720, 160, 2820, 500], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 200), "SDR / HDR 画面调优预设", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 250), "针对 17.3 英寸 3K 屏幕特别调校的 GLSL 着色器矩阵，实时渲染不重启解码器", fill=TEXT_MUTED, font=get_font(False, 26))

    p_presets = ["原始自然", "臻彩高饱和 (推荐)", "动漫高对比", "影院胶片", "夜间护眼琥珀", "日间强光防反光"]
    cur_px = 760
    for idx, p_name in enumerate(p_presets):
        pw = 320
        if idx == 1:
            draw_pill(draw, [cur_px, 320, cur_px + pw, 440], fill=ACCENT_CYAN)
            draw.text((cur_px + 30, 360), p_name, fill=(10, 20, 35), font=get_font(True, 26))
        else:
            draw_pill(draw, [cur_px, 320, cur_px + pw, 440], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
            draw.text((cur_px + 35, 360), p_name, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_px += pw + 18

    # CARD 2: Snapdragon Super Resolution (SGSR1)
    draw_rounded_rect(draw, [720, 530, 2820, 750], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 570), "高通 SGSR1 硬件级边缘超分辨率算法", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 620), "通过单通道自适应边缘检测锐化算法，将 720P/1080P 片源智能放大至接近 3K 物理分辨率清晰度", fill=TEXT_MUTED, font=get_font(False, 26))
    draw_pill(draw, [2540, 590, 2760, 680], fill=SUCCESS_GREEN)
    draw.ellipse([2670, 595, 2750, 675], fill=(255, 255, 255))
    draw.text((2570, 618), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 3: Temporal Smoothing (时域平滑补帧)
    draw_rounded_rect(draw, [720, 780, 2820, 1000], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 820), "时域平滑防抖算法 (Temporal Smoothing)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 870), "针对低帧率动漫和老剧集，消除运动残影与画面抖动，保持车内观影流畅体验", fill=TEXT_MUTED, font=get_font(False, 26))
    draw_pill(draw, [2540, 840, 2760, 930], fill=SUCCESS_GREEN)
    draw.ellipse([2670, 845, 2750, 925], fill=(255, 255, 255))
    draw.text((2570, 868), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 4: Touch Gestures & Background Play
    draw_rounded_rect(draw, [720, 1030, 2820, 1550], radius=26, fill=(28, 40, 60, 240), outline=ACCENT_CYAN, width=2)
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
            draw_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=SUCCESS_GREEN)
            draw.ellipse([2690, cur_osy + 15, 2750, cur_osy + 75], fill=(255, 255, 255))
        else:
            draw_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=(60, 70, 90))
            draw.ellipse([2570, cur_osy + 15, 2630, cur_osy + 75], fill=(160, 170, 190))
        cur_osy += 105

    img.save(os.path.join(OUTPUT_DIR, "scheme1-05-settings.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-05-settings.png"))
    print("Generated Scheme 1 Settings")

def create_scheme1_06_live():
    img = Image.new("RGBA", (2880, 1620), BG_OBSIDIAN)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((260, 60), "电视直播  /  央视·卫视高清频道组", fill=TEXT_PRIMARY, font=get_font(True, 36))

    draw_pill(draw, [2360, 40, 2820, 130], fill=(26, 36, 52, 200), outline=(55, 75, 105), width=2)
    draw.text((2400, 62), "⚡ 极速解码 · 0秒切台", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Channel Groups & Channel List
    draw_rounded_rect(draw, [60, 160, 1100, 1560], radius=28, fill=(20, 28, 40, 240), outline=(50, 65, 90), width=2)
    
    c_groups = ["央视频道", "热门卫视", "高清电影", "体育赛事", "地方频道"]
    cur_gx = 90
    for idx, g_name in enumerate(c_groups):
        gw = 180
        if idx == 0:
            draw_pill(draw, [cur_gx, 190, cur_gx + gw, 260], fill=ACCENT_CYAN)
            draw.text((cur_gx + 30, 210), g_name, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_pill(draw, [cur_gx, 190, cur_gx + gw, 260], fill=(28, 38, 54))
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
            draw_rounded_rect(draw, [90, cur_cy, 1070, cur_cy + 155], radius=20, fill=(35, 65, 110), outline=ACCENT_CYAN, width=3)
            draw.text((120, cur_cy + 30), c_id, fill=ACCENT_CYAN, font=get_font(True, 40))
            draw.text((200, cur_cy + 30), c_name, fill=TEXT_PRIMARY, font=get_font(True, 34))
            draw.text((200, cur_cy + 90), f"▶ {c_epg}", fill=ACCENT_AMBER, font=get_font(False, 26))
            draw_pill(draw, [920, cur_cy + 45, 1040, cur_cy + 110], fill=(16, 185, 129))
            draw.text((945, cur_cy + 62), "播放中", fill=TEXT_PRIMARY, font=get_font(True, 20))
        else:
            draw_rounded_rect(draw, [90, cur_cy, 1070, cur_cy + 155], radius=20, fill=(26, 34, 48, 180), outline=(45, 58, 80), width=1)
            draw.text((120, cur_cy + 30), c_id, fill=TEXT_MUTED, font=get_font(True, 40))
            draw.text((200, cur_cy + 30), c_name, fill=TEXT_PRIMARY, font=get_font(True, 32))
            draw.text((200, cur_cy + 90), c_epg, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_cy += 175

    # RIGHT HALF: Live TV Screen Preview & Quick Actions with REAL CCTV-1 Still!
    draw_rounded_rect(draw, [1140, 160, 2820, 1560], radius=28, fill=(16, 22, 32), outline=(50, 68, 98), width=3)
    # Simulated Live Screen with real studio photo!
    cctv_im = load_and_crop("cctv1_still.jpg", (1640, 970), radius=22)
    paste_image(img, cctv_im, (1160, 180))

    draw.ellipse([1920, 620, 2040, 740], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=3)
    draw_icon_play(draw, (1985, 680), 36, ACCENT_CYAN)
    draw.text((1820, 770), "轻触唤起全屏沉浸直播", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # Station Tag
    draw_pill(draw, [1190, 210, 1420, 275], fill=(220, 38, 38, 220))
    draw.text((1220, 228), "● 正在直播", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Bottom Actions for Live TV
    draw_rounded_rect(draw, [1160, 1180, 2800, 1520], radius=24, fill=(24, 34, 50, 220), outline=(50, 65, 95), width=2)
    draw.text((1200, 1220), "当前频道：CCTV-1 综合频道 (1080P 50FPS 极速源)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((1200, 1280), "节目流预告：19:00 新闻联播  |  19:35 焦点访谈  |  20:00 黄金档剧场", fill=TEXT_SECONDARY, font=get_font(False, 28))

    draw_pill(draw, [1200, 1360, 1620, 1480], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((1275, 1395), "▶ 全屏沉浸直播", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_pill(draw, [1660, 1360, 1960, 1480], fill=(30, 42, 60), outline=ACCENT_CYAN, width=2)
    draw.text((1720, 1400), "⚡ 切换源 (3条备用)", fill=ACCENT_CYAN, font=get_font(True, 28))

    draw_pill(draw, [2000, 1360, 2300, 1480], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((2070, 1400), "♥ 收藏该频道", fill=TEXT_SECONDARY, font=get_font(True, 28))

    img.save(os.path.join(OUTPUT_DIR, "scheme1-06-live.png"))
    img.save(os.path.join(OUTPUT_DIR, "redesign-06-screenoff-audio.png"))
    print("Generated Scheme 1 Live TV with real CCTV studio still")


# ==============================================================================
# 方案二：【全景悬浮无界版 (Cinematic Infinity)】(多场景完整套图)
# ==============================================================================
def create_scheme2_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_CINEMATIC)
    draw = ImageDraw.Draw(img, "RGBA")

    # Ambient backdrop lighting
    draw.ellipse([400, 0, 2480, 800], fill=(30, 50, 80, 100))

    # Top Floating Capsule Navigation (Translucent Glass Bar x: 440 ~ 2440 px, y: 50 ~ 150 px)
    draw_pill(draw, [440, 50, 2440, 150], fill=(22, 28, 42, 200), outline=(50, 70, 100), width=2)
    draw.text((500, 75), "TVBOX PRO", fill=ACCENT_CYAN, font=get_font(True, 32))

    cinematic_nav = ["精选推荐", "电影大片", "热门剧集", "电视直播", "播放历史", "我的收藏", "应用设置"]
    cur_nx = 760
    for idx, n_name in enumerate(cinematic_nav):
        nw = 160 if len(n_name) <= 4 else 180
        if idx == 0:
            draw_pill(draw, [cur_nx, 62, cur_nx + nw, 138], fill=(37, 99, 235))
            draw.text((cur_nx + 25, 82), n_name, fill=TEXT_PRIMARY, font=get_font(True, 24))
        else:
            draw.text((cur_nx + 25, 82), n_name, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_nx += nw + 25

    draw_pill(draw, [2150, 65, 2380, 135], fill=(30, 42, 60), outline=ACCENT_CYAN, width=1)
    draw.text((2185, 82), "🔍 语音搜片", fill=TEXT_PRIMARY, font=get_font(True, 22))

    # Hero Full-Width Floating Spotlight Poster with REAL Fanxiang backdrop!
    draw_rounded_rect(draw, [100, 190, 2780, 820], radius=36, fill=(20, 26, 40, 240), outline=(55, 75, 110), width=2)
    fx_backdrop = load_and_crop("fanxiang_backdrop.jpg", (1500, 630), radius=30)
    paste_image(img, fx_backdrop, (100, 190))

    # Right Information on Hero Card
    draw_pill(draw, [1680, 240, 1940, 300], fill=(220, 38, 38))
    draw.text((1710, 255), "今日全网热搜 TOP 1", fill=TEXT_PRIMARY, font=get_font(True, 22))

    draw.text((1680, 330), "繁花 (30集全 · 沪语4K原声)", fill=TEXT_PRIMARY, font=get_font(True, 48))
    draw.text((1680, 410), "王家卫执导 · 胡歌 / 马伊琍 / 唐嫣 / 辛芷蕾", fill=ACCENT_AMBER, font=get_font(False, 28))
    draw.text((1680, 470), "评分：★ 8.8 (42.5万人评价)  ·  杜比全景声 7.1 音轨", fill=TEXT_SECONDARY, font=get_font(False, 26))

    synopsis_p = "九十年代上海黄河路商海浮沉，阿宝历经磨难成为宝总，与至真园老板娘李李展开惊心动魄的商战与情感纠葛..."
    draw.text((1680, 530), synopsis_p[:38], fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((1680, 570), synopsis_p[38:], fill=TEXT_MUTED, font=get_font(False, 24))

    draw_pill(draw, [1680, 650, 2100, 765], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((1750, 685), "▶ 立即播放", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_pill(draw, [2130, 650, 2450, 765], fill=(30, 40, 58), outline=(60, 75, 100), width=2)
    draw.text((2185, 690), "♥ 收藏剧集", fill=TEXT_SECONDARY, font=get_font(True, 28))

    # Bottom Rows: Borderless Ultra-Clean 3K Poster Rails with REAL Posters!
    draw.text((100, 860), "本周精选榜单 (点击卡片直接开播)", fill=TEXT_PRIMARY, font=get_font(True, 32))
    
    posters = [
        ("qingyunian_poster.jpg", "庆余年 第二季", "第18集/共36集", "4K 臻彩超清"),
        ("wandering_earth2_poster.jpg", "流浪地球 2", "173分钟完整版", "4K HDR 原画"),
        ("zhouchu_poster.jpg", "周处除三害", "动作犯罪高分", "杜比视界"),
        ("santi_poster.jpg", "三体 (电视剧版)", "全30集", "科幻臻彩超清"),
        ("kuangbiao_poster.jpg", "狂飙", "全39集扫黑大剧", "4K 60FPS"),
    ]
    cur_px = 100
    for fn, p_title, p_sub, p_tag in posters:
        draw_rounded_rect(draw, [cur_px, 920, cur_px + 510, 1560], radius=28, fill=(20, 26, 38), outline=(60, 75, 105), width=2)
        p_im = load_and_crop(fn, (470, 460), radius=22)
        paste_image(img, p_im, (cur_px + 20, 940))

        draw_pill(draw, [cur_px + 40, 960, cur_px + 200, 1020], fill=(234, 88, 12, 220))
        draw.text((cur_px + 60, 975), p_tag, fill=TEXT_PRIMARY, font=get_font(True, 22))

        draw.text((cur_px + 30, 1430), p_title, fill=TEXT_PRIMARY, font=get_font(True, 32))
        draw.text((cur_px + 30, 1480), p_sub, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_px += 535

    img.save(os.path.join(OUTPUT_DIR, "scheme2-01-home.png"))
    img.save(os.path.join(OUTPUT_DIR, "scheme2-home.png"))
    print("Generated Scheme 2 Home with real photographic assets")


def create_scheme2_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_CINEMATIC)
    draw = ImageDraw.Draw(img, "RGBA")

    # Fullscreen cinematic backdrop
    qyn_bg = load_and_crop("qingyunian_still.jpg", (2880, 1620))
    paste_image(img, qyn_bg, (0, 0))

    # Dark frosted glass veil
    veil = Image.new("RGBA", (2880, 1620), (10, 14, 22, 210))
    paste_image(img, veil, (0, 0))

    # Top floating navigation
    draw_pill(draw, [60, 50, 240, 140], fill=(24, 30, 44, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 72), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw_pill(draw, [280, 50, 1200, 140], fill=(20, 26, 38, 200), outline=(50, 65, 90), width=1)
    draw.text((320, 72), "影视详情  ·  庆余年 第二季 (Joy of Life II)", fill=TEXT_PRIMARY, font=get_font(True, 32))

    # Detail Floating Poster & Information
    poster_im = load_and_crop("qingyunian_poster.jpg", (460, 680), radius=28)
    paste_image(img, poster_im, (120, 220))

    # Info card on right
    draw_rounded_rect(draw, [620, 220, 2760, 900], radius=32, fill=(18, 24, 36, 230), outline=(48, 65, 95), width=2)
    draw.text((680, 260), "庆余年 第二季 (2024)", fill=TEXT_PRIMARY, font=get_font(True, 52))
    draw.text((680, 340), "评分：★ 8.8 分 (豆瓣超 38 万人评价)  ·  古装 / 权谋 / 历史 / 喜剧", fill=ACCENT_AMBER, font=get_font(True, 30))
    draw.text((680, 400), "导演：孙皓  |  编剧：王倦  |  领衔主演：张若昀 / 李沁 / 陈道明 / 吴刚 / 田雨", fill=TEXT_SECONDARY, font=get_font(False, 26))

    syn = (
        "剧情梗概：范闲率领使团回归南庆，面对二皇子与长公主的步步紧逼，以及抱月楼命案与内库亏空危机，"
        "展开了一场更为凶险狡黠的朝堂权谋周旋。各方势力暗流涌动，悬空庙刺杀与科举舞弊大案接连爆发..."
    )
    draw.text((680, 470), syn[:55], fill=TEXT_MUTED, font=get_font(False, 26))
    draw.text((680, 515), syn[55:], fill=TEXT_MUTED, font=get_font(False, 26))

    # Big Glass Actions
    draw_pill(draw, [680, 600, 1120, 730], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((750, 642), "▶ 立即全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 36))

    draw_pill(draw, [1160, 600, 1480, 730], fill=(30, 42, 60), outline=ACCENT_CYAN, width=2)
    draw.text((1220, 646), "⚡ 智能换源", fill=ACCENT_CYAN, font=get_font(True, 30))

    draw_pill(draw, [1520, 600, 1800, 730], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((1580, 646), "♥ 收藏剧集", fill=TEXT_SECONDARY, font=get_font(True, 28))

    draw_pill(draw, [1840, 600, 2160, 730], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((1900, 646), "📲 投屏/推送", fill=TEXT_SECONDARY, font=get_font(True, 28))

    # Bottom Fluid Episode Rails (Horizontal Scrolling Pills)
    draw.text((120, 940), "剧集快速跳选 (全 36 集)：", fill=TEXT_PRIMARY, font=get_font(True, 32))
    
    cur_epx = 120
    for ep in range(1, 16):
        ew = 150
        eh = 120
        if ep == 18:
            draw_rounded_rect(draw, [cur_epx, 1010, cur_epx + ew, 1010 + eh], radius=20, fill=(37, 99, 235), outline=ACCENT_CYAN, width=3)
            draw.text((cur_epx + 45, 1035), f"{ep}", fill=TEXT_PRIMARY, font=get_font(True, 40))
            draw.text((cur_epx + 25, 1090), "当前播放", fill=ACCENT_CYAN, font=get_font(True, 18))
        else:
            draw_rounded_rect(draw, [cur_epx, 1010, cur_epx + ew, 1010 + eh], radius=20, fill=(24, 32, 48, 220), outline=(50, 65, 90), width=2)
            draw.text((cur_epx + 50, 1045), f"{ep}", fill=TEXT_SECONDARY, font=get_font(True, 36))
        cur_epx += ew + 20

    img.save(os.path.join(OUTPUT_DIR, "scheme2-02-detail.png"))
    print("Generated Scheme 2 Detail")


def create_scheme2_03_player():
    img = Image.new("RGBA", (2880, 1620), BG_CINEMATIC)
    draw = ImageDraw.Draw(img, "RGBA")

    # Real space station 4K background!
    real_space = load_and_crop("space_station_still.jpg", (2880, 1620))
    paste_image(img, real_space, (0, 0))

    # Minimalist Floating Frosted Scrubber at bottom (x: 200 ~ 2680 px, y: 1380 ~ 1560 px)
    draw_rounded_rect(draw, [200, 1380, 2680, 1560], radius=32, fill=(16, 22, 34, 220), outline=(55, 75, 105), width=2)
    
    # Scrubber line inside floating bar
    draw.text((250, 1410), "01:15:32", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw.text((2450, 1410), "02:53:14", fill=TEXT_MUTED, font=get_font(True, 30))
    draw_rounded_rect(draw, [420, 1422, 2400, 1438], radius=8, fill=(50, 65, 85))
    draw_rounded_rect(draw, [420, 1422, 1450, 1438], radius=8, fill=ACCENT_CYAN)
    draw.ellipse([1435, 1412, 1465, 1448], fill=(255, 255, 255), outline=ACCENT_CYAN, width=3)

    # Floating Action Bar inside
    actions = [
        ("⏸", 250, 1470, 70), ("⏪ 15s", 360, 1470, 120), ("⏩ 15s", 510, 1470, 120),
        ("倍速 1.5x", 660, 1470, 140), ("臻彩画质", 830, 1470, 150), ("杜比全景声", 1010, 1470, 160),
        ("比例 21:9", 1200, 1470, 150), ("选集 (18/36)", 1380, 1470, 170)
    ]
    for a_text, ax, ay, aw in actions:
        draw_pill(draw, [ax, ay, ax + aw, ay + 65], fill=(30, 42, 60), outline=(60, 75, 100), width=1)
        draw.text((ax + 20, ay + 15), a_text, fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Top minimal bar
    draw_pill(draw, [60, 50, 200, 130], fill=(20, 26, 38, 200), outline=ACCENT_CYAN, width=1)
    draw.text((95, 70), "←", fill=TEXT_PRIMARY, font=get_font(True, 36))
    draw.text((230, 72), "流浪地球 2 (The Wandering Earth II) · 4K 60FPS 臻彩超清", fill=TEXT_PRIMARY, font=get_font(True, 32))

    img.save(os.path.join(OUTPUT_DIR, "scheme2-03-player.png"))
    print("Generated Scheme 2 Player")


# ==============================================================================
# 方案三：【双区专业影音工作台版 (Studio Cockpit)】(多场景完整套图)
# ==============================================================================
def create_scheme3_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_STUDIO)
    draw = ImageDraw.Draw(img, "RGBA")

    # LEFT WING: Control & Quick Station (40% width: x: 0 ~ 1150 px)
    draw.rectangle([0, 0, 1150, 1620], fill=(18, 24, 34, 240))
    draw.line([1150, 0, 1150, 1620], fill=(45, 60, 85), width=3)

    # Left Wing Header
    draw.text((60, 45), "TVBOX STUDIO", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((60, 95), "17.3-INCH 3K HIGH-FIDELITY COCKPIT", fill=TEXT_MUTED, font=get_font(True, 20))

    # Fast Media Hub with Real Still!
    draw_rounded_rect(draw, [60, 160, 1090, 520], radius=24, fill=(25, 34, 48, 220), outline=(55, 75, 105), width=2)
    qyn_micro = load_and_crop("qingyunian_still.jpg", (300, 320), radius=16)
    paste_image(img, qyn_micro, (80, 180))

    draw_pill(draw, [410, 180, 580, 230], fill=(37, 99, 235))
    draw.text((435, 195), "上次观看", fill=TEXT_PRIMARY, font=get_font(True, 20))
    draw.text((410, 250), "庆余年 第二季 - 18集", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((410, 305), "已播 72% · 4K 臻彩 · 延迟 32ms", fill=ACCENT_CYAN, font=get_font(False, 24))

    draw_pill(draw, [410, 370, 720, 470], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((460, 400), "▶ 继续", fill=TEXT_PRIMARY, font=get_font(True, 28))
    draw_pill(draw, [750, 370, 1060, 470], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((790, 400), "⚡ 智能测速", fill=TEXT_SECONDARY, font=get_font(True, 26))

    # Multi-Source Latency Monitor
    draw_rounded_rect(draw, [60, 550, 1090, 980], radius=24, fill=(25, 34, 48, 220), outline=(55, 75, 105), width=2)
    draw.text((100, 585), "线路智能测速中心 (当前已选最快源)：", fill=TEXT_PRIMARY, font=get_font(True, 28))
    
    source_stats = [
        ("专线·4K超清 (推荐线路)", "32 ms", "极低延迟 · 0丢包", True),
        ("备线·蓝光HDR", "85 ms", "良好 · 无缓冲", False),
        ("专线·原画", "120 ms", "正常", False),
        ("备线·官方解析", "240 ms", "偏高", False),
    ]
    cur_sy = 640
    for s_name, s_ping, s_desc, s_sel in source_stats:
        if s_sel:
            draw_rounded_rect(draw, [100, cur_sy, 1050, cur_sy + 70], radius=14, fill=(35, 65, 110), outline=ACCENT_CYAN, width=2)
            draw.text((130, cur_sy + 18), s_name, fill=TEXT_PRIMARY, font=get_font(True, 24))
            draw.text((620, cur_sy + 18), s_ping, fill=SUCCESS_GREEN, font=get_font(True, 24))
            draw.text((780, cur_sy + 18), s_desc, fill=ACCENT_CYAN, font=get_font(False, 20))
        else:
            draw_rounded_rect(draw, [100, cur_sy, 1050, cur_sy + 70], radius=14, fill=(28, 38, 54), outline=(45, 58, 80), width=1)
            draw.text((130, cur_sy + 18), s_name, fill=TEXT_SECONDARY, font=get_font(False, 24))
            draw.text((620, cur_sy + 18), s_ping, fill=TEXT_MUTED, font=get_font(False, 24))
            draw.text((780, cur_sy + 18), s_desc, fill=TEXT_MUTED, font=get_font(False, 20))
        cur_sy += 80

    # Quick Navigation Docks on Left Wing Bottom
    left_actions = [
        ("★ 精选流媒体", 60, 1020), ("📺 电视直播", 570, 1020),
        ("⏱ 历史记录", 60, 1160), ("♥ 影视收藏", 570, 1160),
        ("📲 多端推送", 60, 1300), ("⚙ 设置中心", 570, 1300),
    ]
    for a_title, ax, ay in left_actions:
        draw_rounded_rect(draw, [ax, ay, ax + 490, ay + 115], radius=18, fill=(30, 42, 60), outline=(50, 65, 90), width=2)
        draw.text((ax + 50, ay + 38), a_title, fill=TEXT_PRIMARY, font=get_font(True, 30))

    # RIGHT WING: Content Showcase (60% width: x: 1200 ~ 2820 px)
    draw_pill(draw, [1200, 40, 2500, 130], fill=(24, 34, 52, 220), outline=ACCENT_CYAN, width=2)
    draw.text((1250, 62), "🔍 语音搜片 / 拼音首字母检索 (例如: QYN2)", fill=TEXT_MUTED, font=get_font(False, 28))
    draw_pill(draw, [2260, 50, 2480, 120], fill=(37, 99, 235))
    draw.text((2295, 62), "🎙 语音", fill=TEXT_PRIMARY, font=get_font(True, 26))

    draw_pill(draw, [2540, 40, 2820, 130], fill=(24, 34, 52, 220), outline=(50, 65, 90), width=1)
    draw.text((2600, 62), "15:42 5G", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # Right Hero Showcase with REAL Zhouchu Poster!
    draw_rounded_rect(draw, [1200, 160, 2820, 800], radius=32, fill=(28, 38, 56, 240), outline=(55, 75, 110), width=2)
    zc_poster = load_and_crop("zhouchu_poster.jpg", (850, 600), radius=24)
    paste_image(img, zc_poster, (1220, 180))
    
    draw.text((2120, 220), "周处除三害 (2024)", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((2120, 290), "黄精甫导演 · 阮经天 / 袁富华 / 陈以文", fill=ACCENT_AMBER, font=get_font(False, 26))
    draw.text((2120, 350), "评分 ★ 8.5  ·  动作 / 犯罪 / 剧情 · 4K HDR", fill=TEXT_SECONDARY, font=get_font(False, 24))
    draw_pill(draw, [2120, 430, 2520, 540], fill=(16, 185, 129))
    draw.text((2190, 465), "▶ 立即观看", fill=TEXT_PRIMARY, font=get_font(True, 32))

    # Right Bottom Showcase with REAL Posters!
    draw.text((1200, 840), "热门热播流 (4K 臻彩超清专区)", fill=TEXT_PRIMARY, font=get_font(True, 32))
    r_cards = [
        ("fanxiang_poster.jpg", "繁花", "沪语4K原画"),
        ("wandering_earth2_poster.jpg", "流浪地球2", "科幻硬核"),
        ("kuangbiao_poster.jpg", "狂飙", "扫黑刑侦"),
        ("dune2_poster.jpg", "沙丘2", "宇宙巨制"),
    ]
    cur_rx = 1200
    for r_fn, r_title, r_desc in r_cards:
        draw_rounded_rect(draw, [cur_rx, 900, cur_rx + 380, 1560], radius=24, fill=(24, 32, 46), outline=(55, 75, 105), width=2)
        r_im = load_and_crop(r_fn, (340, 450), radius=18)
        paste_image(img, r_im, (cur_rx + 20, 920))

        draw.text((cur_rx + 30, 1410), r_title, fill=TEXT_PRIMARY, font=get_font(True, 30))
        draw.text((cur_rx + 30, 1465), r_desc, fill=TEXT_SECONDARY, font=get_font(False, 22))
        cur_rx += 410

    img.save(os.path.join(OUTPUT_DIR, "scheme3-01-home.png"))
    img.save(os.path.join(OUTPUT_DIR, "scheme3-home.png"))
    print("Generated Scheme 3 Home with real photographic assets")

def create_scheme3_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_STUDIO)
    draw = ImageDraw.Draw(img, "RGBA")

    # LEFT WING (40% width: x: 0 ~ 1150 px): Pro Calibration Station
    draw.rectangle([0, 0, 1150, 1620], fill=(18, 24, 34, 240))
    draw.line([1150, 0, 1150, 1620], fill=(45, 60, 85), width=3)

    draw.text((60, 45), "STUDIO DETAIL", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((60, 95), "PRO AUDIO/VIDEO MONITOR & ROUTING", fill=TEXT_MUTED, font=get_font(True, 20))

    # 16:9 Still
    still_im = load_and_crop("qingyunian_still.jpg", (1030, 520), radius=20)
    paste_image(img, still_im, (60, 150))

    # Pro Enhancements Tuning
    draw_rounded_rect(draw, [60, 700, 1090, 1100], radius=24, fill=(24, 32, 46), outline=(50, 68, 95), width=2)
    draw.text((90, 730), "专业渲染着色器配置 (实时调节)：", fill=TEXT_PRIMARY, font=get_font(True, 28))
    
    toggles = [
        ("高通 SGSR1 硬件自适应锐化超分辨率", "已开启 (单通道自适应锐化)", True),
        ("时域平滑算法 (Temporal Smoothing)", "已开启 (消除插帧残影)", True),
        ("SDR-to-HDR 动态色调映射 (臻彩滤镜)", "已激活 (扩展色彩动态范围)", True),
    ]
    cur_ty = 790
    for t_name, t_sub, t_val in toggles:
        draw.text((90, cur_ty), t_name, fill=TEXT_PRIMARY, font=get_font(True, 24))
        draw.text((90, cur_ty + 35), t_sub, fill=ACCENT_CYAN, font=get_font(False, 20))
        draw_pill(draw, [920, cur_ty + 10, 1050, cur_ty + 60], fill=SUCCESS_GREEN)
        draw.text((945, cur_ty + 20), "ON", fill=TEXT_PRIMARY, font=get_font(True, 22))
        cur_ty += 95

    # Multi-Source Latency Real-Time Check
    draw_rounded_rect(draw, [60, 1130, 1090, 1550], radius=24, fill=(24, 32, 46), outline=(50, 68, 95), width=2)
    draw.text((90, 1160), "各线路延迟测速看板 (点击即切)：", fill=TEXT_PRIMARY, font=get_font(True, 28))

    pings = [
        ("专线·4K超清 (推荐线路)", "32 ms", "极速 · 0丢包", True),
        ("备用线路一·蓝光HDR", "85 ms", "良好", False),
        ("备用线路二·原画直出", "140 ms", "正常", False),
    ]
    cur_py = 1220
    for p_name, p_val, p_desc, p_act in pings:
        bg = (35, 65, 110) if p_act else (28, 38, 54)
        draw_rounded_rect(draw, [90, cur_py, 1050, cur_py + 80], radius=16, fill=bg, outline=ACCENT_CYAN if p_act else (50, 65, 90), width=2)
        draw.text((120, cur_py + 25), p_name, fill=TEXT_PRIMARY, font=get_font(True, 24))
        draw.text((680, cur_py + 25), p_val, fill=SUCCESS_GREEN if p_act else TEXT_MUTED, font=get_font(True, 26))
        draw.text((820, cur_py + 28), p_desc, fill=ACCENT_CYAN if p_act else TEXT_MUTED, font=get_font(False, 20))
        cur_py += 95

    # RIGHT WING (60% width: x: 1200 ~ 2820 px): Full Episodes Matrix & Cast
    draw.text((1200, 60), "庆余年 第二季 (Joy of Life II) · 全 36 集全", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((1200, 125), "评分 ★ 8.8  ·  张若昀 / 李沁 / 陈道明 / 吴刚  ·  IJK 硬件加速", fill=ACCENT_AMBER, font=get_font(False, 26))

    # Action Pills
    draw_pill(draw, [2280, 50, 2520, 140], fill=(37, 99, 235))
    draw.text((2320, 75), "▶ 立即开播", fill=TEXT_PRIMARY, font=get_font(True, 30))
    draw_pill(draw, [2550, 50, 2780, 140], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((2600, 78), "♥ 收藏剧集", fill=TEXT_SECONDARY, font=get_font(True, 28))

    # Episode Matrix (Large touch squares)
    ep_x0, ep_y0 = 1200, 200
    ep_w, ep_h = 175, 145
    sp_x, sp_y = 25, 25
    for r in range(7):
        for c in range(8):
            num = r * 8 + c + 1
            if num > 36:
                break
            bx0 = ep_x0 + c * (ep_w + sp_x)
            by0 = ep_y0 + r * (ep_h + sp_y)
            bx1 = bx0 + ep_w
            by1 = by0 + ep_h
            if num == 18:
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=20, fill=(37, 99, 235), outline=ACCENT_CYAN, width=3)
                draw.text((bx0 + 55, by0 + 30), f"{num}", fill=TEXT_PRIMARY, font=get_font(True, 44))
                draw.text((bx0 + 35, by0 + 95), "正在播放", fill=ACCENT_CYAN, font=get_font(True, 20))
            else:
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=20, fill=(24, 32, 46), outline=(50, 65, 90), width=2)
                draw.text((bx0 + 60, by0 + 45), f"{num}", fill=TEXT_SECONDARY, font=get_font(True, 38))

    img.save(os.path.join(OUTPUT_DIR, "scheme3-02-detail.png"))
    print("Generated Scheme 3 Detail")


# ==============================================================================
# 生成方案一的 6 张权威 BEFORE vs AFTER 对比图
# ==============================================================================

def generate_comparison_card(before_path, after_path, out_path, title_text, problem_desc, solution_desc):
    comp_w, comp_h = 2880, 1800
    comp = Image.new("RGBA", (comp_w, comp_h), (12, 16, 24))
    draw = ImageDraw.Draw(comp, "RGBA")

    draw.text((60, 40), title_text, fill=TEXT_PRIMARY, font=get_font(True, 46))
    draw_pill(draw, [2340, 40, 2820, 120], fill=(37, 99, 235))
    draw.text((2380, 62), "17.3寸 3K 视频播放重构", fill=TEXT_PRIMARY, font=get_font(True, 26))

    img_before = Image.open(before_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)
    img_after = Image.open(after_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)

    # Left: Before
    draw_rounded_rect(draw, [55, 175, 1445, 965], radius=20, fill=(35, 25, 25), outline=(220, 38, 38), width=3)
    comp.paste(img_before, (60, 180))
    draw_pill(draw, [80, 200, 420, 270], fill=(220, 38, 38, 230))
    draw.text((105, 218), "原版问题状态 (BEFORE)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Right: After
    draw_rounded_rect(draw, [1475, 175, 2825, 965], radius=20, fill=(20, 35, 30), outline=(16, 185, 129), width=3)
    comp.paste(img_after, (1480, 180))
    draw_pill(draw, [1500, 200, 1860, 270], fill=(16, 185, 129, 230))
    draw.text((1525, 218), "整改后建议稿 (AFTER)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Bottom Diagnosis Box
    draw_rounded_rect(draw, [55, 990, 1445, 1740], radius=24, fill=(25, 18, 22, 240), outline=(180, 40, 40), width=2)
    draw.text((95, 1030), "🚨 原版交互与排版缺陷诊断", fill=(248, 113, 113), font=get_font(True, 34))
    cur_py = 1100
    for line in problem_desc:
        draw.text((95, cur_py), line, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_py += 55

    draw_rounded_rect(draw, [1475, 990, 2825, 1740], radius=24, fill=(18, 30, 28, 240), outline=(16, 185, 129), width=2)
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

    print("\n=== Generating Scheme 2 (Cinematic Infinity / Apple TV & Infuse Style) ===")
    create_scheme2_01_home()
    create_scheme2_02_detail()
    create_scheme2_03_player()

    print("\n=== Generating Scheme 3 (Studio Cockpit / Pro Workstation Style) ===")
    create_scheme3_01_home()
    create_scheme3_02_detail()
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
        out_path=os.path.join(COMPARE_DIR, "compare-06-audio-mode.png"),
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
