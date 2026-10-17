"""
generate_ux_redesign_mockups.py
Generates 6 high-fidelity 3K (2880x1620) redesigned in-car UI mockups and
6 Before vs After side-by-side comparison graphics.
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = "car/evidence/redesign"
COMPARE_DIR = "car/reports/ux_redesign"
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

# Color Palette (Modern In-Car Glassmorphism & High-Contrast Night/Day Palette)
BG_COLOR = (13, 17, 23)           # Deep space dark
SURFACE_CARD = (26, 32, 44, 220)   # Glass card
SURFACE_BORDER = (55, 65, 81, 255) # Border stroke
ACCENT_CYAN = (0, 210, 255)       # Car tech cyan
ACCENT_BLUE = (37, 99, 235)       # Primary button
ACCENT_AMBER = (255, 179, 71)     # Warm ambient gold
TEXT_PRIMARY = (255, 255, 255)    # High contrast text
TEXT_SECONDARY = (156, 163, 175)  # Muted secondary text
TEXT_MUTED = (107, 114, 128)      # Meta text
SUCCESS_GREEN = (16, 185, 129)    # Status active green

def draw_rounded_rect(draw, box, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

def draw_pill(draw, box, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    radius = (y1 - y0) // 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

# -------------------------------------------------------------
# MOCKUP 1: HOME & DASHBOARD (Driver-Oriented Dock & Hero Cards)
# -------------------------------------------------------------
def create_mockup_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Background subtle ambient glow
    draw.ellipse([800, -200, 2200, 600], fill=(20, 35, 60, 120))

    # 1. Driver-Side Vertical Dock (x: 0 ~ 240 px)
    draw.rectangle([0, 0, 240, 1620], fill=(18, 24, 34, 240))
    draw.line([240, 0, 240, 1620], fill=(45, 55, 72, 255), width=2)
    
    # Dock Brand
    draw.text((45, 45), "TVBOX", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((48, 95), "CAR 3K", fill=TEXT_MUTED, font=get_font(True, 24))

    # Dock Navigation items (x: 20 ~ 220 px)
    nav_items = [
        ("★ 精选推荐", True),
        ("▦ 分类专题", False),
        ("📺 电视直播", False),
        ("⏱ 播放历史", False),
        ("♥ 我的收藏", False),
        ("⚙ 系统设置", False),
    ]
    cur_y = 200
    for title, active in nav_items:
        if active:
            draw_rounded_rect(draw, [20, cur_y, 220, cur_y + 110], radius=20, fill=(30, 58, 100, 240), outline=ACCENT_CYAN, width=3)
            draw.rectangle([20, cur_y + 30, 28, cur_y + 80], fill=ACCENT_CYAN)
            draw.text((50, cur_y + 35), title, fill=TEXT_PRIMARY, font=get_font(True, 30))
        else:
            draw_rounded_rect(draw, [20, cur_y, 220, cur_y + 110], radius=20, fill=(24, 30, 42, 160))
            draw.text((50, cur_y + 35), title, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_y += 135

    # In-Car Driver Profile info at bottom dock
    draw_rounded_rect(draw, [20, 1460, 220, 1570], radius=20, fill=(28, 36, 50, 220))
    draw.text((55, 1485), "驾驶座就绪", fill=SUCCESS_GREEN, font=get_font(True, 24))
    draw.text((55, 1520), "黄金触控区", fill=TEXT_MUTED, font=get_font(False, 20))

    # 2. Top In-Car Status & Quick Voice Search Bar (x: 280 ~ 2800 px)
    # Weather & Drive state capsule
    draw_pill(draw, [280, 40, 780, 130], fill=(26, 34, 48, 200), outline=(50, 60, 80), width=2)
    draw.text((320, 62), "🚗 驻车安全档位  |  晴 24°C", fill=TEXT_SECONDARY, font=get_font(False, 28))

    # Big Voice Search Input Bar (x: 820 ~ 2300 px) - Driver & Passenger accessible
    draw_pill(draw, [820, 40, 2300, 130], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((870, 60), "🔍 语音搜片 / 首字母搜剧 (例如: QYN2 庆余年)", fill=TEXT_MUTED, font=get_font(False, 28))
    # Voice Button Pill inside Search Bar
    draw_pill(draw, [2020, 50, 2280, 120], fill=(37, 99, 235), outline=None)
    draw.text((2055, 62), "🎙 按住语音", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # Clock & Signal Pill
    draw_pill(draw, [2340, 40, 2800, 130], fill=(26, 34, 48, 200), outline=(50, 60, 80), width=2)
    draw.text((2400, 60), "15:42  5G 📶 100%", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # 3. Main Stage: Left Hero Resume Card (Driver Zone x: 280 ~ 1380 px, y: 170 ~ 680 px)
    draw_rounded_rect(draw, [280, 170, 1380, 680], radius=32, fill=(24, 32, 48, 240), outline=(55, 75, 110), width=3)
    # Tag
    draw_pill(draw, [320, 210, 540, 265], fill=(37, 99, 235, 200))
    draw.text((345, 222), "上次未看完", fill=TEXT_PRIMARY, font=get_font(True, 24))
    # Title & Progress
    draw.text((320, 290), "庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 46))
    draw.text((320, 360), "第 18 集 · 4K 臻彩超清 (已播至 72%)", fill=ACCENT_CYAN, font=get_font(False, 30))
    # Progress bar
    draw_rounded_rect(draw, [320, 430, 1340, 446], radius=8, fill=(45, 55, 72))
    draw_rounded_rect(draw, [320, 430, 1050, 446], radius=8, fill=ACCENT_CYAN)
    # Big Driver-Friendly Resume Play Button (x: 320 ~ 780, y: 490 ~ 620) -> Height 130px, Width 460px
    draw_pill(draw, [320, 490, 780, 620], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((400, 528), "▶ 继续播放", fill=TEXT_PRIMARY, font=get_font(True, 38))
    # Audio-only mode quick trigger
    draw_pill(draw, [810, 490, 1200, 620], fill=(45, 55, 75), outline=(75, 85, 105), width=2)
    draw.text((850, 532), "🔊 纯音频听剧", fill=TEXT_SECONDARY, font=get_font(True, 32))

    # 4. Right Stage: Today Hot Feature Card (Passenger / Wide View, x: 1420 ~ 2800 px, y: 170 ~ 680 px)
    draw_rounded_rect(draw, [1420, 170, 2800, 680], radius=32, fill=(28, 38, 56, 240), outline=(60, 80, 115), width=2)
    draw_pill(draw, [1460, 210, 1720, 265], fill=(220, 38, 38, 200))
    draw.text((1485, 222), "今日热榜 TOP 1", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((1460, 290), "繁花 (Blossoms Shanghai)", fill=TEXT_PRIMARY, font=get_font(True, 46))
    draw.text((1460, 360), "王家卫导演作品 · 沪语4K原画 · 杜比全景声", fill=ACCENT_AMBER, font=get_font(False, 30))
    draw.text((1460, 430), "九十年代上海黄河路商海浮沉，阿宝与李李的商战传奇...", fill=TEXT_SECONDARY, font=get_font(False, 26))
    # Details & Play button
    draw_pill(draw, [1460, 500, 1860, 620], fill=(16, 185, 129), outline=None)
    draw.text((1530, 535), "▶ 立即观看", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # 5. Bottom Carousel Cards: Top Tabs & Large Touch Posters (y: 720 ~ 1580 px)
    # High-Comfort Horizontal Category Tabs (x: 280 ~ 2800 px, y: 720 ~ 810 px, height 90px)
    tabs = ["🔥 热播推荐", "🎬 4K臻彩大片", "📺 热门电视剧", "✨ 动漫追番", "🎞 纪录片专区", "📻 经典相声曲艺"]
    cur_tx = 280
    for idx, tab_name in enumerate(tabs):
        if idx == 0:
            draw_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=ACCENT_CYAN, outline=None)
            draw.text((cur_tx + 35, 742), tab_name, fill=(10, 20, 35), font=get_font(True, 28))
        else:
            draw_pill(draw, [cur_tx, 720, cur_tx + 240, 810], fill=(28, 36, 50, 180), outline=(50, 60, 80), width=2)
            draw.text((cur_tx + 35, 742), tab_name, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_tx += 265

    # 5 Big Poster Cards (Width 475px, Height 680px, Spacing 30px)
    card_data = [
        ("流浪地球2", "评分 9.1", "科幻/硬核/4K", (40, 60, 95)),
        ("三体 (电视剧版)", "评分 8.8", "剧情/悬疑/臻彩", (50, 45, 80)),
        ("周处除三害", "评分 8.5", "动作/犯罪/HDR", (70, 50, 40)),
        ("狂飙", "评分 9.0", "反腐/扫黑/原画", (35, 70, 60)),
        ("大理寺少卿游", "评分 8.2", "古装/探案/超分", (60, 60, 50)),
    ]
    cur_cx = 280
    for title, score, tag, color in card_data:
        # Card Background
        draw_rounded_rect(draw, [cur_cx, 840, cur_cx + 475, 1560], radius=24, fill=color, outline=(65, 85, 120), width=2)
        # Poster Top Area simulation
        draw.rectangle([cur_cx + 20, 860, cur_cx + 455, 1360], fill=(color[0] + 20, color[1] + 20, color[2] + 20))
        # Simulated poster art lines
        draw.text((cur_cx + 60, 1080), title, fill=TEXT_PRIMARY, font=get_font(True, 40))
        # Score badge
        draw_pill(draw, [cur_cx + 40, 880, cur_cx + 200, 940], fill=(234, 88, 12, 220))
        draw.text((cur_cx + 60, 895), score, fill=TEXT_PRIMARY, font=get_font(True, 24))
        # Card bottom meta
        draw.text((cur_cx + 30, 1390), title, fill=TEXT_PRIMARY, font=get_font(True, 32))
        draw.text((cur_cx + 30, 1445), tag, fill=TEXT_SECONDARY, font=get_font(False, 24))
        # Touch button inside card
        draw_pill(draw, [cur_cx + 30, 1490, cur_cx + 445, 1545], fill=(37, 99, 235, 180))
        draw.text((cur_cx + 170, 1502), "进入详情", fill=TEXT_PRIMARY, font=get_font(True, 24))

        cur_cx += 505

    img.save(os.path.join(OUTPUT_DIR, "redesign-01-home.png"))
    print("Generated redesign-01-home.png")


# -------------------------------------------------------------
# MOCKUP 2: DETAIL & EPISODES (Driver Friendly Island + Clean URL)
# -------------------------------------------------------------
def create_mockup_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Top Back & Header Bar (x: 60 ~ 2820 px, y: 40 ~ 140 px)
    draw_pill(draw, [60, 40, 240, 130], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((280, 58), "影片详情  /  庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 36))
    
    # In-Car Quick Action Badges at Top Right
    draw_pill(draw, [2340, 40, 2820, 130], fill=(28, 38, 56, 200), outline=(60, 75, 105), width=2)
    draw.text((2380, 62), "SGSR1 超分就绪  ·  臻彩滤镜", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Driver Gold Touch Zone (x: 60 ~ 1300 px, y: 160 ~ 1560 px)
    # 1. Video Preview Window (x: 60 ~ 1300 px, y: 160 ~ 840 px)
    draw_rounded_rect(draw, [60, 160, 1300, 840], radius=28, fill=(15, 20, 30), outline=(50, 70, 100), width=3)
    # Simulated video still inside preview
    draw.rectangle([80, 180, 1280, 820], fill=(25, 45, 65))
    draw.ellipse([610, 430, 750, 570], fill=(0, 0, 0, 160), outline=ACCENT_CYAN, width=3)
    draw.polygon([(660, 470), (660, 530), (715, 500)], fill=ACCENT_CYAN)
    draw.text((540, 590), "轻触直接全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 30))
    # Badges on video
    draw_pill(draw, [100, 200, 320, 260], fill=(16, 185, 129, 220))
    draw.text((125, 215), "4K 60FPS 极速", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # 2. Driver Primary Action Button Cluster (x: 60 ~ 1300 px, y: 870 ~ 1030 px)
    # Huge Primary Fullscreen Play Button (x: 60 ~ 540 px) - Height 140px!
    draw_pill(draw, [60, 870, 540, 1010], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((130, 912), "▶ 立即全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 38))

    # Quick Source Switch Button (x: 570 ~ 840 px)
    draw_pill(draw, [570, 870, 840, 1010], fill=(28, 38, 56, 220), outline=ACCENT_CYAN, width=2)
    draw.text((620, 918), "⚡ 极速换源", fill=ACCENT_CYAN, font=get_font(True, 32))

    # Favorite Button (x: 870 ~ 1070 px)
    draw_pill(draw, [870, 870, 1070, 1010], fill=(28, 38, 56, 220), outline=(70, 85, 115), width=2)
    draw.text((915, 918), "♥ 收藏", fill=TEXT_PRIMARY, font=get_font(True, 32))

    # Audio-Only Mode Trigger (x: 1100 ~ 1300 px) - Driver feature!
    draw_pill(draw, [1100, 870, 1300, 1010], fill=(45, 35, 70, 220), outline=ACCENT_AMBER, width=2)
    draw.text((1130, 918), "🔊 纯听剧", fill=ACCENT_AMBER, font=get_font(True, 32))

    # 3. Clean Metadata Card (x: 60 ~ 1300 px, y: 1040 ~ 1560 px)
    draw_rounded_rect(draw, [60, 1040, 1300, 1560], radius=24, fill=(24, 32, 46, 220), outline=(50, 65, 95), width=2)
    draw.text((100, 1075), "庆余年 第二季 (Joy of Life 2)", fill=TEXT_PRIMARY, font=get_font(True, 40))
    draw.text((100, 1135), "评分 ★ 8.8  ·  古装 / 权谋 / 奇幻  ·  共 36 集全", fill=ACCENT_AMBER, font=get_font(True, 28))
    draw.text((100, 1185), "主演：张若昀 / 李沁 / 陈道明 / 吴刚 / 田雨", fill=TEXT_SECONDARY, font=get_font(False, 26))
    
    # Clean Synopsis - Formatted and readable, NO raw ugly URL string!
    synopsis = (
        "剧情简介：该剧改编自猫腻同名畅销小说，讲述了拥有神秘身世的青年范闲，历经家族、"
        "江湖、庙堂的种种考验与锤炼，书写出一段不同寻常的人生传奇。第二季中范闲回归南庆，"
        "直面内库亏空与朝堂纷争，展开更波澜壮阔的智勇博弈。"
    )
    draw.text((100, 1250), synopsis[:48], fill=TEXT_SECONDARY, font=get_font(False, 26))
    draw.text((100, 1295), synopsis[48:96], fill=TEXT_SECONDARY, font=get_font(False, 26))
    draw.text((100, 1340), synopsis[96:], fill=TEXT_SECONDARY, font=get_font(False, 26))

    # In-Car Audio/Video Enhancement Tags
    draw_pill(draw, [100, 1450, 340, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((130, 1468), "SGSR1 边缘锐化", fill=ACCENT_CYAN, font=get_font(True, 22))

    draw_pill(draw, [360, 1450, 600, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((390, 1468), "座舱环绕声场", fill=ACCENT_CYAN, font=get_font(True, 22))

    # RIGHT HALF: Line Source & Episodes Grid (x: 1360 ~ 2820 px, y: 160 ~ 1560 px)
    draw_rounded_rect(draw, [1360, 160, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    
    # Source Switcher Bar (x: 1400 ~ 2780 px, y: 200 ~ 300 px)
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

    # Range Quick Jumps (y: 330 ~ 410 px)
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

    # Episodes Grid (x: 1400 ~ 2780 px, y: 440 ~ 1520 px)
    # Huge In-Car Touch Squares (155px wide x 135px tall, spacing 24px)
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

            if ep_num == 18: # Current Playing
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=18, fill=(37, 99, 235), outline=ACCENT_CYAN, width=3)
                draw.text((bx0 + 40, by0 + 25), f"{ep_num}", fill=TEXT_PRIMARY, font=get_font(True, 42))
                draw.text((bx0 + 25, by0 + 85), "正在播放", fill=ACCENT_CYAN, font=get_font(True, 20))
            else:
                draw_rounded_rect(draw, [bx0, by0, bx1, by1], radius=18, fill=(28, 38, 54, 200), outline=(50, 65, 90), width=2)
                draw.text((bx0 + 55, by0 + 42), f"{ep_num}", fill=TEXT_SECONDARY, font=get_font(True, 36))

    img.save(os.path.join(OUTPUT_DIR, "redesign-02-detail.png"))
    print("Generated redesign-02-detail.png")


# -------------------------------------------------------------
# MOCKUP 3: FULLSCREEN PLAYER & DRIVER CONTROL ISLAND
# -------------------------------------------------------------
def create_mockup_03_player():
    img = Image.new("RGBA", (2880, 1620), (10, 12, 16))
    draw = ImageDraw.Draw(img, "RGBA")

    # High Dynamic Cinematic Background Frame simulation
    draw.rectangle([0, 0, 2880, 1620], fill=(20, 28, 42))
    # Ambient movie lighting gradient
    for y in range(0, 300):
        alpha = int(220 * (1 - y / 300))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))
    for y in range(1100, 1620):
        alpha = int(240 * ((y - 1100) / 520))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))

    # Center Visual Mock
    draw.text((1200, 700), "【4K 60FPS 电影画面呈现中】", fill=(80, 110, 150), font=get_font(True, 44))

    # 1. Top Control Bar (y: 40 ~ 140 px)
    draw_pill(draw, [60, 40, 240, 130], fill=(20, 28, 40, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 退出", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((280, 60), "庆余年 第二季 - 第 18 集", fill=TEXT_PRIMARY, font=get_font(True, 36))
    draw_pill(draw, [780, 55, 1020, 115], fill=(16, 185, 129, 200))
    draw.text((805, 70), "IJK 硬解 · SGSR1", fill=TEXT_PRIMARY, font=get_font(True, 22))

    # Top Right Car Quick Toggles
    draw_pill(draw, [1980, 45, 2340, 125], fill=(30, 40, 58, 220), outline=ACCENT_AMBER, width=2)
    draw.text((2015, 65), "🔊 一键熄屏听剧", fill=ACCENT_AMBER, font=get_font(True, 28))

    draw_pill(draw, [2380, 45, 2580, 125], fill=(30, 40, 58, 220), outline=(60, 75, 100), width=2)
    draw.text((2420, 65), "🔒 锁屏防触", fill=TEXT_SECONDARY, font=get_font(False, 26))

    draw_pill(draw, [2620, 45, 2820, 125], fill=(30, 40, 58, 220), outline=(60, 75, 100), width=2)
    draw.text((2675, 65), "15:43", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # 2. Mid Screen In-Car Gesture Guide Badges (Subtle low-contrast)
    draw_rounded_rect(draw, [60, 500, 180, 900], radius=30, fill=(20, 30, 45, 120), outline=(50, 70, 100, 140), width=2)
    draw.text((85, 540), "☼\n\n左\n屏\n上\n下\n滑\n\n调\n亮\n度", fill=TEXT_SECONDARY, font=get_font(False, 24))

    draw_rounded_rect(draw, [2700, 500, 2820, 900], radius=30, fill=(20, 30, 45, 120), outline=(50, 70, 100, 140), width=2)
    draw.text((2725, 540), "🔊\n\n右\n屏\n上\n下\n滑\n\n调\n音\n量", fill=TEXT_SECONDARY, font=get_font(False, 24))

    # 3. Heavy-Duty Anti-Vibration Scrubber Progress Bar (y: 1220 ~ 1320 px)
    # Time Bubble (x: 60 ~ 300 px, y: 1240 px)
    draw.text((60, 1240), "42:15 / 58:30", fill=TEXT_PRIMARY, font=get_font(True, 34))
    
    # Progress Track (Height 20px, Heavy Scrubber Thumb 46px)
    draw_rounded_rect(draw, [320, 1250, 2820, 1270], radius=10, fill=(60, 75, 95))
    draw_rounded_rect(draw, [320, 1250, 2120, 1270], radius=10, fill=ACCENT_CYAN) # Played
    draw.ellipse([2100, 1237, 2146, 1283], fill=(255, 255, 255), outline=ACCENT_CYAN, width=4) # Thumb

    # 4. DRIVER CONTROL ISLAND (Bottom Left, x: 60 ~ 1640 px, y: 1360 ~ 1560 px)
    # Height: 150px! Single-button width: 230~300px! Zero misclick in vibrations!
    # Main Play/Pause Capsule
    draw_pill(draw, [60, 1360, 320, 1550], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((120, 1425), "⏸ 暂停", fill=TEXT_PRIMARY, font=get_font(True, 38))

    # Quick Jump -15s
    draw_pill(draw, [350, 1360, 560, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((385, 1430), "⏪ -15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    # Quick Jump +15s
    draw_pill(draw, [590, 1360, 800, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((625, 1430), "⏩ +15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    # Picture Preset Capsule (In-Car 臻彩 mode)
    draw_pill(draw, [830, 1360, 1190, 1550], fill=(28, 48, 70, 240), outline=(0, 210, 255), width=3)
    draw.text((880, 1400), "🎨 画面预设", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((880, 1445), "臻彩高对比", fill=ACCENT_CYAN, font=get_font(True, 34))

    # Audio Preset Capsule (In-Car Theater Sound)
    draw_pill(draw, [1220, 1360, 1580, 1550], fill=(45, 36, 65, 240), outline=ACCENT_AMBER, width=3)
    draw.text((1260, 1400), "🔊 座舱音效", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((1260, 1445), "影院环绕声", fill=ACCENT_AMBER, font=get_font(True, 34))

    # 5. Secondary Tools Cluster (Bottom Right, x: 1980 ~ 2820 px, y: 1360 ~ 1550 px)
    sec_buttons = [("倍速 1.25x", 1980), ("选集 (36)", 2200), ("比例 16:9", 2420), ("更多设置", 2640)]
    for btn_text, bx in sec_buttons:
        draw_pill(draw, [bx, 1380, bx + 195, 1530], fill=(26, 34, 48, 220), outline=(60, 75, 100), width=2)
        draw.text((bx + 28, 1435), btn_text, fill=TEXT_SECONDARY, font=get_font(True, 26))

    img.save(os.path.join(OUTPUT_DIR, "redesign-03-player.png"))
    print("Generated redesign-03-player.png")


# -------------------------------------------------------------
# MOCKUP 4: SEARCH & VOICE INPUT (Big Input + Dual Waterfall)
# -------------------------------------------------------------
def create_mockup_04_search():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # 1. Top Unified Search Header Bar (x: 60 ~ 2820 px, y: 40 ~ 190 px, height 150px!)
    draw_pill(draw, [60, 40, 220, 180], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 88), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    # Big Search Bar Capsule (x: 250 ~ 2350 px)
    draw_pill(draw, [250, 40, 2350, 180], fill=(24, 34, 52, 240), outline=ACCENT_CYAN, width=3)
    draw.text((320, 85), "🔍 庆余年|", fill=TEXT_PRIMARY, font=get_font(True, 42))

    # Voice Input inside Search Bar (Huge button x: 1900 ~ 2310 px)
    draw_pill(draw, [1920, 55, 2310, 165], fill=(37, 99, 235), outline=None)
    draw.text((1975, 88), "🎙 语音搜片中...", fill=TEXT_PRIMARY, font=get_font(True, 32))

    # Search & Clear buttons
    draw_pill(draw, [2390, 40, 2600, 180], fill=(16, 185, 129), outline=None)
    draw.text((2445, 88), "搜索", fill=TEXT_PRIMARY, font=get_font(True, 34))

    draw_pill(draw, [2630, 40, 2820, 180], fill=(45, 55, 75), outline=None)
    draw.text((2685, 88), "清空", fill=TEXT_SECONDARY, font=get_font(False, 30))

    # 2. LEFT HALF: Ergonomic In-Car Large Virtual Keyboard (x: 60 ~ 1350 px, y: 230 ~ 1560 px)
    draw_rounded_rect(draw, [60, 230, 1350, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((100, 270), "车载防颠簸大键盘 (支持简拼/全拼/首字母)", fill=TEXT_MUTED, font=get_font(True, 26))

    # System IME Switcher Link
    draw_pill(draw, [960, 255, 1310, 315], fill=(30, 50, 80), outline=ACCENT_CYAN, width=1)
    draw.text((990, 272), "⌨ 唤起系统输入法", fill=ACCENT_CYAN, font=get_font(True, 22))

    # 4-Row In-Car Large Keys (Width 115px, Height 110px, Spacing 12px)
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

    # Bottom Quick Pinyin Suffix Row
    pinyin_suffixes = ["庆", "余", "年", "第二季", "电影", "电视剧", "动漫"]
    cur_px = 100
    for ps in pinyin_suffixes:
        draw_pill(draw, [cur_px, 920, cur_px + 155, 1000], fill=(35, 55, 85), outline=(70, 95, 135), width=1)
        draw.text((cur_px + 30, 945), ps, fill=TEXT_PRIMARY, font=get_font(True, 24))
        cur_px += 175

    # 3. RIGHT HALF: Recent Searches & Real-Time Hot Results (x: 1400 ~ 2820 px, y: 230 ~ 1560 px)
    draw_rounded_rect(draw, [1400, 230, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    
    # Recent Tags (x: 1440 ~ 2780 px, y: 260 ~ 380 px)
    draw.text((1440, 270), "最近历史搜索：", fill=TEXT_MUTED, font=get_font(True, 28))
    hist_tags = ["庆余年2", "繁花", "周处除三害", "三体", "飞驰人生2"]
    cur_hx = 1660
    for ht in hist_tags:
        draw_pill(draw, [cur_hx, 255, cur_hx + 190, 325], fill=(30, 40, 58), outline=(60, 75, 100), width=1)
        draw.text((cur_hx + 30, 275), ht, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_hx += 210

    # Search Results List / Hot Billboard Cards (y: 380 ~ 1520 px)
    draw.text((1440, 370), "搜索结果 (联想匹配到 6 条结果)：", fill=ACCENT_CYAN, font=get_font(True, 30))
    
    results = [
        ("1", "庆余年 第二季 (2024)", "36集全 · 4K 臻彩超清 · 张若昀 / 陈道明", "立即播放 ▶"),
        ("2", "庆余年 第一季 (2019)", "46集全 · 蓝光1080P · 经典重温", "立即播放 ▶"),
        ("3", "庆余年 特别篇 / 花絮合集", "12个片段 · 原画超清 · 幕后专访", "查看详情"),
        ("4", "庆余年 原声大碟音乐集", "纯音频专辑 · 杜比全景声音效", "纯音频听 ▶"),
        ("5", "周处除三害 (2024)", "电影 · 4K HDR · 阮经天", "立即播放 ▶")
    ]
    cur_ry = 430
    for r_rank, r_title, r_desc, r_action in results:
        draw_rounded_rect(draw, [1440, cur_ry, 2780, cur_ry + 195], radius=20, fill=(28, 38, 56, 220), outline=(60, 80, 115), width=2)
        # Rank badge
        draw_pill(draw, [1470, cur_ry + 35, 1530, cur_ry + 95], fill=(37, 99, 235))
        draw.text((1490, cur_ry + 45), r_rank, fill=TEXT_PRIMARY, font=get_font(True, 30))
        # Text
        draw.text((1560, cur_ry + 35), r_title, fill=TEXT_PRIMARY, font=get_font(True, 34))
        draw.text((1560, cur_ry + 95), r_desc, fill=TEXT_SECONDARY, font=get_font(False, 26))
        # Action pill
        draw_pill(draw, [2500, cur_ry + 45, 2740, cur_ry + 150], fill=(16, 185, 129), outline=None)
        draw.text((2530, cur_ry + 78), r_action, fill=TEXT_PRIMARY, font=get_font(True, 26))
        cur_ry += 220

    img.save(os.path.join(OUTPUT_DIR, "redesign-04-search.png"))
    print("Generated redesign-04-search.png")


# -------------------------------------------------------------
# MOCKUP 5: SPLIT-VIEW SETTINGS & CAR SAFETY (Grouped Cards)
# -------------------------------------------------------------
def create_mockup_05_settings():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar (x: 60 ~ 2820 px, y: 40 ~ 140 px)
    draw_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((260, 60), "车机设置中心  /  座舱音画与行车安全", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # LEFT COLUMN: Category Navigation Sidebar (x: 60 ~ 680 px, y: 160 ~ 1560 px)
    draw_rounded_rect(draw, [60, 160, 680, 1560], radius=28, fill=(20, 28, 40, 240), outline=(50, 65, 90), width=2)
    
    settings_cats = [
        ("🎨 画面色彩与超分引擎", True),
        ("🔊 座舱声场与音效预设", False),
        ("🚗 行车安全与防分心策略", False),
        ("↩ 双模应用内返回控件", False),
        ("⚙ 播放解码与核心渲染", False),
        ("📦 订阅源与多仓管理", False),
        ("ℹ 关于车机定制版 (Car.7-r2)", False)
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

    # RIGHT COLUMN: Grouped Settings Cards (x: 720 ~ 2820 px, y: 160 ~ 1560 px)
    # CARD 1: Picture Enhancement Presets (Segmented Controller)
    draw_rounded_rect(draw, [720, 160, 2820, 500], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 200), "SDR / HDR 画面调优预设", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 250), "针对 17.3 英寸 3K 屏幕特别调校的 GLSL 着色器矩阵，实时渲染不重启解码器", fill=TEXT_MUTED, font=get_font(False, 26))

    # Segmented Pill Bar for Presets (x: 760 ~ 2780 px, y: 320 ~ 440 px)
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
    # Switch on right (Close grouping, no 32cm visual disconnect!)
    draw_pill(draw, [2540, 590, 2760, 680], fill=SUCCESS_GREEN) # Switch ON
    draw.ellipse([2670, 595, 2750, 675], fill=(255, 255, 255))
    draw.text((2570, 618), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 3: Temporal Smoothing (时域平滑补帧)
    draw_rounded_rect(draw, [720, 780, 2820, 1000], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 820), "时域平滑防抖算法 (Temporal Smoothing)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 870), "针对低帧率动漫和老剧集，消除运动残影与画面抖动，保持车内观影流畅体验", fill=TEXT_MUTED, font=get_font(False, 26))
    draw_pill(draw, [2540, 840, 2760, 930], fill=SUCCESS_GREEN)
    draw.ellipse([2670, 845, 2750, 925], fill=(255, 255, 255))
    draw.text((2570, 868), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # CARD 4: In-Car Driving Distraction Defense Mode (车机专属安全策略)
    draw_rounded_rect(draw, [720, 1030, 2820, 1550], radius=26, fill=(28, 40, 60, 240), outline=ACCENT_AMBER, width=2)
    draw.text((760, 1070), "🚗 车载行车安全防分心模式 (安全合规与防违章)", fill=ACCENT_AMBER, font=get_font(True, 34))
    draw.text((760, 1120), "根据车速与挡位自动切换，避免前排动态视频导致驾驶员分心或行车违规抓拍", fill=TEXT_MUTED, font=get_font(False, 26))

    safety_options = [
        ("车辆行驶挂 D 档时：自动切换为【纯音频听剧模式】(黑屏省电且安全)", True),
        ("前排副驾遮蔽模式：向右微调偏光显示，降低夜间驾驶员余光干扰", False),
        ("锁屏防误触保护：行车时锁定全屏手势，仅保留方向盘按键切集与音量", True),
    ]
    cur_osy = 1190
    for o_text, o_val in safety_options:
        draw.text((760, cur_osy + 25), o_text, fill=TEXT_PRIMARY, font=get_font(False, 28))
        if o_val:
            draw_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=SUCCESS_GREEN)
            draw.ellipse([2690, cur_osy + 15, 2750, cur_osy + 75], fill=(255, 255, 255))
        else:
            draw_pill(draw, [2560, cur_osy + 10, 2760, cur_osy + 80], fill=(60, 70, 90))
            draw.ellipse([2570, cur_osy + 15, 2630, cur_osy + 75], fill=(160, 170, 190))
        cur_osy += 105

    img.save(os.path.join(OUTPUT_DIR, "redesign-05-settings.png"))
    print("Generated redesign-05-settings.png")


# -------------------------------------------------------------
# MOCKUP 6: SCREEN-OFF AUDIO-ONLY HUD (In-Car Night Mode)
# -------------------------------------------------------------
def create_mockup_06_audio_hud():
    img = Image.new("RGBA", (2880, 1620), (8, 9, 12)) # 0nit Pure OLED Black
    draw = ImageDraw.Draw(img, "RGBA")

    # Top Exit / Resume Visual Pill
    draw_pill(draw, [60, 50, 360, 140], fill=(20, 24, 32, 200), outline=ACCENT_AMBER, width=2)
    draw.text((100, 75), "✕ 退出纯音频", fill=ACCENT_AMBER, font=get_font(True, 30))

    # Car Safe Status Tag Center
    draw.text((1050, 75), "行车纯音频听剧 HUD · 屏幕背光与解码功耗已降低 85%", fill=(120, 130, 150), font=get_font(False, 28))

    # Night Ambient Amber Minimalist Vinyl Disc Center (x: 400 ~ 950 px, y: 350 ~ 900 px)
    draw.ellipse([400, 350, 950, 900], fill=(18, 20, 26), outline=ACCENT_AMBER, width=4)
    draw.ellipse([550, 500, 800, 750], fill=(28, 32, 42), outline=(100, 80, 50), width=3)
    draw.ellipse([640, 590, 710, 660], fill=ACCENT_AMBER) # Center pin

    # Title & Metadata Info (x: 1050 ~ 2700 px, y: 380 ~ 800 px)
    draw_pill(draw, [1050, 380, 1340, 440], fill=(255, 179, 71, 180))
    draw.text((1080, 395), "座舱沉浸声场模式", fill=(20, 15, 10), font=get_font(True, 24))

    draw.text((1050, 480), "庆余年 第二季 - 第 18 集", fill=TEXT_PRIMARY, font=get_font(True, 56))
    draw.text((1050, 570), "杜比全景声 7.1 声道音轨 · 影院人声增强", fill=ACCENT_AMBER, font=get_font(True, 32))

    # Large Ambient Subtitles / Dialogue Display (HUD style, low brightness amber)
    draw.text((1050, 680), "【实时对话字幕】", fill=TEXT_MUTED, font=get_font(False, 26))
    draw.text((1050, 740), "范闲：“很多事情，非得亲自走一遭，才知晓其中深意。”", fill=(255, 215, 150), font=get_font(True, 40))

    # Heavy Progress Scrubber (x: 400 ~ 2480 px, y: 1020 ~ 1100 px)
    draw.text((400, 980), "42:15", fill=TEXT_PRIMARY, font=get_font(True, 36))
    draw.text((2380, 980), "58:30", fill=TEXT_MUTED, font=get_font(True, 36))
    draw_rounded_rect(draw, [400, 1040, 2480, 1064], radius=12, fill=(35, 40, 50))
    draw_rounded_rect(draw, [400, 1040, 1850, 1064], radius=12, fill=ACCENT_AMBER)
    draw.ellipse([1830, 1024, 1876, 1080], fill=(255, 255, 255), outline=ACCENT_AMBER, width=4)

    # Giant Ergonomic Touch Control Discs (y: 1160 ~ 1460 px, centered for driver reach)
    # Rewind -15s
    draw.ellipse([1000, 1220, 1180, 1400], fill=(25, 30, 40), outline=ACCENT_AMBER, width=3)
    draw.text((1035, 1290), "⏪ 15s", fill=ACCENT_AMBER, font=get_font(True, 36))

    # Giant Master Play/Pause Disc (Diameter 240px!)
    draw.ellipse([1320, 1190, 1560, 1430], fill=ACCENT_AMBER, outline=(255, 220, 160), width=4)
    draw.text((1405, 1280), "⏸", fill=(20, 15, 10), font=get_font(True, 72))

    # Fast Forward +15s
    draw.ellipse([1700, 1220, 1880, 1400], fill=(25, 30, 40), outline=ACCENT_AMBER, width=3)
    draw.text((1735, 1290), "⏩ 15s", fill=ACCENT_AMBER, font=get_font(True, 36))

    # Previous / Next Episode Pills
    draw_pill(draw, [650, 1250, 920, 1370], fill=(22, 26, 36), outline=(60, 70, 90), width=2)
    draw.text((710, 1290), "上一集 (17)", fill=TEXT_SECONDARY, font=get_font(True, 28))

    draw_pill(draw, [1960, 1250, 2230, 1370], fill=(22, 26, 36), outline=(60, 70, 90), width=2)
    draw.text((2020, 1290), "下一集 (19)", fill=TEXT_SECONDARY, font=get_font(True, 28))

    img.save(os.path.join(OUTPUT_DIR, "redesign-06-screenoff-audio.png"))
    print("Generated redesign-06-screenoff-audio.png")


# -------------------------------------------------------------
# GENERATE 6 BEFORE VS AFTER COMPARISONS
# -------------------------------------------------------------
def generate_comparison_card(before_path, after_path, out_path, title_text, problem_desc, solution_desc):
    # Output canvas: 2880 x 1800 (header 180px + two images side by side 1400x788 + bottom summary 200px)
    comp_w, comp_h = 2880, 1800
    comp = Image.new("RGBA", (comp_w, comp_h), (12, 16, 24))
    draw = ImageDraw.Draw(comp, "RGBA")

    # Header
    draw.text((60, 40), title_text, fill=TEXT_PRIMARY, font=get_font(True, 46))
    draw_pill(draw, [2340, 40, 2820, 120], fill=(37, 99, 235))
    draw.text((2380, 62), "17.3寸 3K 车机体验重构", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # Load Before & After images, resize to 1380 x 776 px (16:9)
    img_before = Image.open(before_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)
    img_after = Image.open(after_path).convert("RGBA").resize((1380, 776), Image.Resampling.LANCZOS)

    # Left: Before (Existing)
    draw_rounded_rect(draw, [55, 175, 1445, 965], radius=20, fill=(35, 25, 25), outline=(220, 38, 38), width=3)
    comp.paste(img_before, (60, 180))
    # Before Badge
    draw_pill(draw, [80, 200, 420, 270], fill=(220, 38, 38, 230))
    draw.text((105, 218), "原版问题状态 (BEFORE)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Right: After (Redesigned)
    draw_rounded_rect(draw, [1475, 175, 2825, 965], radius=20, fill=(20, 35, 30), outline=(16, 185, 129), width=3)
    comp.paste(img_after, (1480, 180))
    # After Badge
    draw_pill(draw, [1500, 200, 1860, 270], fill=(16, 185, 129, 230))
    draw.text((1525, 218), "整改后设计稿 (AFTER)", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Bottom Diagnosis & Optimization Analysis Box (y: 990 ~ 1740 px)
    # Left Problem Box
    draw_rounded_rect(draw, [55, 990, 1445, 1740], radius=24, fill=(25, 18, 22, 240), outline=(180, 40, 40), width=2)
    draw.text((95, 1030), "🚨 原版交互与人机工程缺陷分析", fill=(248, 113, 113), font=get_font(True, 34))
    cur_py = 1100
    for line in problem_desc:
        draw.text((95, cur_py), line, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_py += 55

    # Right Solution Box
    draw_rounded_rect(draw, [1475, 990, 2825, 1740], radius=24, fill=(18, 30, 28, 240), outline=(16, 185, 129), width=2)
    draw.text((1515, 1030), "✨ 3K 车机体验重构方案要点", fill=(52, 211, 153), font=get_font(True, 34))
    cur_sy = 1100
    for line in solution_desc:
        draw.text((1515, cur_sy), line, fill=TEXT_PRIMARY, font=get_font(False, 26))
        cur_sy += 55

    comp.save(out_path)
    print(f"Generated comparison: {out_path}")


def main():
    print("Starting generation of 6 High-Fidelity 3K In-Car Redesign Mockups...")
    create_mockup_01_home()
    create_mockup_02_detail()
    create_mockup_03_player()
    create_mockup_04_search()
    create_mockup_05_settings()
    create_mockup_06_audio_hud()

    print("\nStarting generation of 6 Side-by-Side Comparison Composites...")
    
    # 1. Home comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-home.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-01-home.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-01-home.png"),
        title_text="01. 主界面排版与主驾近端交互重构 (Home & Dashboard)",
        problem_desc=[
            "• 核心设置按钮被推到屏幕最右边缘 (x: 2302px / 30.6cm)，主驾需探身才能触达；",
            "• 顶端日期独占 1918px (25.5cm) 宽，信息利用率极低，属于电视大字残余；",
            "• 分类 Tab 条高度仅 101px (13.4mm)，手指点击容错率低，颠簸路况容易误触；",
            "• 缺乏车机特有的“上次播放继续”主驾胶囊卡片，每次上车续播链路过长；",
            "• 遥控器白色高亮边框残余，触摸屏无按下波纹视觉反馈。"
        ],
        solution_desc=[
            "• 设立【主驾左侧垂直导航侧边栏】(x: 0~240px)，设置/历史/直播触手可及；",
            "• 新增【一键继续播放 Hero 磁贴】(x: 280~1380px)，主驾抬手即播，无需翻页；",
            "• 顶部集成天气、电量/挡位状态，并提供车载语音搜片快速唤醒麦克风；",
            "• 增大海报卡片与分类 Tab 触控面积至 64dp+，彻底废除电视焦点巡焦边框；",
            "• 采用深空灰蓝微拟物玻璃形态，白天高对比防倒影、夜间低炫光护眼。"
        ]
    )

    # 2. Detail comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-detail.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-02-detail.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-02-detail.png"),
        title_text="02. 影片详情与剧集选集交互重构 (Detail & Episodes)",
        problem_desc=[
            "• 操作按钮全部向右沦陷：全屏(16.2cm)、快速搜索(20.1cm)、收藏(28.0cm)、换源(32.0cm)；",
            "• 中间直接暴露出长达 1552px 的原始 HTTP 播放 URL 字符串，界面视觉噪点严重；",
            "• 存在 previewPlayerBlock 遥控器焦点阻断假层，点击小窗无法直接切全屏；",
            "• 剧集列表单项下边距仅 1mm，纵向滚动时容易误触邻近集数；",
            "• 缺乏车机专属的“纯音频/熄屏听剧”快速切换入口。"
        ],
        solution_desc=[
            "• 将【全屏播放】、【换源】、【收藏】、【纯听剧】收拢在左侧 x < 1300px 黄金触达区；",
            "• 彻底隐藏原始 HTTP 播放串，重构为整洁优雅的演职员、评分与剧情简介卡片；",
            "• 移除遥控器假层，小窗视频支持直接轻触全屏；",
            "• 剧集选择重构为 155x135px 超大车载方块触控网格，增加当前集数呼吸光标；",
            "• 引入“1-10、11-20、21-30”分段快速跳选胶囊，大幅减少长途行车选集滑动次数。"
        ]
    )

    # 3. Player comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-player-controls-enabled.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-03-player.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-03-player.png"),
        title_text="03. 车载全屏播放器与主驾播控岛重构 (Fullscreen Player)",
        problem_desc=[
            "• 底部平铺 18 个密集小按键，单键宽仅 17.6mm、间距仅 3.6mm，颠簸路段误触率 >60%；",
            "• 时间显示框宽度仅 22mm，长视频时间戳（如 01:23:45）直接发生截断；",
            "• 四大音画胶囊横跨 2880px 全屏，右侧“音效·影院”落在 x: 2120px，主驾必须欠身；",
            "• 缺乏全屏盲操手势（上下滑调亮度/音量、左右滑快进退），严重分散驾驶精力；",
            "• 电视快进提示弹窗居中遮挡主体画面，影响观影视线。"
        ],
        solution_desc=[
            "• 打造【左下角主驾专属播控岛】：仅保留 5 个超大防颠簸胶囊（宽 >= 230px，高 150px）；",
            "• 播放/暂停、±15s 快进退、臻彩画质切换、座舱音效切换全部集中于左侧 1600px 内；",
            "• 进度条加粗至 20px，配合 46px 发光吸附手柄与放大的无截断时间气泡；",
            "• 建立完整车载手势层：左侧垂直滑调亮度、右侧垂直滑调音量、双击快速暂停；",
            "• 次要功能（倍速、比例、选集、解码）折叠收拢在右下角，主次分明。"
        ]
    )

    # 4. Search comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-search.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-04-search.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-04-search.png"),
        title_text="04. 车载智能搜索与语音交互重构 (Smart Search)",
        problem_desc=[
            "• 左侧塞满 36 键电视遥控器小全键盘，且代码强制杀死 (hiddenImm) 原生软键盘；",
            "• 封锁了车机自带的语音输入法与大按键车载手写板，输入片名极其繁琐；",
            "• 右半屏 50% 面积（约 18.5cm 宽）为全黑空白，大屏显示空间严重浪费；",
            "• 搜索热词采用单列纵向排版，下部热词排到 y: 1934px 跌出屏幕，翻找成本高；",
            "• 残留电视扫码推流搜索框，在独立 SIM 卡车机上完全失去意义。"
        ],
        solution_desc=[
            "• 顶部搜索栏集成【一键车载语音搜片】大按键，直接按住说话，行车零分心；",
            "• 开放调用 Android 原生输入法，同时内置大触控面积（115x110px）防颠簸字母键盘；",
            "• 废除电视扫码冗余，右侧 50% 空间打造为“最近搜索标签”与“实时搜索结果双列瀑布流”；",
            "• 搜索结果卡片直接带“立即播放”快捷按键，实现搜播闭环；",
            "• 适配拼音首字母简拼快速匹配（如输入 QYN 直接秒出《庆余年》）。"
        ]
    )

    # 5. Settings comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-settings-top.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-05-settings.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-05-settings.png"),
        title_text="05. 双栏分体式车机设置中心与安全策略 (Settings & Car Safety)",
        problem_desc=[
            "• 代码中左侧分类导航栏被写死 visibility='gone'，导致 40+ 设置项只能单列硬滑；",
            "• 高级设置通栏排布撕裂：配置名称在最左侧(x: 124px)，开关在最右侧(x: 2576px)，视距拉扯达 32cm；",
            "• 充斥大量电视工程级冗余参数（Webview 解析、DNS 聚合、M3U8 嗅探等）；",
            "• 严重缺失车载核心安全配置（车速联动、挂档黑屏、夜间低眩光、方向盘按键绑定）。"
        ],
        solution_desc=[
            "• 恢复并优化【左侧垂直分类导航栏】(x: 60~680px)，分类层级一目了然；",
            "• 采用【分组成组卡片设计】，标题、说明与开关紧凑集成在卡片右侧，消除视线拉扯；",
            "• 预设选择升级为大号分段触控条（臻彩、动漫、影院、护眼、日间防眩）；",
            "• 新增【车载行车安全防分心专区】：支持挂 D 档自动转纯音频听剧、防误触锁屏；",
            "• 折叠收敛极客底层参数，界面专为驾驶员与乘客友好设计。"
        ]
    )

    # 6. Screen-off audio comparison
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-player-controls.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-06-screenoff-audio.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-06-audio-mode.png"),
        title_text="06. 车载专属创新：一键熄屏纯音频听剧 HUD (Audio HUD Mode)",
        problem_desc=[
            "• 原版完全缺失“熄屏听剧/纯音频模式”，长途夜间行驶屏幕高亮直射驾驶员瞳孔；",
            "• 破坏夜间暗适应能力，倒影映射在前挡风玻璃上产生严重驾驶安全隐患；",
            "• 动态视频长时间渲染导致车机发热严重，并可能引起交警监控抓拍“行车看视频”违规；",
            "• 用户很多时候仅需听评书、相声、剧情对话，现有设计强行占用屏幕画面。"
        ],
        solution_desc=[
            "• 首创【一键熄屏纯音频听剧 HUD 模式】：Surface 挂起，背光功耗降低 85%；",
            "• 0nit OLED 纯黑背景 + 暖色微光琥珀黑胶视效，营造奢华低眩光座舱声学氛围；",
            "• 核心展示实时台词对话微光字幕与超大直径（240px）盲操圆形播控按键；",
            "• 完美满足长途高速巡航、夜间归家听剧、停车小憩听相声等高频真实车载场景；",
            "• 方向盘多媒体按键无缝接管快进退与暂停，行车安全系数达到最高等级。"
        ]
    )

    print("\nAll 6 Mockups and 6 Comparisons successfully generated!")

if __name__ == "__main__":
    main()
