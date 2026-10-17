"""
generate_ux_redesign_mockups.py
Generates 6 high-fidelity 3K (2880x1620) redesigned in-car UI mockups and
6 Before vs After side-by-side comparison graphics.
Strictly focused on: High-End In-Car Video Player Experience
(No vehicle telematics/D-gear dependencies, no screen-off blackout).
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

# Color Palette (Modern In-Car Dark Glassmorphism / Infuse & Apple TV Style)
BG_COLOR = (12, 16, 23)            # Cinematic deep dark
SURFACE_CARD = (22, 28, 40, 230)   # Glass card background
SURFACE_BORDER = (45, 55, 75, 255) # Border stroke
ACCENT_CYAN = (0, 210, 255)        # Modern tech cyan
ACCENT_BLUE = (37, 99, 235)        # Primary action blue
ACCENT_AMBER = (255, 179, 71)      # Rating / warm accent
TEXT_PRIMARY = (255, 255, 255)     # High contrast pure white
TEXT_SECONDARY = (160, 170, 185)   # Readable light grey
TEXT_MUTED = (100, 110, 128)       # Subtitle / metadata grey
SUCCESS_GREEN = (16, 185, 129)     # Active / low latency green

def draw_rounded_rect(draw, box, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

def draw_pill(draw, box, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    radius = (y1 - y0) // 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)


# ----------------------------------------------------------------------
# MOCKUP 1: HOME & STREAMLINED MEDIA CENTER (流媒体首页与无缝续播)
# ----------------------------------------------------------------------
def create_mockup_01_home():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Subtle ambient lighting
    draw.ellipse([700, -200, 2200, 500], fill=(20, 38, 65, 130))

    # 1. Left Vertical Navigation Rail (x: 0 ~ 220 px)
    draw.rectangle([0, 0, 220, 1620], fill=(16, 22, 32, 240))
    draw.line([220, 0, 220, 1620], fill=(40, 50, 68, 255), width=2)

    # Logo
    draw.text((45, 45), "TVBOX", fill=ACCENT_CYAN, font=get_font(True, 38))
    draw.text((48, 95), "CAR 3K", fill=TEXT_MUTED, font=get_font(True, 22))

    nav_items = [
        ("★ 推荐", True),
        ("▦ 影视", False),
        ("📺 直播", False),
        ("⏱ 历史", False),
        ("♥ 收藏", False),
        ("⚙ 设置", False),
    ]
    cur_y = 210
    for title, active in nav_items:
        if active:
            draw_rounded_rect(draw, [20, cur_y, 200, cur_y + 110], radius=18, fill=(30, 58, 100, 240), outline=ACCENT_CYAN, width=2)
            draw.text((50, cur_y + 36), title, fill=TEXT_PRIMARY, font=get_font(True, 30))
        else:
            draw_rounded_rect(draw, [20, cur_y, 200, cur_y + 110], radius=18, fill=(22, 28, 40, 140))
            draw.text((50, cur_y + 36), title, fill=TEXT_SECONDARY, font=get_font(False, 28))
        cur_y += 135

    # 2. Top Streamlined Header Bar (x: 260 ~ 2820 px, y: 35 ~ 130 px)
    # Search Input Bar
    draw_pill(draw, [260, 35, 1400, 125], fill=(24, 32, 46, 220), outline=(50, 65, 90), width=2)
    draw.text((310, 58), "🔍 搜索片名 / 首字母简拼 (例如: QYN2 庆余年)", fill=TEXT_MUTED, font=get_font(False, 28))

    # Top Filter Tags
    top_filters = ["全部精选", "电影", "电视剧", "4K HDR专区", "动漫", "纪录片"]
    cur_fx = 1440
    for idx, f_name in enumerate(top_filters):
        fw = 180
        if idx == 0:
            draw_pill(draw, [cur_fx, 40, cur_fx + fw, 120], fill=ACCENT_BLUE)
            draw.text((cur_fx + 30, 58), f_name, fill=TEXT_PRIMARY, font=get_font(True, 26))
        else:
            draw_pill(draw, [cur_fx, 40, cur_fx + fw, 120], fill=(26, 34, 48), outline=(48, 60, 80), width=1)
            draw.text((cur_fx + 35, 58), f_name, fill=TEXT_SECONDARY, font=get_font(False, 26))
        cur_fx += fw + 16

    # Clock & Network
    draw_pill(draw, [2640, 40, 2820, 120], fill=(24, 32, 46, 200), outline=(50, 65, 90), width=1)
    draw.text((2675, 58), "15:42 📶", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # 3. Main Stage: Left Resume Hero Card (x: 260 ~ 1340 px, y: 165 ~ 660 px)
    draw_rounded_rect(draw, [260, 165, 1340, 660], radius=28, fill=(24, 34, 52, 240), outline=(55, 75, 110), width=2)
    draw_pill(draw, [300, 205, 510, 260], fill=(37, 99, 235, 200))
    draw.text((325, 218), "上次播放", fill=TEXT_PRIMARY, font=get_font(True, 24))

    draw.text((300, 285), "庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((300, 350), "第 18 集 · 4K 臻彩超清 (已播至 72% / 42分15秒)", fill=ACCENT_CYAN, font=get_font(False, 28))

    # Progress bar
    draw_rounded_rect(draw, [300, 420, 1300, 436], radius=8, fill=(45, 55, 75))
    draw_rounded_rect(draw, [300, 420, 1020, 436], radius=8, fill=ACCENT_CYAN)

    # Resume Play Button (Large touch target)
    draw_pill(draw, [300, 480, 720, 605], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
    draw.text((375, 518), "▶ 继续播放", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # Select Episode Button
    draw_pill(draw, [750, 480, 1060, 605], fill=(32, 42, 60), outline=(60, 75, 100), width=2)
    draw.text((805, 522), "▦ 快速选集", fill=TEXT_SECONDARY, font=get_font(True, 30))

    # 4. Right Stage: Today Hot Highlight Banner (x: 1380 ~ 2820 px, y: 165 ~ 660 px)
    draw_rounded_rect(draw, [1380, 165, 2820, 660], radius=28, fill=(28, 38, 56, 240), outline=(60, 80, 115), width=2)
    draw_pill(draw, [1420, 205, 1670, 260], fill=(220, 38, 38, 200))
    draw.text((1445, 218), "热门影视榜 TOP 1", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw.text((1420, 285), "繁花 (Blossoms Shanghai)", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((1420, 350), "王家卫导演作品 · 沪语原声 4K 原画 · 杜比全景声", fill=ACCENT_AMBER, font=get_font(False, 28))
    draw.text((1420, 415), "九十年代上海黄河路商海浮沉，阿宝与李李的商战传奇...", fill=TEXT_SECONDARY, font=get_font(False, 26))

    draw_pill(draw, [1420, 485, 1800, 605], fill=(16, 185, 129), outline=None)
    draw.text((1485, 522), "▶ 立即观看", fill=TEXT_PRIMARY, font=get_font(True, 34))

    # 5. Bottom Rows: High-Res 3K Posters (y: 700 ~ 1580 px)
    draw.text((260, 700), "🔥 正在热播 · 4K 超清专区", fill=TEXT_PRIMARY, font=get_font(True, 32))

    card_data = [
        ("流浪地球 2", "评分 9.1", "科幻 / 硬核 / 4K HDR", (40, 58, 90)),
        ("三体 (剧版)", "评分 8.8", "剧情 / 悬疑 / 臻彩", (50, 45, 80)),
        ("周处除三害", "评分 8.5", "犯罪 / 动作 / 原画", (75, 48, 38)),
        ("狂飙", "评分 9.0", "扫黑 / 刑侦 / 超清", (35, 70, 60)),
        ("大理寺少卿游", "评分 8.2", "古装 / 探案 / 4K", (60, 60, 50)),
    ]
    cur_cx = 260
    for title, score, tag, color in card_data:
        draw_rounded_rect(draw, [cur_cx, 760, cur_cx + 480, 1560], radius=24, fill=color, outline=(60, 75, 105), width=2)
        # Poster mockup
        draw.rectangle([cur_cx + 20, 780, cur_cx + 460, 1340], fill=(color[0] + 15, color[1] + 15, color[2] + 15))
        draw.text((cur_cx + 50, 1060), title, fill=TEXT_PRIMARY, font=get_font(True, 38))

        draw_pill(draw, [cur_cx + 40, 800, cur_cx + 200, 860], fill=(234, 88, 12, 220))
        draw.text((cur_cx + 60, 815), score, fill=TEXT_PRIMARY, font=get_font(True, 24))

        draw.text((cur_cx + 30, 1370), title, fill=TEXT_PRIMARY, font=get_font(True, 32))
        draw.text((cur_cx + 30, 1425), tag, fill=TEXT_SECONDARY, font=get_font(False, 24))

        draw_pill(draw, [cur_cx + 30, 1475, cur_cx + 450, 1535], fill=(37, 99, 235, 200))
        draw.text((cur_cx + 175, 1488), "进入详情", fill=TEXT_PRIMARY, font=get_font(True, 24))

        cur_cx += 512

    img.save(os.path.join(OUTPUT_DIR, "redesign-01-home.png"))
    print("Generated redesign-01-home.png")


# ----------------------------------------------------------------------
# MOCKUP 2: DETAIL & SMART SOURCE SELECTION (智能测速与多源切换)
# ----------------------------------------------------------------------
def create_mockup_02_detail():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header Bar
    draw_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((260, 58), "影片详情  /  庆余年 第二季", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # Audio/Video Badges top right
    draw_pill(draw, [2360, 40, 2820, 130], fill=(26, 36, 52, 200), outline=(55, 75, 105), width=2)
    draw.text((2400, 62), "SGSR1 超分就绪 · 杜比全景声", fill=ACCENT_CYAN, font=get_font(False, 26))

    # LEFT HALF: Video Preview & Primary Controls (x: 60 ~ 1320 px, y: 160 ~ 1560 px)
    draw_rounded_rect(draw, [60, 160, 1320, 850], radius=28, fill=(16, 22, 32), outline=(50, 68, 98), width=3)
    draw.rectangle([80, 180, 1300, 830], fill=(25, 45, 68))
    # Play Center Circle
    draw.ellipse([620, 435, 760, 575], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=3)
    draw.polygon([(670, 475), (670, 535), (725, 505)], fill=ACCENT_CYAN)
    draw.text((545, 595), "轻触直接全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 28))
    # Tag on video
    draw_pill(draw, [100, 200, 320, 260], fill=(16, 185, 129, 220))
    draw.text((125, 215), "4K 60FPS 极速", fill=TEXT_PRIMARY, font=get_font(True, 24))

    # Primary Actions Cluster (x: 60 ~ 1320 px, y: 880 ~ 1020 px)
    # Master Fullscreen Button
    draw_pill(draw, [60, 880, 560, 1010], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((140, 920), "▶ 立即全屏播放", fill=TEXT_PRIMARY, font=get_font(True, 38))

    # Smart Source Switch
    draw_pill(draw, [590, 880, 860, 1010], fill=(28, 38, 56, 220), outline=ACCENT_CYAN, width=2)
    draw.text((640, 922), "⚡ 测速切源", fill=ACCENT_CYAN, font=get_font(True, 32))

    # Favorite Button
    draw_pill(draw, [890, 880, 1090, 1010], fill=(28, 38, 56, 220), outline=(65, 80, 110), width=2)
    draw.text((935, 922), "♥ 收藏", fill=TEXT_PRIMARY, font=get_font(True, 32))

    # PiP Floating Trigger Button
    draw_pill(draw, [1120, 880, 1320, 1010], fill=(32, 45, 68, 220), outline=ACCENT_AMBER, width=2)
    draw.text((1150, 922), "📺 画中画", fill=ACCENT_AMBER, font=get_font(True, 32))

    # Formatted Metadata Card (x: 60 ~ 1320 px, y: 1040 ~ 1560 px)
    draw_rounded_rect(draw, [60, 1040, 1320, 1560], radius=24, fill=(24, 32, 46, 220), outline=(50, 65, 95), width=2)
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

    # Enhancement Badges
    draw_pill(draw, [100, 1450, 360, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((130, 1468), "SGSR1 边缘超分", fill=ACCENT_CYAN, font=get_font(True, 22))

    draw_pill(draw, [380, 1450, 640, 1515], fill=(30, 58, 95, 200), outline=ACCENT_CYAN, width=1)
    draw.text((410, 1468), "杜比 7.1 全景声", fill=ACCENT_CYAN, font=get_font(True, 22))

    # RIGHT HALF: Smart Sources with Latency + Episodes Grid (x: 1380 ~ 2820 px)
    draw_rounded_rect(draw, [1380, 160, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)

    # Multi-Source Latency Bar
    draw.text((1420, 215), "播放线路 (智能测速)：", fill=TEXT_MUTED, font=get_font(True, 28))
    sources = [
        ("专线·4K超清", "32ms (最快)", True),
        ("备线·蓝光HDR", "85ms", False),
        ("专线·原画", "120ms", False),
        ("官方解析", "240ms", False)
    ]
    cur_sx = 1720
    for s_name, s_lat, s_act in sources:
        if s_act:
            draw_pill(draw, [cur_sx, 195, cur_sx + 260, 275], fill=(37, 99, 235), outline=(96, 165, 250), width=2)
            draw.text((cur_sx + 20, 210), s_name, fill=TEXT_PRIMARY, font=get_font(True, 22))
            draw.text((cur_sx + 20, 240), f"⚡ {s_lat}", fill=ACCENT_CYAN, font=get_font(True, 18))
        else:
            draw_pill(draw, [cur_sx, 195, cur_sx + 240, 275], fill=(28, 38, 54), outline=(50, 65, 88), width=1)
            draw.text((cur_sx + 20, 210), s_name, fill=TEXT_SECONDARY, font=get_font(False, 22))
            draw.text((cur_sx + 20, 240), f"📶 {s_lat}", fill=TEXT_MUTED, font=get_font(False, 18))
        cur_sx += 275

    # Range Jump Capsules
    draw.text((1420, 325), "剧集分段：", fill=TEXT_MUTED, font=get_font(True, 26))
    ranges = [("1-10", False), ("11-20 (当前)", True), ("21-30", False), ("31-36", False)]
    cur_rx = 1570
    for r_name, r_act in ranges:
        if r_act:
            draw_pill(draw, [cur_rx, 310, cur_rx + 220, 380], fill=ACCENT_CYAN)
            draw.text((cur_rx + 30, 328), r_name, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_pill(draw, [cur_rx, 310, cur_rx + 180, 380], fill=(30, 40, 58))
            draw.text((cur_rx + 35, 328), r_name, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_rx += 240

    # Large In-Car Touch Grid
    ep_x0, ep_y0 = 1420, 420
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

    img.save(os.path.join(OUTPUT_DIR, "redesign-02-detail.png"))
    print("Generated redesign-02-detail.png")


# ----------------------------------------------------------------------
# MOCKUP 3: FULLSCREEN PLAYER & MODERN TOUCH CONTROLS (现代触控播放器)
# ----------------------------------------------------------------------
def create_mockup_03_player():
    img = Image.new("RGBA", (2880, 1620), (10, 12, 16))
    draw = ImageDraw.Draw(img, "RGBA")

    # High Dynamic Cinematic Background Frame simulation
    draw.rectangle([0, 0, 2880, 1620], fill=(20, 28, 42))
    for y in range(0, 300):
        alpha = int(220 * (1 - y / 300))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))
    for y in range(1100, 1620):
        alpha = int(240 * ((y - 1100) / 520))
        draw.line([0, y, 2880, y], fill=(0, 0, 0, alpha))

    draw.text((1200, 700), "【4K 60FPS 电影画面呈现中】", fill=(80, 110, 150), font=get_font(True, 44))

    # Top Bar
    draw_pill(draw, [60, 40, 240, 130], fill=(20, 28, 40, 220), outline=ACCENT_CYAN, width=2)
    draw.text((105, 62), "← 退出", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((280, 60), "庆余年 第二季 - 第 18 集", fill=TEXT_PRIMARY, font=get_font(True, 36))
    draw_pill(draw, [780, 55, 1020, 115], fill=(16, 185, 129, 200))
    draw.text((805, 70), "IJK 硬解 · SGSR1", fill=TEXT_PRIMARY, font=get_font(True, 22))

    # Top Right Controls: Background Audio Toggle + PiP Trigger
    draw_pill(draw, [1920, 45, 2280, 125], fill=(30, 40, 58, 220), outline=ACCENT_CYAN, width=2)
    draw.text((1955, 65), "🎧 后台播放：开", fill=ACCENT_CYAN, font=get_font(True, 26))

    draw_pill(draw, [2320, 45, 2580, 125], fill=(30, 40, 58, 220), outline=ACCENT_AMBER, width=2)
    draw.text((2360, 65), "📺 悬浮画中画", fill=ACCENT_AMBER, font=get_font(True, 26))

    draw_pill(draw, [2620, 45, 2820, 125], fill=(30, 40, 58, 220), outline=(60, 75, 100), width=2)
    draw.text((2675, 65), "15:43", fill=TEXT_PRIMARY, font=get_font(True, 28))

    # Center Gesture Guidance Overlays (Low-contrast HUD)
    # Left: Brightness
    draw_rounded_rect(draw, [60, 500, 180, 900], radius=30, fill=(20, 30, 45, 130), outline=(50, 70, 100, 140), width=2)
    draw.text((85, 540), "☼\n\n左\n半\n屏\n滑\n动\n\n调\n亮\n度", fill=TEXT_SECONDARY, font=get_font(False, 24))

    # Right: Volume
    draw_rounded_rect(draw, [2700, 500, 2820, 900], radius=30, fill=(20, 30, 45, 130), outline=(50, 70, 100, 140), width=2)
    draw.text((2725, 540), "🔊\n\n右\n半\n屏\n滑\n动\n\n调\n音\n量", fill=TEXT_SECONDARY, font=get_font(False, 24))

    # Center Long Press 2x Speed Tip
    draw_pill(draw, [1240, 500, 1640, 580], fill=(0, 0, 0, 180), outline=ACCENT_CYAN, width=2)
    draw.text((1280, 522), "⚡ 长按屏幕 2.0x 极速快进", fill=ACCENT_CYAN, font=get_font(True, 24))

    # Scrubber Progress Bar
    draw.text((60, 1235), "42:15 / 58:30", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw_rounded_rect(draw, [320, 1245, 2820, 1265], radius=10, fill=(60, 75, 95))
    draw_rounded_rect(draw, [320, 1245, 2120, 1265], radius=10, fill=ACCENT_CYAN)
    draw.ellipse([2100, 1232, 2146, 1278], fill=(255, 255, 255), outline=ACCENT_CYAN, width=4)

    # Bottom Modern Controls Row (y: 1360 ~ 1550 px)
    # 1. Left Primary Island
    draw_pill(draw, [60, 1360, 300, 1550], fill=(37, 99, 235), outline=(96, 165, 250), width=3)
    draw.text((115, 1425), "⏸ 暂停", fill=TEXT_PRIMARY, font=get_font(True, 38))

    draw_pill(draw, [330, 1360, 530, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((365, 1430), "⏪ -15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [560, 1360, 760, 1550], fill=(28, 38, 54, 230), outline=ACCENT_CYAN, width=2)
    draw.text((595, 1430), "⏩ +15s", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [790, 1360, 1080, 1550], fill=(28, 48, 70, 240), outline=(0, 210, 255), width=3)
    draw.text((830, 1400), "🎨 画面调优", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((830, 1445), "臻彩高对比", fill=ACCENT_CYAN, font=get_font(True, 34))

    draw_pill(draw, [1110, 1360, 1400, 1550], fill=(45, 36, 65, 240), outline=ACCENT_AMBER, width=3)
    draw.text((1150, 1400), "🔊 杜比音效", fill=TEXT_MUTED, font=get_font(False, 24))
    draw.text((1150, 1445), "影院环绕声", fill=ACCENT_AMBER, font=get_font(True, 34))

    # 2. Right Secondary Island
    sec_buttons = [("倍速 1.25x", 1880), ("选集 (36)", 2120), ("比例 16:9", 2360), ("切源·32ms", 2600)]
    for btn_text, bx in sec_buttons:
        draw_pill(draw, [bx, 1380, bx + 215, 1530], fill=(26, 34, 48, 220), outline=(60, 75, 100), width=2)
        draw.text((bx + 30, 1435), btn_text, fill=TEXT_SECONDARY, font=get_font(True, 26))

    img.save(os.path.join(OUTPUT_DIR, "redesign-03-player.png"))
    print("Generated redesign-03-player.png")


# ----------------------------------------------------------------------
# MOCKUP 4: PIP FLOATING OVER IN-CAR NAVIGATION (悬浮画中画与导航协同)
# ----------------------------------------------------------------------
def create_mockup_04_pip_navigation():
    img = Image.new("RGBA", (2880, 1620), (18, 22, 28))
    draw = ImageDraw.Draw(img, "RGBA")

    # Background: In-Car Navigation Map UI Simulation (高德/百度车机导航地图实景模拟)
    # Road network simulation
    draw.rectangle([0, 0, 2880, 1620], fill=(25, 32, 42)) # Map terrain
    # Green parks
    draw.rectangle([300, 200, 1200, 800], fill=(28, 42, 38))
    draw.rectangle([1800, 600, 2600, 1400], fill=(26, 38, 35))
    # Major Expressway (Cyan navigation line)
    points = [(200, 1620), (600, 1100), (1100, 850), (1600, 700), (2100, 400), (2600, 200)]
    for i in range(len(points)-1):
        draw.line([points[i], points[i+1]], fill=(0, 180, 136), width=36) # Route line
        draw.line([points[i], points[i+1]], fill=(0, 230, 170), width=18)

    # In-Car Navigation HUD: Left Side (x: 80 ~ 680 px, y: 80 ~ 480 px)
    draw_rounded_rect(draw, [80, 80, 680, 480], radius=24, fill=(16, 24, 36, 240), outline=(40, 60, 80), width=2)
    draw.text((120, 110), "700m 后向右进入", fill=TEXT_MUTED, font=get_font(False, 28))
    draw.text((120, 155), "机场高速连接线", fill=TEXT_PRIMARY, font=get_font(True, 44))
    draw.text((120, 230), "当前车速 82 km/h  |  限速 100", fill=ACCENT_CYAN, font=get_font(True, 30))
    draw.line([120, 290, 640, 290], fill=(40, 55, 75))
    draw.text((120, 320), "剩余 18.5 km · 预计 22 分钟到达", fill=TEXT_SECONDARY, font=get_font(False, 28))
    draw.text((120, 375), "当前播报：前方 2 公里有测速抓拍", fill=ACCENT_AMBER, font=get_font(False, 24))

    # Top Status Banner: Indicating Background Audio from TVBox
    draw_pill(draw, [800, 40, 2080, 120], fill=(20, 30, 45, 230), outline=ACCENT_CYAN, width=2)
    draw.text((840, 62), "🎧 TVBox 正在后台播放中 · 庆余年 第二季 第18集 (音频正常混音输出)", fill=TEXT_PRIMARY, font=get_font(True, 26))

    # RIGHT SIDE: TVBox PiP Floating Window (x: 1720 ~ 2800 px, y: 220 ~ 920 px)
    # Size: 1080 x 700 px (16:9 Floating player window over navigation)
    draw_rounded_rect(draw, [1720, 220, 2800, 920], radius=24, fill=(12, 16, 24), outline=ACCENT_CYAN, width=4)
    # Simulated video inside PiP
    draw.rectangle([1730, 230, 2790, 910], fill=(25, 45, 65))
    draw.text((2050, 520), "【TVBox 悬浮画中画正在播放】", fill=TEXT_PRIMARY, font=get_font(True, 32))
    draw.text((2150, 570), "4K 60FPS · 视频小窗播放中", fill=ACCENT_CYAN, font=get_font(False, 24))

    # PiP Window Floating Toolbar (Top inside PiP)
    draw_rounded_rect(draw, [1730, 230, 2790, 310], radius=16, fill=(0, 0, 0, 180))
    draw.text((1760, 252), "庆余年2 - 18集", fill=TEXT_PRIMARY, font=get_font(True, 26))
    # PiP Buttons: Restore Fullscreen, Close
    draw_pill(draw, [2540, 245, 2660, 295], fill=(37, 99, 235))
    draw.text((2565, 255), "全屏 ⤢", fill=TEXT_PRIMARY, font=get_font(True, 20))
    draw_pill(draw, [2690, 245, 2770, 295], fill=(220, 38, 38))
    draw.text((2720, 255), "✕", fill=TEXT_PRIMARY, font=get_font(True, 22))

    # PiP Mini Playback Controls (Bottom inside PiP)
    draw_rounded_rect(draw, [1730, 830, 2790, 910], radius=16, fill=(0, 0, 0, 180))
    draw.text((1760, 855), "42:15 / 58:30", fill=TEXT_SECONDARY, font=get_font(False, 22))
    draw_pill(draw, [2250, 842, 2360, 898], fill=(37, 99, 235))
    draw.text((2285, 852), "⏸", fill=TEXT_PRIMARY, font=get_font(True, 24))
    draw_pill(draw, [2390, 842, 2530, 898], fill=(30, 42, 60))
    draw.text((2415, 855), "⏩ 15s", fill=ACCENT_CYAN, font=get_font(True, 20))
    draw_pill(draw, [2560, 842, 2760, 898], fill=(30, 42, 60))
    draw.text((2585, 855), "选集 (18/36)", fill=TEXT_SECONDARY, font=get_font(True, 20))

    # Bottom Explanatory Card (x: 80 ~ 2800 px, y: 1420 ~ 1560 px)
    draw_rounded_rect(draw, [80, 1420, 2800, 1560], radius=20, fill=(16, 24, 36, 240), outline=(45, 65, 95), width=2)
    draw.text((120, 1455), "💡 车载协同真需求说明：", fill=ACCENT_AMBER, font=get_font(True, 30))
    draw.text((450, 1458), "切出 TVBox 使用车机导航时，支持【后台纯音频不中断】或【画中画小窗悬浮】。副驾看剧、主驾看路互不干扰！", fill=TEXT_PRIMARY, font=get_font(False, 28))

    img.save(os.path.join(OUTPUT_DIR, "redesign-04-search.png")) # Using slot 4 for PiP scenario
    print("Generated redesign-04-search.png (PiP & Nav Scenario)")


# ----------------------------------------------------------------------
# MOCKUP 5: SMART SEARCH & MULTI-DIMENSIONAL FILTERS (智能搜索与分类筛选)
# ----------------------------------------------------------------------
def create_mockup_05_search_filter():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header
    draw_pill(draw, [60, 40, 220, 150], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 72), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    # Giant Search Bar with System Input Switcher
    draw_pill(draw, [250, 40, 2350, 150], fill=(24, 34, 52, 240), outline=ACCENT_CYAN, width=3)
    draw.text((320, 70), "🔍 庆余年|", fill=TEXT_PRIMARY, font=get_font(True, 40))

    draw_pill(draw, [1880, 55, 2310, 135], fill=(37, 99, 235))
    draw.text((1925, 75), "🎙 车载语音搜片", fill=TEXT_PRIMARY, font=get_font(True, 28))

    draw_pill(draw, [2390, 40, 2600, 150], fill=(16, 185, 129))
    draw.text((2445, 72), "搜索", fill=TEXT_PRIMARY, font=get_font(True, 32))

    draw_pill(draw, [2630, 40, 2820, 150], fill=(45, 55, 75))
    draw.text((2685, 72), "清空", fill=TEXT_SECONDARY, font=get_font(False, 28))

    # LEFT HALF: Multi-Dimensional Fast Filters (x: 60 ~ 1000 px, y: 190 ~ 1560 px)
    draw_rounded_rect(draw, [60, 190, 1000, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((100, 230), "多维快速筛选", fill=TEXT_PRIMARY, font=get_font(True, 34))

    filter_groups = [
        ("类 型：", ["全部", "剧情", "古装", "悬疑", "动作", "科幻"]),
        ("地 区：", ["全部", "大陆", "香港", "欧美", "日韩", "其他"]),
        ("年 份：", ["全部", "2024", "2023", "2022", "2020-2021", "更早"]),
        ("排 序：", ["最高评分", "最新上映", "最多播放", "最热追剧"])
    ]
    cur_gy = 310
    for g_title, g_items in filter_groups:
        draw.text((100, cur_gy), g_title, fill=TEXT_MUTED, font=get_font(True, 28))
        cur_ix = 230
        for idx, item in enumerate(g_items):
            iw = 110 if len(item) <= 2 else 150
            if idx == 0:
                draw_pill(draw, [cur_ix, cur_gy - 10, cur_ix + iw, cur_gy + 50], fill=ACCENT_CYAN)
                draw.text((cur_ix + 25, cur_gy + 5), item, fill=(10, 20, 35), font=get_font(True, 22))
            else:
                draw_pill(draw, [cur_ix, cur_gy - 10, cur_ix + iw, cur_gy + 50], fill=(30, 42, 60))
                draw.text((cur_ix + 25, cur_gy + 5), item, fill=TEXT_SECONDARY, font=get_font(False, 22))
            cur_ix += iw + 15
        cur_gy += 105

    # System Keyboard / Voice Prompt
    draw_rounded_rect(draw, [100, 780, 960, 1500], radius=24, fill=(26, 36, 54, 200), outline=(48, 65, 95), width=1)
    draw.text((140, 830), "💡 输入法与语音提示", fill=ACCENT_CYAN, font=get_font(True, 30))
    draw.text((140, 900), "• 原版 TV 6×6 虚拟键盘已移除，支持直接调用车机原生输入法；", fill=TEXT_PRIMARY, font=get_font(False, 26))
    draw.text((140, 970), "• 支持点击右上角【🎙 语音搜片】直接说出影视名称；", fill=TEXT_PRIMARY, font=get_font(False, 26))
    draw.text((140, 1040), "• 支持拼音首字母简拼（例如输入 QYN 秒搜《庆余年》）。", fill=TEXT_PRIMARY, font=get_font(False, 26))

    # RIGHT HALF: Rich Search Results Dual Column (x: 1040 ~ 2820 px, y: 190 ~ 1560 px)
    draw_rounded_rect(draw, [1040, 190, 2820, 1560], radius=28, fill=(22, 30, 44, 220), outline=(50, 65, 95), width=2)
    draw.text((1080, 230), "搜索结果 (联想匹配到 5 部热门作品)：", fill=ACCENT_CYAN, font=get_font(True, 32))

    search_cards = [
        ("庆余年 第二季 (2024)", "36集全 · 4K 臻彩超清 · 张若昀 / 陈道明", "★ 8.8", "立即播放 ▶"),
        ("庆余年 第一季 (2019)", "46集全 · 蓝光1080P · 经典重温", "★ 8.0", "立即播放 ▶"),
        ("庆余年 特别篇 / 花絮", "12个片段 · 原画超清 · 幕后专访", "★ 8.2", "查看详情"),
        ("周处除三害 (2024)", "电影 · 4K HDR · 阮经天", "★ 8.5", "立即播放 ▶"),
        ("大理寺少卿游 (2024)", "36集全 · 古装探案超清", "★ 8.2", "立即播放 ▶")
    ]
    cur_cy = 310
    for r_title, r_desc, r_score, r_btn in search_cards:
        draw_rounded_rect(draw, [1080, cur_cy, 2780, cur_cy + 210], radius=22, fill=(28, 38, 56, 230), outline=(55, 75, 105), width=2)
        # Poster mockup
        draw.rectangle([1100, cur_cy + 20, 1220, cur_cy + 190], fill=(40, 60, 90))
        draw.text((1125, cur_cy + 85), "海报", fill=TEXT_MUTED, font=get_font(True, 26))

        draw.text((1260, cur_cy + 40), r_title, fill=TEXT_PRIMARY, font=get_font(True, 36))
        draw.text((1260, cur_cy + 105), r_desc, fill=TEXT_SECONDARY, font=get_font(False, 26))
        draw.text((1260, cur_cy + 155), f"评分：{r_score}  |  专线 32ms 低延迟", fill=ACCENT_AMBER, font=get_font(True, 22))

        draw_pill(draw, [2500, cur_cy + 60, 2740, cur_cy + 160], fill=(16, 185, 129))
        draw.text((2530, cur_cy + 92), r_btn, fill=TEXT_PRIMARY, font=get_font(True, 26))
        cur_cy += 240

    img.save(os.path.join(OUTPUT_DIR, "redesign-05-settings.png")) # Using slot 5
    print("Generated redesign-05-settings.png (Search & Filter Scenario)")


# ----------------------------------------------------------------------
# MOCKUP 6: STREAMLINED PRACTICAL MEDIA SETTINGS (精简实用型设置中心)
# ----------------------------------------------------------------------
def create_mockup_06_settings():
    img = Image.new("RGBA", (2880, 1620), BG_COLOR)
    draw = ImageDraw.Draw(img, "RGBA")

    # Header
    draw_pill(draw, [60, 40, 220, 130], fill=(28, 36, 50, 220), outline=ACCENT_CYAN, width=2)
    draw.text((95, 62), "← 返回", fill=TEXT_PRIMARY, font=get_font(True, 30))

    draw.text((260, 60), "播放器设置中心  /  核心解码与音画质调优", fill=TEXT_PRIMARY, font=get_font(True, 36))

    # LEFT COLUMN: Category Navigation Rail (x: 60 ~ 680 px, y: 160 ~ 1560 px)
    draw_rounded_rect(draw, [60, 160, 680, 1560], radius=28, fill=(20, 28, 40, 240), outline=(50, 65, 90), width=2)

    settings_cats = [
        ("⚙ 播放核心与后台行为", True),
        ("🎨 画面色彩与 SGSR1 超分", False),
        ("🔊 杜比座舱音效设置", False),
        ("👆 触控手势与长按倍速", False),
        ("📦 订阅源与缓存管理", False),
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

    # RIGHT COLUMN: Practical Grouped Setting Cards (x: 720 ~ 2820 px, y: 160 ~ 1560 px)
    # CARD 1: Background & PiP Playback (真需求设置项)
    draw_rounded_rect(draw, [720, 160, 2820, 520], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 200), "切后台播放行为 (切出车机看导航时)", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 250), "决定当您按下 Home 键返回车机主界面或打开车机地图导航时，视频播放器的响应方式", fill=TEXT_MUTED, font=get_font(False, 26))

    bg_options = [
        ("保持后台播放音频 (推荐，导航与声音混音)", True),
        ("自动弹出悬浮画中画小窗 (边看导航边看剧)", False),
        ("切出即暂停播放", False)
    ]
    cur_bx = 760
    for b_title, b_act in bg_options:
        bw = 640
        if b_act:
            draw_pill(draw, [cur_bx, 330, cur_bx + bw, 440], fill=ACCENT_CYAN)
            draw.text((cur_bx + 35, 368), b_title, fill=(10, 20, 35), font=get_font(True, 24))
        else:
            draw_pill(draw, [cur_bx, 330, cur_bx + bw, 440], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
            draw.text((cur_bx + 35, 368), b_title, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_bx += bw + 20

    # CARD 2: Decoder Kernel (播放核心)
    draw_rounded_rect(draw, [720, 550, 2820, 890], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 590), "视频解码核心与渲染器", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 640), "高通骁龙芯片推荐使用 IJK 硬解 + GlEnhanceRenderView 以支持 SGSR1 超分辨率算法", fill=TEXT_MUTED, font=get_font(False, 26))

    dec_options = [
        ("IJK 硬件解码 (推荐，支持 SGSR1 与臻彩)", True),
        ("ExoPlayer 核心 (通用兼容)", False),
        ("系统原生 MediaPlayer (低配备用)", False)
    ]
    cur_dx = 760
    for d_title, d_act in dec_options:
        dw = 640
        if d_act:
            draw_pill(draw, [cur_dx, 710, cur_dx + dw, 820], fill=ACCENT_BLUE)
            draw.text((cur_dx + 40, 748), d_title, fill=TEXT_PRIMARY, font=get_font(True, 24))
        else:
            draw_pill(draw, [cur_dx, 710, cur_dx + dw, 820], fill=(30, 42, 60), outline=(60, 75, 100), width=2)
            draw.text((cur_dx + 40, 748), d_title, fill=TEXT_SECONDARY, font=get_font(False, 24))
        cur_dx += dw + 20

    # CARD 3: Touch Gestures & Long Press Speed (长按倍速与手势调节)
    draw_rounded_rect(draw, [720, 920, 2820, 1550], radius=26, fill=(24, 34, 50, 220), outline=(55, 75, 105), width=2)
    draw.text((760, 960), "17.3 英寸触控交互与长按倍速", fill=TEXT_PRIMARY, font=get_font(True, 34))
    draw.text((760, 1010), "全屏手势参数与颠簸防误触配置", fill=TEXT_MUTED, font=get_font(False, 26))

    gesture_switches = [
        ("全屏长按触发极速快进：长按屏幕任意位置触发 2.0x 播放，松手恢复", True),
        ("左右分屏垂直滑动手势：左侧滑调应用亮度，右侧滑调媒体音量", True),
        ("双击全屏快速暂停/继续：防误触过滤（需手指双击间隔小于 300ms）", True),
        ("横向滑动精准进度 Seek：显示微缩时间气泡，松手前可随时回退", True)
    ]
    cur_gy = 1080
    for g_title, g_val in gesture_switches:
        draw.text((760, cur_gy + 20), g_title, fill=TEXT_PRIMARY, font=get_font(False, 28))
        if g_val:
            draw_pill(draw, [2540, cur_gy + 5, 2760, cur_gy + 75], fill=SUCCESS_GREEN)
            draw.ellipse([2670, cur_gy + 10, 2750, cur_gy + 70], fill=(255, 255, 255))
            draw.text((2565, cur_gy + 22), "已开启", fill=TEXT_PRIMARY, font=get_font(True, 22))
        cur_gy += 110

    img.save(os.path.join(OUTPUT_DIR, "redesign-06-screenoff-audio.png")) # Using slot 6
    print("Generated redesign-06-screenoff-audio.png (Settings Scenario)")


# ----------------------------------------------------------------------
# GENERATE 6 BEFORE VS AFTER COMPARISON COMPOSITES
# ----------------------------------------------------------------------
def generate_comparison_card(before_path, after_path, out_path, title_text, problem_desc, solution_desc):
    comp_w, comp_h = 2880, 1800
    comp = Image.new("RGBA", (comp_w, comp_h), (12, 16, 24))
    draw = ImageDraw.Draw(comp, "RGBA")

    # Header
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
    draw.text((1515, 1030), "✨ 17.3寸 3K 视频播放器优化建议要点", fill=(52, 211, 153), font=get_font(True, 34))
    cur_sy = 1100
    for line in solution_desc:
        draw.text((1515, cur_sy), line, fill=TEXT_PRIMARY, font=get_font(False, 26))
        cur_sy += 55

    comp.save(out_path)
    print(f"Generated comparison: {out_path}")


def main():
    print("Generating 6 Refined High-Fidelity 3K Video Player Mockups...")
    create_mockup_01_home()
    create_mockup_02_detail()
    create_mockup_03_player()
    create_mockup_04_pip_navigation()
    create_mockup_05_search_filter()
    create_mockup_06_settings()

    print("\nGenerating 6 Refined Side-by-Side Comparison Composites...")

    # 1. Home
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-home.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-01-home.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-01-home.png"),
        title_text="01. 主界面流媒体中心与无缝续播设计 (Home & Media Center)",
        problem_desc=[
            "• 设置按钮被排在屏幕最右侧边缘 (x: 2302px / 30.6cm)，驾驶位伸手极其困难；",
            "• 顶部日期独占 1918px (25.5cm) 宽，信息利用率低下，属于电视远视距残余；",
            "• 分类 Tab 条高度仅 101px (13.4mm)，手指滑动极易误触；",
            "• 缺乏快速继续播放卡片，每次打开应用找上次未看完的剧集链路过长；",
            "• 存在刺眼的电视白色聚焦框，触摸屏缺少平滑按压反馈。"
        ],
        solution_desc=[
            "• 设立【左侧垂直流媒体导航 Rail】(x: 0~220px)，设置/影视/直播触手可及；",
            "• 新增【上次播放无缝续播 Hero 卡片】，直观展示剧集进度与【继续播放】大按键；",
            "• 顶部集成搜索栏与分类快速过滤标签（电影/剧集/4K HDR/动漫）；",
            "• 海报触控卡片重构为 480×720px 大规格，彻底废除 TV 焦点白框；",
            "• 深空灰蓝玻璃态微拟物设计，白天高清晰防反光，夜间护眼不刺眼。"
        ]
    )

    # 2. Detail
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-detail.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-02-detail.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-02-detail.png"),
        title_text="02. 影片详情与多源智能测速切源 (Detail & Smart Sources)",
        problem_desc=[
            "• 全屏、快速搜索、收藏、换源等按键全部沦陷在右侧 (x: 1600~2700px)；",
            "• 界面中央直接暴露 1552px 长的未格式化原始 HTTP play.m3u8 字符串；",
            "• 存在 previewPlayerBlock 遥控器阻断假层，阻碍小窗直接点击全屏；",
            "• 剧集列表下边距仅 1mm，长剧集反复滑动极度繁琐；",
            "• 线路切源全盲，不知道哪条线路延迟低、播放顺畅。"
        ],
        solution_desc=[
            "• 黄金岛按键群【全屏播放】、【智能切源】、【收藏】、【画中画】移至左侧近端；",
            "• 隐藏原始 URL 字符串，重构为整洁优雅的演职员、评分与剧情简介卡片；",
            "• 引入【多源智能测速】：显示各线路延迟（如专线 32ms 最快），一键选优；",
            "• 剧集重构为 155×135px 超大方块矩阵，带 22px 充足安全间距与正在播放光标；",
            "• 提供 1-10、11-20、21-30 分段快速跳选胶囊，长剧选集秒直达。"
        ]
    )

    # 3. Player
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-player-controls-enabled.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-03-player.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-03-player.png"),
        title_text="03. 全屏播放器与现代触控播控条 (Fullscreen Modern Player)",
        problem_desc=[
            "• 底部平铺 18 个细碎小按钮，单键宽 17.6mm，间距仅 3.6mm，极易误触；",
            "• 时间显示框宽度仅 22mm，长视频时间戳 (01:23:45) 发生严重截断；",
            "• 右侧音效胶囊落在 x: 2120px，主驾必须欠身；",
            "• 缺乏屏幕触控长按倍速与滑动手势，调音量调亮度必须费力点小按钮；",
            "• 电视快进黑色弹窗居中遮挡视频主体画面。"
        ],
        solution_desc=[
            "• 打造左下角 5 大高容错播控岛：暂停/播放、±15s 快进退、臻彩、杜比音效；",
            "• 进度条加粗至 20px，配合 46px 吸附手柄与放大的 42:15 / 58:30 无截断时间气泡；",
            "• 新增【长按屏幕 2.0x 极速快进】：手指长按即加速、松手恢复，体验极佳；",
            "• 建立车载盲操手势：左侧垂直滑调亮度、右侧垂直滑调音量、双击暂停；",
            "• 右下角折叠收拢倍速选择、选集列表、画面比例（适应/填充/21:9 无黑边）。"
        ]
    )

    # 4. PiP & Nav
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-player-controls.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-04-search.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-04-search.png"),
        title_text="04. 车载真实高频协同：后台播放与悬浮画中画 (Background Play & PiP)",
        problem_desc=[
            "• 原版切出应用后播放器直接休眠断音，无法边开导航边听剧集音频；",
            "• 缺乏画中画 (PiP) 悬浮窗能力，不能把视频缩小浮在车机导航地图上方；",
            "• 盲目熄灭 17.3 寸大屏无法看路况地图，属于脱离用车实际的伪需求；",
            "• 车机大屏优势未发挥，在多任务与导航协同场景下体验割裂。"
        ],
        solution_desc=[
            "• 支持【后台音频不间断播放】：切出看高德/百度导航时，剧集声音平稳混音输出；",
            "• 支持【悬浮画中画 (PiP)】：视频缩小为可拖动小窗浮在导航上方；",
            "• 副驾追剧娱乐、主驾看路况导航两不相误，完美解决车载大屏多任务痛点；",
            "• 画中画小窗带轻量播控（暂停、关闭、放大还原、±15s 快进）。"
        ]
    )

    # 5. Search & Filters
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-search.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-05-settings.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-05-settings.png"),
        title_text="05. 智能搜索与多维分类筛选器 (Smart Search & Filters)",
        problem_desc=[
            "• 强塞 36 键 TV 虚拟键盘，且代码主动调用 hiddenImm() 封杀原生软键盘与语音；",
            "• 右半屏 50% 面积 (18.5cm) 全黑空白，大屏显示空间严重浪费；",
            "• 单列纵向热词排至 y: 1934px 跌出屏幕底端，查看成本极高；",
            "• 残留电视局域网扫码推流搜索框，在自带网络车机上毫无使用场景。"
        ],
        solution_desc=[
            "• 搜索栏集成【车载语音搜片】大按键，支持点击直接说出片名；",
            "• 彻底废除 TV 字母软键盘，开放调用 Android 原生输入法与车机大手写板；",
            "• 左侧重构为【多维快速分类筛选滑轨】（类型、地区、年份、热度排序）；",
            "• 右侧重构为双列富媒体搜索结果卡片，单卡直接带【立即播放】按键，搜完即播。"
        ]
    )

    # 6. Streamlined Settings
    generate_comparison_card(
        before_path="car/evidence/car.7/r2-B2EEDC14/pages/page-settings-top.png",
        after_path=os.path.join(OUTPUT_DIR, "redesign-06-screenoff-audio.png"),
        out_path=os.path.join(COMPARE_DIR, "compare-06-audio-mode.png"),
        title_text="06. 精简实用型媒体设置中心 (Streamlined Media Settings)",
        problem_desc=[
            "• 左侧分类导航菜单被代码写死 visibility='gone'，40+ 设置项只能单列硬滑；",
            "• 高级设置项通栏排布撕裂：标题在 x: 124px，开关在 x: 2576px，视距拉扯 32cm；",
            "• 充满大量 Webview 解析、DNS 聚合、M3U8 规则等未分层的底层技术参数；",
            "• 缺乏关于切后台播放、画中画悬浮窗、长按倍速触发等实用设置项。"
        ],
        solution_desc=[
            "• 恢复左侧垂直分类导航栏 (x: 60~680px)，清晰划分为 4 大实用业务板块；",
            "• 增加【切后台播放行为选择】：支持后台音频播放、自动弹出画中画、切出暂停；",
            "• 紧凑成组卡片排布，消除 32cm 视距撕裂，开关紧挨配置说明右侧；",
            "• 增加【长按倍速速率设置】（2.0x / 3.0x）与全屏手势开关；",
            "• 深度收敛折叠极客底层参数，界面专为大屏视频观影服务。"
        ]
    )

    print("\nAll 6 Refined Mockups and Comparisons successfully generated!")

if __name__ == "__main__":
    main()
