"""
Generates elegant Play Store 7-inch tablet screenshots (1200x1920, 9:16) for Speaky.
Tablet layout: wider canvas, tablet mockup frame, larger text, two-panel feel.
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE = '/Users/samir/AndroidStudioProjects/aiTutor/play store images'
OUT  = os.path.join(BASE, 'tablet_screenshots')
os.makedirs(OUT, exist_ok=True)

W, H = 1200, 1920   # 9:16, good for 7-inch tablets

# ── colours ───────────────────────────────────────────────────────────────────
BG_TOP    = (7,   9,  44)
BG_BOT    = (13,   6,  34)
GLOW_CYAN = (0,  200, 255)
GLOW_VIOL = (110, 45, 235)
WHITE     = (255, 255, 255)
CYAN      = (0,  215, 245)
DIM_CYAN  = (120, 195, 225)
CARD_BG   = (18,  20,  55, 180)

SLIDES = [
    {
        "file":     "Screenshot_20260405_132051_Speaky.jpg",
        "headline": "Your Daily\nEnglish Hub",
        "sub":      "Words, stories & smart study tools — all in one place",
        "badge":    "Home Dashboard",
    },
    {
        "file":     "Screenshot_20260405_132132_Speaky.jpg",
        "headline": "Deep-Dive\nWord Details",
        "sub":      "Meaning, pronunciation & real-world example sentences",
        "badge":    "Word Detail",
    },
    {
        "file":     "Screenshot_20260405_132138_Speaky.jpg",
        "headline": "Study at Your\nOwn Level",
        "sub":      "Structured levels from A1 Beginner all the way to C1 Advanced",
        "badge":    "Level Selector",
    },
    {
        "file":     "Screenshot_20260405_132159_Speaky.jpg",
        "headline": "Smart\nFlashcards",
        "sub":      "Spaced-repetition review so you remember what you learn",
        "badge":    "Flash Cards",
    },
    {
        "file":     "Screenshot_20260405_132218_Speaky.jpg",
        "headline": "Learn Through\nReal Stories",
        "sub":      "Engaging narratives written for every level of learner",
        "badge":    "Stories",
    },
    {
        "file":     "Screenshot_20260405_132223_Speaky.jpg",
        "headline": "Listen & Read\nAloud",
        "sub":      "Audio playback for every story — train your ear naturally",
        "badge":    "Audio Stories",
    },
    {
        "file":     "Screenshot_20260405_132423_Speaky.jpg",
        "headline": "Instant Word\nLookup",
        "sub":      "Search any word and get definition, IPA & examples instantly",
        "badge":    "Word Search",
    },
]

# ── helpers ───────────────────────────────────────────────────────────────────

def v_gradient(w, h, top, bot):
    img = Image.new('RGB', (w, h))
    px  = img.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] + (bot[i]-top[i])*t) for i in range(3))
        for x in range(w):
            px[x, y] = c
    return img


def add_glow(canvas, cx, cy, radius, color, alpha=50, steps=22):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    for i in range(steps, 0, -1):
        r = int(radius * i / steps)
        a = int(alpha * (1 - i/steps) ** 1.3)
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(*color, a))
    canvas.alpha_composite(layer)


def dot_grid(canvas, spacing=100, color=(80, 120, 220), base_alpha=10):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    for x in range(0, canvas.width, spacing):
        for y in range(0, canvas.height, spacing):
            d.ellipse([x-1, y-1, x+1, y+1], fill=(*color, base_alpha))
    canvas.alpha_composite(layer)


def rounded_mask(w, h, r):
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w-1, h-1], radius=r, fill=255)
    return m


def make_tablet_frame(ss_path, frame_h=1150):
    """7-inch tablet portrait frame with the screenshot fitted inside."""
    src = Image.open(ss_path).convert('RGBA')
    sw, sh = src.size
    src = src.crop((0, int(sh*0.02), sw, sh))
    sw, sh = src.size

    # Tablet has ~4:7 screen ratio inside the frame (portrait)
    # We'll show the phone screenshot centred with letterbox padding
    scr_h   = frame_h - 100
    scr_w   = int(scr_h * (sw/sh))

    tab_w   = int(frame_h * 0.62)   # ~7-inch tablet portrait proportions
    tab_h   = frame_h

    tablet  = Image.new('RGBA', (tab_w, tab_h), (0,0,0,0))
    d       = ImageDraw.Draw(tablet)

    corner  = 40
    # body
    d.rounded_rectangle([0, 0, tab_w-1, tab_h-1], radius=corner,
                         fill=(16, 18, 50, 255), outline=(55, 62, 118, 210), width=3)

    # inner screen bezel (slightly inset)
    bx, by  = 18, 55
    bw, bh  = tab_w - 36, tab_h - 100
    d.rounded_rectangle([bx, by, bx+bw, by+bh], radius=22,
                         fill=(8, 10, 30, 255))

    # resize screenshot to fit screen area
    scr = src.resize((min(scr_w, bw-4), min(scr_h, bh-4)), Image.LANCZOS)
    rw, rh = scr.size
    rx = bx + (bw - rw) // 2
    ry = by + (bh - rh) // 2
    mask = rounded_mask(rw, rh, 18)
    tablet.paste(scr, (rx, ry), mask)

    # front camera (top centre)
    cx = tab_w // 2
    d.ellipse([cx-6, 22, cx+6, 34], fill=(25, 28, 65, 220))

    # home bar
    hw = 100
    hx = (tab_w - hw) // 2
    d.rounded_rectangle([hx, tab_h-20, hx+hw, tab_h-12],
                         radius=4, fill=(160, 175, 215, 110))

    return tablet


def drop_shadow(canvas, img, x, y, blur=50, opacity=0.55):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    sc    = Image.new('RGBA', img.size, (0,0,0,int(255*opacity)))
    sc.putalpha(img.split()[3])
    layer.paste(sc, (x+18, y+24))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(layer)


def get_font(size, bold=False):
    candidates = [
        '/System/Library/Fonts/Supplemental/Futura.ttc',
        '/System/Library/Fonts/SFPro.ttf',
        '/System/Library/Fonts/Helvetica.ttc',
        '/Library/Fonts/Arial Bold.ttf' if bold else '/Library/Fonts/Arial.ttf',
        '/System/Library/Fonts/Supplemental/Arial.ttf',
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def glow_text(canvas, text, x, y, font, color, glow_color, blur=16):
    gl = Image.new('RGBA', canvas.size, (0,0,0,0))
    gd = ImageDraw.Draw(gl)
    gd.text((x, y), text, font=font, fill=(*glow_color, 70))
    gl = gl.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(gl)
    ImageDraw.Draw(canvas).text((x, y), text, font=font, fill=color)


def centered_text(canvas, text, y, font, color, glow_color=None, blur=14):
    d  = ImageDraw.Draw(canvas)
    bb = d.textbbox((0, 0), text, font=font)
    x  = (W - (bb[2]-bb[0])) // 2
    if glow_color:
        gl = Image.new('RGBA', canvas.size, (0,0,0,0))
        gd = ImageDraw.Draw(gl)
        gd.text((x, y), text, font=font, fill=(*glow_color, 60))
        gl = gl.filter(ImageFilter.GaussianBlur(blur))
        canvas.alpha_composite(gl)
    ImageDraw.Draw(canvas).text((x, y), text, font=font, fill=color)
    return bb[3] - bb[1]   # line height


# ── render ────────────────────────────────────────────────────────────────────

for idx, slide in enumerate(SLIDES):
    ss_path = os.path.join(BASE, slide['file'])
    if not os.path.exists(ss_path):
        print(f'Missing {slide["file"]}, skipping')
        continue

    # 1. gradient background
    canvas = v_gradient(W, H, BG_TOP, BG_BOT).convert('RGBA')

    # 2. glows
    add_glow(canvas, W//2,  460,  750, GLOW_CYAN, alpha=38)
    add_glow(canvas, W//2, 1560,  600, GLOW_VIOL, alpha=32)
    add_glow(canvas,   80,  280,  450, GLOW_VIOL, alpha=18)
    add_glow(canvas, W-80,  280,  450, GLOW_CYAN, alpha=14)

    # 3. dot grid
    dot_grid(canvas, spacing=100)

    import textwrap

    # ── 4. text block (built top-down, track cursor y) ────────────────────────
    d = ImageDraw.Draw(canvas)
    cursor = 72

    # headline lines
    font_h = get_font(118, bold=True)
    for line in slide['headline'].split('\n'):
        lh = centered_text(canvas, line, cursor, font_h, WHITE, WHITE, blur=16)
        cursor += lh + 10
    cursor += 10

    # sub-headline wrapped
    font_s   = get_font(46)
    wrapped  = textwrap.wrap(slide['sub'], width=42)
    for line in wrapped:
        centered_text(canvas, line, cursor, font_s, (*DIM_CYAN, 200))
        cursor += 58
    cursor += 22

    # thin rainbow divider — RIGHT after the text
    div_layer = Image.new('RGBA', (W, H), (0,0,0,0))
    dd = ImageDraw.Draw(div_layer)
    div_y = cursor
    for x in range(W):
        t   = x / (W-1)
        a   = int(190 * (1 - abs(t-0.5)*2))
        col = tuple(int(GLOW_CYAN[i] + (GLOW_VIOL[i]-GLOW_CYAN[i])*t) for i in range(3))
        dd.line([(x, div_y), (x, div_y+3)], fill=(*col, a))
    canvas.alpha_composite(div_layer)
    cursor += 28

    # ── 5. tablet mockup — anchored below text with min 80px gap ─────────────
    tablet      = make_tablet_frame(ss_path, frame_h=1180)
    tw, th      = tablet.size
    tx_tab      = (W - tw) // 2

    # tablet sits at bottom, but never closer than 160px from cursor
    ty          = max(cursor + 160, H - th - 50)

    drop_shadow(canvas, tablet, tx_tab, ty, blur=55, opacity=0.58)
    canvas.paste(tablet, (tx_tab, ty), tablet)

    # ── 7. pagination dots — between badge and tablet top ────────────────────
    n_dots    = len(SLIDES)
    ds        = 26
    total_ds  = n_dots * ds
    dsx       = (W - total_ds) // 2
    dsy       = ty - 46          # 46px above tablet top

    dot_layer = Image.new('RGBA', (W,H), (0,0,0,0))
    dld       = ImageDraw.Draw(dot_layer)
    for i in range(n_dots):
        active = (i == idx)
        r      = 7 if active else 5
        a      = 230 if active else 65
        col    = CYAN if active else DIM_CYAN
        cx_    = dsx + i * ds + ds//2
        dld.ellipse([cx_-r, dsy-r, cx_+r, dsy+r], fill=(*col, a))
    canvas.alpha_composite(dot_layer)

    # 7. save
    out_name = f'tablet_{idx+1:02d}.jpg'
    out_path = os.path.join(OUT, out_name)
    canvas.convert('RGB').save(out_path, 'JPEG', quality=95)
    print(f'Saved {out_name}  ({W}x{H})')

print('Done —', OUT)
