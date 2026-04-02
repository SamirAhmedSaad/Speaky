"""
Generates elegant Play Store screenshots (1080x1920, 9:16) for Speaky.
Each screenshot gets:
  - Branded dark gradient background
  - Feature headline at the top
  - The actual app screenshot displayed as a floating phone mockup
  - Subtle glow, decorative elements
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE = '/Users/samir/AndroidStudioProjects/aiTutor/play store images'
OUT  = os.path.join(BASE, 'play_store_screenshots')
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1920   # 9:16

# ── colours ───────────────────────────────────────────────────────────────────
BG_TOP    = (8,   10,  42)
BG_BOT    = (14,   8,  36)
GLOW_CYAN = (0,  200, 255)
GLOW_VIOL = (120, 50, 240)
WHITE     = (255, 255, 255)
CYAN      = (0,  210, 240)
DIM_CYAN  = (130, 200, 230)

# ── per-screen metadata ───────────────────────────────────────────────────────
SLIDES = [
    {
        "file": "Screenshot_20260405_132051_Speaky.jpg",
        "headline": "Your Daily\nEnglish Hub",
        "sub":      "Words, stories & study tools\nall in one place",
    },
    {
        "file": "Screenshot_20260405_132132_Speaky.jpg",
        "headline": "Deep-Dive\nWord Details",
        "sub":      "Meaning, pronunciation &\nreal-world examples",
    },
    {
        "file": "Screenshot_20260405_132138_Speaky.jpg",
        "headline": "Study at Your\nOwn Level",
        "sub":      "From A1 Beginner\nto C1 Advanced",
    },
    {
        "file": "Screenshot_20260405_132159_Speaky.jpg",
        "headline": "Smart\nFlashcards",
        "sub":      "Review vocabulary\nwhen it matters most",
    },
    {
        "file": "Screenshot_20260405_132218_Speaky.jpg",
        "headline": "Learn Through\nReal Stories",
        "sub":      "Engaging narratives\nwritten for learners",
    },
    {
        "file": "Screenshot_20260405_132223_Speaky.jpg",
        "headline": "Listen to\nStories Aloud",
        "sub":      "Improve listening &\ncomprehension skills",
    },
    {
        "file": "Screenshot_20260405_132423_Speaky.jpg",
        "headline": "Instant Word\nLookup",
        "sub":      "Search any word — get\nmeaning, IPA & examples",
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


def add_glow(canvas, cx, cy, radius, color, alpha=55, steps=20):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    for i in range(steps, 0, -1):
        r = int(radius * i / steps)
        a = int(alpha * (1 - i/steps) ** 1.3)
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(*color, a))
    canvas.alpha_composite(layer)


def dot_grid(canvas, spacing=90, color=(80, 120, 220), base_alpha=12):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    for x in range(0, canvas.width, spacing):
        for y in range(0, canvas.height, spacing):
            d.ellipse([x-1, y-1, x+1, y+1], fill=(*color, base_alpha))
    canvas.alpha_composite(layer)


def rounded_mask(w, h, r):
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0,0,w-1,h-1], radius=r, fill=255)
    return m


def make_phone(ss_path, phone_h=1100):
    src  = Image.open(ss_path).convert('RGBA')
    sw, sh = src.size
    # slight crop of status bar
    src  = src.crop((0, int(sh*0.02), sw, sh))
    sw, sh = src.size

    aspect = sw / sh
    scr_h  = phone_h - 60
    scr_w  = int(scr_h * aspect)
    scr    = src.resize((scr_w, scr_h), Image.LANCZOS)

    ph_w = scr_w + 40
    ph_h = phone_h
    phone = Image.new('RGBA', (ph_w, ph_h), (0,0,0,0))
    d = ImageDraw.Draw(phone)

    # body
    d.rounded_rectangle([0,0,ph_w-1,ph_h-1], radius=55,
                         fill=(18,20,52,255), outline=(55,60,110,200), width=3)

    # screen
    sx, sy = 20, 30
    mask = rounded_mask(scr_w, scr_h, 35)
    phone.paste(scr, (sx, sy), mask)

    # notch
    nw, nh = 80, 18
    nx = (ph_w - nw) // 2
    d.rounded_rectangle([nx, 8, nx+nw, 8+nh], radius=9, fill=(10,12,38,255))
    # camera
    d.ellipse([nx+nw-18, 10, nx+nw-8, 18], fill=(25,30,60,220))

    # home indicator
    ind_w = 120
    ix = (ph_w - ind_w) // 2
    d.rounded_rectangle([ix, ph_h-18, ix+ind_w, ph_h-10],
                         radius=4, fill=(180,190,220,120))
    return phone


def shadow(canvas, img, x, y, blur=40, opacity=0.55):
    layer = Image.new('RGBA', canvas.size, (0,0,0,0))
    s_col = Image.new('RGBA', img.size, (0,0,0,int(255*opacity)))
    s_col.putalpha(img.split()[3])
    layer.paste(s_col, (x+16, y+20))
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


# ── render ────────────────────────────────────────────────────────────────────

for idx, slide in enumerate(SLIDES):
    ss_path = os.path.join(BASE, slide['file'])
    if not os.path.exists(ss_path):
        print(f'Missing {slide["file"]}, skipping')
        continue

    # 1. background
    canvas = v_gradient(W, H, BG_TOP, BG_BOT).convert('RGBA')

    # 2. glows
    add_glow(canvas, W//2, 420,  700, GLOW_CYAN, alpha=40)
    add_glow(canvas, W//2, 1500, 550, GLOW_VIOL, alpha=35)
    add_glow(canvas, 100,  300,  400, GLOW_VIOL, alpha=20)

    # 3. dot grid
    dot_grid(canvas, spacing=90)

    # 4. top divider accent bar
    accent_layer = Image.new('RGBA', (W, H), (0,0,0,0))
    ad = ImageDraw.Draw(accent_layer)
    # horizontal gradient line under headline area
    for x in range(W):
        t   = x / (W-1)
        a   = int(180 * (1 - abs(t - 0.5)*2))
        col = tuple(int(GLOW_CYAN[i] + (GLOW_VIOL[i]-GLOW_CYAN[i])*t) for i in range(3))
        ad.line([(x, 520), (x, 523)], fill=(*col, a))
    canvas.alpha_composite(accent_layer)

    # 5. phone mockup
    phone      = make_phone(ss_path, phone_h=1120)
    ph_w, ph_h = phone.size
    px = (W - ph_w) // 2
    py = H - ph_h - 60   # bottom-anchored with padding

    shadow(canvas, phone, px, py, blur=50, opacity=0.6)
    canvas.paste(phone, (px, py), phone)

    # 6. text
    d = ImageDraw.Draw(canvas)

    # app name (small, top)
    font_app  = get_font(38)
    app_label = "Speaky"
    bb        = d.textbbox((0,0), app_label, font=font_app)
    aw        = bb[2]-bb[0]
    ax        = (W - aw) // 2
    # glow under app name
    glow_txt = Image.new('RGBA', (W,H), (0,0,0,0))
    gt = ImageDraw.Draw(glow_txt)
    gt.text((ax, 68), app_label, font=font_app, fill=(*CYAN, 80))
    glow_txt = glow_txt.filter(ImageFilter.GaussianBlur(10))
    canvas.alpha_composite(glow_txt)
    d.text((ax, 68), app_label, font=font_app, fill=(*CYAN, 220))

    # headline
    font_h = get_font(108, bold=True)
    hy     = 140
    for line in slide['headline'].split('\n'):
        bb  = d.textbbox((0,0), line, font=font_h)
        lw  = bb[2]-bb[0]
        lx  = (W - lw) // 2
        # subtle glow
        glt = Image.new('RGBA', (W,H), (0,0,0,0))
        gd  = ImageDraw.Draw(glt)
        gd.text((lx, hy), line, font=font_h, fill=(255,255,255,40))
        glt = glt.filter(ImageFilter.GaussianBlur(14))
        canvas.alpha_composite(glt)
        d.text((lx, hy), line, font=font_h, fill=WHITE)
        hy += 118

    # sub-headline
    font_s = get_font(46)
    sy2    = hy + 10
    for line in slide['sub'].split('\n'):
        bb  = d.textbbox((0,0), line, font=font_s)
        lw  = bb[2]-bb[0]
        lx  = (W - lw) // 2
        d.text((lx, sy2), line, font=font_s, fill=(*DIM_CYAN, 200))
        sy2 += 58

    # 7. decorative dot row above phone
    dot_row_layer = Image.new('RGBA', (W,H), (0,0,0,0))
    drl = ImageDraw.Draw(dot_row_layer)
    n_dots = 7
    dot_spacing = 24
    total_w = n_dots * dot_spacing
    dsx = (W - total_w) // 2
    dsy = py - 38
    for i in range(n_dots):
        r   = 5 if i == idx % n_dots else 4
        a   = 220 if i == idx % n_dots else 70
        col = CYAN if i == idx % n_dots else DIM_CYAN
        cx  = dsx + i * dot_spacing + dot_spacing//2
        drl.ellipse([cx-r, dsy-r, cx+r, dsy+r], fill=(*col, a))
    canvas.alpha_composite(dot_row_layer)

    # 8. save
    out_name = f'screenshot_{idx+1:02d}.jpg'
    out_path = os.path.join(OUT, out_name)
    canvas.convert('RGB').save(out_path, 'JPEG', quality=95)
    print(f'Saved {out_name}  ({W}x{H})')

print('Done —', OUT)
