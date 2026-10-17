"""
PlantCare — Store Listing Asset Generator  (v3 — Pro + Freemium)
Generates: icon 512×512, feature graphic 1024×500, 8 screenshots 1080×1920

Each screenshot mimics the actual app UI:
  • Material status bar + app bar + bottom nav
  • Realistic plant names, dates, and care data
  • Material Design cards with rounded corners
  • App colour scheme: #1B5E20 / #2E7D32 / #3D8B6B / #66BB6A

Run: py generate_assets.py
Requires: pip install pillow
"""
from PIL import Image, ImageDraw, ImageFont
import os, math

# ── Output paths ──────────────────────────────────────────────────────────────
OUT_ICON    = "graphics/icon_512.png"
OUT_FEATURE = "graphics/feature_graphic_1024x500.png"
SCREEN_DIR  = "screenshots"

# ── Brand colours ─────────────────────────────────────────────────────────────
C_GREEN_DARK   = (27,  94,  32)   # #1B5E20  toolbar / status bar
C_GREEN_MID    = (46, 125,  50)   # #2E7D32  primary
C_GREEN_TEAL   = (61, 139, 107)   # #3D8B6B  accent / FAB
C_GREEN_LIGHT  = (102,187,106)    # #66BB6A  active indicators
C_GREEN_PALE   = (200,230,201)    # #C8E6C9  chip bg / dot
C_WHITE        = (255,255,255)
C_SURFACE      = (250,252,250)    # card / page background
C_TEXT_PRI     = (27,  46,  34)   # #1B2E22
C_TEXT_SEC     = (107,126,114)    # #6B7E72
C_DIVIDER      = (224,232,226)
C_CHIP_ACTIVE  = (200,245,200)
C_RED          = (211, 47, 47)
C_BLUE         = ( 30,136,229)
C_AMBER        = (255,160,  0)

W, H = 1080, 1920


# ── Helpers ───────────────────────────────────────────────────────────────────

def get_font(size, bold=False):
    candidates_bold   = ["arialbd.ttf","Arial Bold.ttf","DejaVuSans-Bold.ttf","NotoSans-Bold.ttf"]
    candidates_normal = ["arial.ttf","Arial.ttf","DejaVuSans.ttf","NotoSans-Regular.ttf"]
    candidates = candidates_bold if bold else candidates_normal
    dirs = ["C:/Windows/Fonts/","/usr/share/fonts/truetype/dejavu/","/usr/share/fonts/"]
    for d in dirs:
        for name in candidates:
            p = os.path.join(d, name)
            if os.path.exists(p):
                try:
                    return ImageFont.truetype(p, size)
                except Exception:
                    pass
    return ImageFont.load_default()


def canvas():
    img = Image.new("RGB", (W, H), C_SURFACE)
    return img, ImageDraw.Draw(img)


def draw_status_bar(draw, y=0):
    draw.rectangle([0, y, W, y+72], fill=C_GREEN_DARK)
    draw.text((50, y+18), "9:41", font=get_font(32, bold=True), fill=C_WHITE)
    # battery + signal icons (text approximation)
    draw.text((W-230, y+18), "▌▌▌  ▰▰▰▰ ⬛", font=get_font(26), fill=C_WHITE)


def draw_app_bar(draw, title, y=72, with_back=False, subtitle=None):
    draw.rectangle([0, y, W, y+128], fill=C_GREEN_MID)
    tx = 120 if with_back else 50
    if with_back:
        draw.text((50, y+36), "←", font=get_font(50, bold=True), fill=C_WHITE)
    draw.text((tx, y+22 if subtitle else y+36), title,
              font=get_font(50 if subtitle else 52, bold=True), fill=C_WHITE)
    if subtitle:
        draw.text((tx, y+82), subtitle, font=get_font(30), fill=C_GREEN_PALE)


def draw_bottom_nav(draw, active_idx):
    """4-tab bottom navigation bar."""
    nav_y = H - 160
    draw.rectangle([0, nav_y, W, H], fill=C_WHITE)
    draw.line([(0, nav_y), (W, nav_y)], fill=C_DIVIDER, width=2)
    tabs = [("🌿", "Alle"), ("🌱", "Meine"), ("📅", "Kalender"), ("☀️", "Heute")]
    tab_w = W // 4
    for i, (icon, label) in enumerate(tabs):
        cx = i * tab_w + tab_w // 2
        col = C_GREEN_MID if i == active_idx else C_TEXT_SEC
        draw.text((cx - 26, nav_y + 14), icon, font=get_font(48), fill=col)
        draw.text((cx - len(label)*9, nav_y + 74), label, font=get_font(28), fill=col)
        if i == active_idx:
            draw.rounded_rectangle([cx-60, nav_y+6, cx+60, nav_y+62],
                                   radius=28, fill=C_CHIP_ACTIVE)
            draw.text((cx - 26, nav_y + 14), icon, font=get_font(48), fill=C_GREEN_MID)


def card(draw, x, y, w, h, radius=24, bg=C_WHITE, shadow=True):
    if shadow:
        draw.rounded_rectangle([x+4, y+4, x+w+4, y+h+4], radius=radius,
                               fill=(200,210,202))
    draw.rounded_rectangle([x, y, x+w, y+h], radius=radius, fill=bg)


def plant_avatar(draw, cx, cy, r, color=C_GREEN_TEAL, letter="M"):
    draw.ellipse([cx-r, cy-r, cx+r, cy+r], fill=color)
    draw.text((cx - r//2 + 4, cy - r//2 + 2), letter,
              font=get_font(r, bold=True), fill=C_WHITE)


def water_chip(draw, x, y, text, color=C_BLUE):
    tw = len(text) * 17 + 30
    draw.rounded_rectangle([x, y, x+tw, y+42], radius=21, fill=color+(50,) if len(color)==3 else color)
    draw.rounded_rectangle([x, y, x+tw, y+42], radius=21, outline=color, width=2)
    draw.text((x+14, y+8), text, font=get_font(26), fill=color)


# ── Icon ─────────────────────────────────────────────────────────────────────

def make_icon():
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    r = 110
    col = C_GREEN_MID
    draw.rectangle([r, 0, size-r, size], fill=col)
    draw.rectangle([0, r, size, size-r], fill=col)
    for corner in [(0,0),(size-2*r,0),(0,size-2*r),(size-2*r,size-2*r)]:
        draw.ellipse([corner[0], corner[1], corner[0]+2*r, corner[1]+2*r], fill=col)
    # leaf illustration
    cx, cy = size//2, size//2+10
    s = 150
    draw.ellipse([cx-s*.45, cy-s*.25, cx-s*.05, cy+s*.05], fill=C_GREEN_PALE)
    draw.ellipse([cx+s*.05, cy-s*.35, cx+s*.45, cy-s*.05], fill=C_GREEN_PALE)
    draw.ellipse([cx-s*.2, cy-s*.65, cx+s*.2, cy-s*.25], fill=C_WHITE)
    draw.line([(cx, cy+s*.45), (cx, cy-s*.1)], fill=C_WHITE, width=max(3, s//18))
    pt = cy+s*.35
    pb = cy+s*.55
    draw.polygon([(cx-s*.28,pt),(cx+s*.28,pt),(cx+s*.23,pb),(cx-s*.23,pb)], fill=C_WHITE)
    draw.rectangle([cx-s*.32, pt-s*.04, cx+s*.32, pt+s*.06], fill=C_WHITE)
    img.save(OUT_ICON, "PNG")
    print(f"  OK {OUT_ICON}")


# ── Feature Graphic ───────────────────────────────────────────────────────────

def make_feature():
    fw, fh = 1024, 500
    img = Image.new("RGB", (fw, fh), C_GREEN_DARK)
    draw = ImageDraw.Draw(img)
    for y in range(fh):
        t = y / fh
        r = int(C_GREEN_DARK[0]*(1-t) + C_GREEN_MID[0]*t)
        g = int(C_GREEN_DARK[1]*(1-t) + C_GREEN_MID[1]*t)
        b = int(C_GREEN_DARK[2]*(1-t) + C_GREEN_MID[2]*t)
        draw.line([(0, y), (fw, y)], fill=(r, g, b))
    draw.ellipse([fw-280, -80, fw+80, 280], fill=C_GREEN_MID)
    draw.ellipse([fw-230, -30, fw+30, 230], fill=C_GREEN_DARK)
    draw.ellipse([-60, fh-180, 180, fh+60], fill=C_GREEN_MID)
    # Mock phone
    px, py = fw-260, 50
    draw.rounded_rectangle([px, py, px+180, py+380], radius=24, fill=(40,110,44))
    draw.rounded_rectangle([px+10, py+30, px+170, py+360], radius=14, fill=C_SURFACE)
    draw.rectangle([px+10, py+30, px+170, py+60], fill=C_GREEN_DARK)
    draw.text((px+20, py+36), "PlantCare", font=get_font(22, bold=True), fill=C_WHITE)
    for i, name in enumerate(["Monstera", "Ficus", "Orchidee"]):
        cy2 = py + 80 + i * 80
        draw.rounded_rectangle([px+16, cy2, px+164, cy2+64], radius=10, fill=C_WHITE)
        draw.ellipse([px+24, cy2+10, px+56, cy2+54], fill=C_GREEN_LIGHT)
        draw.text((px+64, cy2+12), name, font=get_font(18, bold=True), fill=C_TEXT_PRI)
        draw.text((px+64, cy2+38), "alle 7 Tage", font=get_font(14), fill=C_TEXT_SEC)
    # Text
    draw.text((55, 110), "PlantCare", font=get_font(96, bold=True), fill=C_WHITE)
    draw.text((58, 220), "Dein Pflanzenassistent", font=get_font(38), fill=C_GREEN_PALE)
    draw.text((58, 278), "Pflege · Erinnerungen · KI", font=get_font(30), fill=C_GREEN_LIGHT)
    draw.rounded_rectangle([58, 340, 310, 390], radius=24, fill=C_GREEN_LIGHT)
    draw.text((78, 352), "Kostenlos & Pro", font=get_font(26, bold=True), fill=C_GREEN_DARK)
    img.save(OUT_FEATURE, "PNG")
    print(f"  OK {OUT_FEATURE}")


# ── Screens ───────────────────────────────────────────────────────────────────

PLANTS = [
    ("Monstera",      "Fensterblatt",   "M", C_GREEN_TEAL,  7,  "Zimmer"),
    ("Ficus",         "Feigenbaum",     "F", (121,85,72),   10, "Wohnzimmer"),
    ("Orchidee",      "Phalaenopsis",   "O", (156,39,176),  14, "Schlafzimmer"),
    ("Basilikum",     "Ocimum basilicum","B", C_GREEN_MID,  3,  "Küche"),
    ("Aloe Vera",     "Sukkulente",     "A", C_AMBER,       21, "Balkon"),
    ("Pothos",        "Efeutute",       "P", (0,150,136),    7, "Bad"),
    ("Kaktus",        "Cereus",         "K", (121,85,72),   30, "Büro"),
    ("Lavendel",      "Lavandula",      "L", (94,53,177),    7, "Garten"),
]


def screen_alle_pflanzen():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "PlantCare", 72)
    # Search bar
    draw.rounded_rectangle([40, 218, W-40, 298], radius=40, fill=C_WHITE,
                           outline=C_DIVIDER, width=2)
    draw.text((80, 232), "🔍  Pflanzen suchen…", font=get_font(34), fill=C_TEXT_SEC)
    # Category chips
    chips = [("Alle",C_GREEN_MID,C_WHITE),("Zimmer",C_CHIP_ACTIVE,C_GREEN_DARK),
             ("Außen",C_CHIP_ACTIVE,C_GREEN_DARK),("Kräuter",C_CHIP_ACTIVE,C_GREEN_DARK)]
    cx = 40
    for label, bg, fg in chips:
        tw = len(label)*20 + 44
        draw.rounded_rectangle([cx, 320, cx+tw, 372], radius=26,
                               fill=bg, outline=C_GREEN_PALE, width=1)
        draw.text((cx+20, 330), label, font=get_font(30), fill=fg)
        cx += tw + 20
    # Plant grid (2 columns)
    col_w = (W-120)//2
    for i, (name, latin, letter, color, interval, loc) in enumerate(PLANTS[:6]):
        col = i % 2
        row = i // 2
        x = 40 + col*(col_w+40)
        y = 400 + row*280
        card(draw, x, y, col_w, 260, radius=20)
        draw.rounded_rectangle([x+10, y+10, x+col_w-10, y+140], radius=16, fill=color)
        draw.text((x+col_w//2-20, y+52), letter, font=get_font(80,bold=True), fill=C_WHITE)
        draw.text((x+16, y+154), name, font=get_font(32, bold=True), fill=C_TEXT_PRI)
        draw.text((x+16, y+196), f"💧 alle {interval} Tage", font=get_font(26), fill=C_TEXT_SEC)
        draw.text((x+16, y+228), latin, font=get_font(22), fill=C_TEXT_SEC)
    draw_bottom_nav(draw, 0)
    save_screen(img, "01_alle_pflanzen")


def screen_meine_pflanzen():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Meine Pflanzen", 72, subtitle=f"{len(PLANTS[:4])} Pflanzen")
    y0 = 230
    for i, (name, latin, letter, color, interval, loc) in enumerate(PLANTS[:4]):
        card(draw, 40, y0, W-80, 160, radius=20)
        plant_avatar(draw, 110, y0+80, 60, color, letter)
        draw.text((200, y0+24), name, font=get_font(38, bold=True), fill=C_TEXT_PRI)
        draw.text((200, y0+74), loc, font=get_font(28), fill=C_TEXT_SEC)
        draw.text((200, y0+110), f"💧 alle {interval} Tage  |  {latin}", font=get_font(24), fill=C_TEXT_SEC)
        # heart
        draw.text((W-120, y0+56), "♥" if i % 2 == 0 else "♡",
                  font=get_font(48), fill=C_GREEN_LIGHT if i%2==0 else C_TEXT_SEC)
        y0 += 180
    # FAB
    draw.ellipse([W-160, H-280, W-40, H-160], fill=C_GREEN_TEAL)
    draw.text((W-122, H-248), "+", font=get_font(72, bold=True), fill=C_WHITE)
    draw_bottom_nav(draw, 1)
    save_screen(img, "02_meine_pflanzen")


def screen_kalender():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Pflege-Kalender", 72, subtitle="April 2026")
    # Month grid header
    days_hdr = ["Mo","Di","Mi","Do","Fr","Sa","So"]
    col_w2 = (W-80)//7
    for i, d in enumerate(days_hdr):
        draw.text((40 + i*col_w2 + col_w2//2 - 18, 230), d,
                  font=get_font(28, bold=True), fill=C_TEXT_SEC)
    # Calendar days (6 weeks, April 2026 starts on Wed = index 2)
    start_offset = 2
    day = 1
    for row in range(6):
        for col in range(7):
            idx = row*7 + col
            if idx < start_offset or day > 30:
                continue
            cx2 = 40 + col*col_w2 + col_w2//2
            cy2 = 290 + row*110 + 44
            today = day == 26
            if today:
                draw.ellipse([cx2-38, cy2-38, cx2+38, cy2+38], fill=C_GREEN_MID)
                draw.text((cx2-14, cy2-20), str(day), font=get_font(34, bold=True), fill=C_WHITE)
            else:
                draw.text((cx2-14, cy2-20), str(day), font=get_font(34), fill=C_TEXT_PRI)
            # care dots
            if day in (3,7,10,14,17,21,24,28):
                draw.ellipse([cx2-8, cy2+18, cx2+8, cy2+34], fill=C_GREEN_MID)
            if day in (5,12,19,26):
                draw.ellipse([cx2+6, cy2+18, cx2+22, cy2+34], fill=C_AMBER)
            day += 1
    # Today's reminders at bottom
    draw.text((50, 980), "Heute · 26. April", font=get_font(34, bold=True), fill=C_TEXT_PRI)
    for i, (name, _, _, color, _, _) in enumerate(PLANTS[:2]):
        card(draw, 40, 1030+i*150, W-80, 130, radius=18)
        plant_avatar(draw, 100, 1030+i*150+65, 46, color, name[0])
        draw.text((170, 1030+i*150+22), name, font=get_font(34, bold=True), fill=C_TEXT_PRI)
        draw.text((170, 1030+i*150+68), "💧 Gießen fällig", font=get_font(28), fill=C_TEXT_SEC)
        draw.rounded_rectangle([W-200, 1030+i*150+40, W-60, 1030+i*150+90],
                               radius=20, fill=C_GREEN_PALE)
        draw.text((W-190, 1030+i*150+50), "Erledigt", font=get_font(26), fill=C_GREEN_DARK)
    draw_bottom_nav(draw, 2)
    save_screen(img, "03_kalender")


def screen_heute():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Heute", 72, subtitle="Montag, 26. April 2026")
    # Summary chip row
    chips2 = [(f"{3} fällig", C_RED),(f"2 erledigt", C_GREEN_TEAL),(f"1 überfällig", C_AMBER)]
    cx3 = 40
    for label, col2 in chips2:
        tw = len(label)*19 + 40
        draw.rounded_rectangle([cx3, 230, cx3+tw, 282], radius=26, fill=col2)
        draw.text((cx3+18, 240), label, font=get_font(28, bold=True), fill=C_WHITE)
        cx3 += tw + 20
    draw.text((50, 310), "Fällig heute", font=get_font(36, bold=True), fill=C_TEXT_PRI)
    for i, (name, _, letter, color, _, loc) in enumerate(PLANTS[:3]):
        y2 = 370 + i*185
        card(draw, 40, y2, W-80, 165, radius=20)
        plant_avatar(draw, 108, y2+82, 58, color, letter)
        draw.text((190, y2+22), name, font=get_font(38, bold=True), fill=C_TEXT_PRI)
        draw.text((190, y2+70), f"💧 Gießen  –  {loc}", font=get_font(28), fill=C_TEXT_SEC)
        draw.text((190, y2+108), "Heute fällig", font=get_font(26), fill=C_GREEN_DARK)
        # Checkbox
        done = i == 1
        box_col = C_GREEN_TEAL if done else C_DIVIDER
        draw.rounded_rectangle([W-130, y2+56, W-50, y2+116], radius=16,
                               fill=box_col, outline=box_col, width=2)
        if done:
            draw.text((W-120, y2+64), "✓", font=get_font(42, bold=True), fill=C_WHITE)
    # Streak banner
    card(draw, 40, H-330, W-80, 130, radius=20, bg=C_GREEN_PALE)
    draw.text((80, H-310), "🔥  7-Tage-Pflegeserie aktiv!",
              font=get_font(34, bold=True), fill=C_GREEN_DARK)
    draw.text((80, H-258), "Heute gießen, um die Serie zu halten.",
              font=get_font(26), fill=C_TEXT_PRI)
    draw_bottom_nav(draw, 3)
    save_screen(img, "04_heute")


def screen_detail():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Monstera", 72, with_back=True, subtitle="Fensterblatt · Zimmer")
    # Hero photo area
    draw.rounded_rectangle([40, 225, W-40, 620], radius=24, fill=C_GREEN_TEAL)
    draw.text((W//2-90, 350), "🌿", font=get_font(160), fill=C_WHITE)
    # Chip row
    for j, (icon, txt) in enumerate([("💧","7 Tage"),("☀️","Hell"),("🌡️","18–24°")]):
        cx4 = 60 + j * 320
        card(draw, cx4, 640, 290, 100, radius=20)
        draw.text((cx4+20, 648), icon, font=get_font(36), fill=C_GREEN_DARK)
        draw.text((cx4+72, 652), txt, font=get_font(32, bold=True), fill=C_TEXT_PRI)
        draw.text((cx4+72, 692), ["Gießen","Licht","Temp."][j], font=get_font(24), fill=C_TEXT_SEC)
    # Care info cards
    sections = [
        ("💧 Bewässerung", "Alle 7 Tage gründlich gießen. Im Winter seltener."),
        ("☀️ Standort",    "Helles, indirektes Licht. Keine direkte Sonne."),
        ("🌱 Boden",       "Humusreiche, durchlässige Erde. pH 5,5–7."),
        ("✂️ Düngen",      "März–September alle 4 Wochen mit Grünpflanzendünger."),
    ]
    y3 = 765
    for title, text in sections:
        card(draw, 40, y3, W-80, 118, radius=18)
        draw.text((76, y3+14), title, font=get_font(30, bold=True), fill=C_TEXT_PRI)
        draw.text((76, y3+56), text, font=get_font(26), fill=C_TEXT_SEC)
        y3 += 136
    # Action buttons
    for j2, (label, col3) in enumerate([("💧 Jetzt gießen", C_GREEN_TEAL),
                                        ("📷 Foto hinzufügen", C_GREEN_MID)]):
        x4 = 40 + j2*530
        draw.rounded_rectangle([x4, H-250, x4+490, H-170], radius=28, fill=col3)
        draw.text((x4+30, H-232), label, font=get_font(30, bold=True), fill=C_WHITE)
    save_screen(img, "05_detail")


def screen_erinnerungen():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Erinnerungen", 72, subtitle="Diese Woche")
    # Week strip
    week = ["Mo\n21","Di\n22","Mi\n23","Do\n24","Fr\n25","Sa\n26","So\n27"]
    ww = (W-80)//7
    for i, d in enumerate(week):
        wx = 40 + i*ww
        today2 = i == 5
        bg2 = C_GREEN_MID if today2 else C_WHITE
        draw.rounded_rectangle([wx+4, 218, wx+ww-4, 310], radius=20,
                               fill=bg2, outline=C_DIVIDER, width=1)
        draw.text((wx+14, 228), d, font=get_font(26, bold=(today2)), fill=C_WHITE if today2 else C_TEXT_SEC)
        if i in (0,2,4,5):
            draw.ellipse([wx+ww//2-10, 288, wx+ww//2+10, 308], fill=C_GREEN_LIGHT if not today2 else C_WHITE)
    draw.text((50, 336), "Anstehende Erinnerungen", font=get_font(34, bold=True), fill=C_TEXT_PRI)
    reminders = [
        ("Monstera",  "💧 Gießen",              "Heute",       C_GREEN_TEAL,  False),
        ("Orchidee",  "💧 Gießen",              "Morgen",      (156,39,176),  False),
        ("Ficus",     "✂️ Rückschnitt",          "Di, 28. Apr", (121,85,72),   False),
        ("Basilikum", "💧 Gießen",              "Mi, 29. Apr", C_GREEN_MID,   True),
        ("Aloe Vera", "🌱 Düngen",              "Fr, 01. Mai", C_AMBER,       False),
    ]
    y4 = 390
    for name, action, date, color, done in reminders:
        card(draw, 40, y4, W-80, 148, radius=18)
        plant_avatar(draw, 106, y4+74, 52, color, name[0])
        col_t = C_TEXT_SEC if done else C_TEXT_PRI
        draw.text((182, y4+18), name, font=get_font(34, bold=True), fill=col_t)
        draw.text((182, y4+62), action, font=get_font(28), fill=C_TEXT_SEC)
        draw.text((182, y4+102), date, font=get_font(26), fill=C_GREEN_DARK if not done else C_TEXT_SEC)
        draw.rounded_rectangle([W-130, y4+52, W-52, y4+108], radius=14,
                               fill=C_GREEN_TEAL if done else C_DIVIDER)
        if done:
            draw.text((W-120, y4+58), "✓", font=get_font(40, bold=True), fill=C_WHITE)
        y4 += 166
    save_screen(img, "06_erinnerungen")


def screen_ki_erkennung():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Pflanzenerkennung", 72, with_back=True)
    # Camera viewfinder mock
    draw.rounded_rectangle([60, 218, W-60, 800], radius=28, fill=(20,30,25))
    # Corner brackets
    for (bx, by) in [(100,258),(W-180,258),(100,760),(W-180,760)]:
        draw.rectangle([bx, by, bx+60, by+8], fill=C_GREEN_LIGHT)
        draw.rectangle([bx, by, bx+8, by+60], fill=C_GREEN_LIGHT)
    # Plant silhouette
    draw.text((W//2-80, 360), "🌿", font=get_font(260), fill=C_GREEN_PALE)
    draw.text((W//2-220, 720), "Pflanze in den Rahmen halten",
              font=get_font(28), fill=C_GREEN_PALE)
    # Organ selector
    draw.text((50, 836), "Pflanzenteil auswählen:", font=get_font(32, bold=True), fill=C_TEXT_PRI)
    organs = [("🍃 Blatt",True),("🌸 Blüte",False),("🍎 Frucht",False),("🌳 Rinde",False)]
    ox = 40
    for label, active in organs:
        tw = len(label)*19 + 40
        bg3 = C_GREEN_MID if active else C_WHITE
        fg3 = C_WHITE if active else C_TEXT_PRI
        draw.rounded_rectangle([ox, 888, ox+tw, 942], radius=27,
                               fill=bg3, outline=C_GREEN_PALE, width=1)
        draw.text((ox+18, 898), label, font=get_font(28, bold=active), fill=fg3)
        ox += tw + 16
    # Result card
    card(draw, 40, 968, W-80, 360, radius=24)
    draw.text((76, 990), "🔍 Erkennungsergebnis", font=get_font(32, bold=True), fill=C_TEXT_PRI)
    draw.line([(60, 1038), (W-60, 1038)], fill=C_DIVIDER, width=2)
    results = [
        ("Monstera deliciosa", "Fensterblatt",   "96%", C_GREEN_TEAL),
        ("Monstera adansonii", "Löchriges Blatt","3%",  C_GREEN_LIGHT),
        ("Epipremnum aureum",  "Efeutute",        "1%",  C_DIVIDER),
    ]
    for j3, (sci, de, pct, bar_col) in enumerate(results):
        ry = 1052 + j3*96
        draw.text((76, ry), sci, font=get_font(30, bold=True), fill=C_TEXT_PRI)
        draw.text((76, ry+36), f"{de}  ·  {pct}", font=get_font(26), fill=C_TEXT_SEC)
        bar_w = int((W-200) * int(pct[:-1]) / 100)
        draw.rounded_rectangle([76, ry+68, 76+bar_w, ry+80], radius=6, fill=bar_col)
    draw.rounded_rectangle([60, 1340, W-60, 1400], radius=28, fill=C_GREEN_TEAL)
    draw.text((120, 1352), "📋 Pflege-Info anzeigen", font=get_font(32, bold=True), fill=C_WHITE)
    save_screen(img, "07_ki_erkennung")


def screen_einstellungen():
    img, draw = canvas()
    draw_status_bar(draw)
    draw_app_bar(draw, "Einstellungen", 72)
    # Account card
    y5 = 225
    card(draw, 40, y5, W-80, 180, radius=20)
    draw.ellipse([76, y5+30, 176, y5+150], fill=C_GREEN_TEAL)
    draw.text((108, y5+62), "M", font=get_font(72, bold=True), fill=C_WHITE)
    draw.text((210, y5+36), "Max Mustermann", font=get_font(34, bold=True), fill=C_TEXT_PRI)
    draw.text((210, y5+82), "max@beispiel.de", font=get_font(28), fill=C_TEXT_SEC)
    draw.text((210, y5+122), "Google-Konto  ·  Verifiziert ✓", font=get_font(24), fill=C_GREEN_DARK)
    # Sections
    sections2 = [
        ("⭐ PlantCare Pro",          "Upgrade · Werbefrei + unbegrenzt", C_AMBER),
        ("☁️ Cloud-Synchronisation", "Automatisches Backup aktiv",       C_GREEN_TEAL),
        ("🔔 Benachrichtigungen",     "Täglich 8:00 Uhr",                C_GREEN_TEAL),
        ("🎨 Darstellung",            "System-Standard (Hell)",           C_GREEN_MID),
        ("🔒 Datenschutz & Daten",    "DSGVO · Daten exportieren",        C_GREEN_MID),
    ]
    y5 = 430
    for icon_title, subtitle, dot_col in sections2:
        card(draw, 40, y5, W-80, 112, radius=18)
        draw.ellipse([72, y5+26, 122, y5+86], fill=dot_col)
        draw.text((144, y5+18), icon_title, font=get_font(32, bold=True), fill=C_TEXT_PRI)
        draw.text((144, y5+62), subtitle, font=get_font(26), fill=C_TEXT_SEC)
        draw.text((W-100, y5+36), "›", font=get_font(56), fill=C_TEXT_SEC)
        y5 += 130
    # Danger zone
    card(draw, 40, y5+10, W-80, 110, radius=18, bg=(255,248,248))
    draw.text((76, y5+30), "⚠️  Konto löschen", font=get_font(32, bold=True), fill=C_RED)
    draw.text((76, y5+74), "Alle Daten werden unwiderruflich gelöscht.", font=get_font(24), fill=C_TEXT_SEC)
    save_screen(img, "08_einstellungen")


def save_screen(img, name):
    path = os.path.join(SCREEN_DIR, f"{name}_1080x1920.png")
    img.save(path, "PNG")
    print(f"  OK {path}")


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    os.makedirs("graphics",   exist_ok=True)
    os.makedirs(SCREEN_DIR,   exist_ok=True)
    print("Generating PlantCare store assets (v2 — Realistic UI)…\n")
    make_icon()
    make_feature()
    print()
    screen_alle_pflanzen()
    screen_meine_pflanzen()
    screen_kalender()
    screen_heute()
    screen_detail()
    screen_erinnerungen()
    screen_ki_erkennung()
    screen_einstellungen()
    print("\n✓ Done — all assets saved to graphics/ and screenshots/")
    print("  For real device screenshots, run: python adb_capture.py")
