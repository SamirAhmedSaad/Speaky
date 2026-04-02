"""
Generates elegant Play Store feature graphics (1024x500) for Speaky.
Creates one graphic per screenshot, each with:
  - Dark-to-deep-navy gradient background
  - Subtle radial glow / light bloom
  - Floating phone mockup with the screenshot + rounded corners + soft shadow
  - App name "Speaky" + tagline on the left
  - Decorative dots / circles
"""

import os, math, textwrap
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

BASE = '/Users/samir/AndroidStudioProjects/aiTutor/play store images'
OUT  = os.path.join(BASE, 'feature_graphics')
os.makedirs(OUT, exist_ok=True)

W, H = 1024, 500

# ── colours ──────────────────────────────────────────────────────────────────
BG_LEFT  = (8,   10,  40)   # deep indigo
BG_RIGHT = (15,   8,  35)   # deep purple-navy
GLOW1    = (0,  200, 255)   # cyan
GLOW2    = (130, 60, 255)   # violet
TEXT_PRI = (255, 255, 255)
TEXT_SEC = (0,  210, 240)   # cyan
ACCENT   = (0,  200, 255)

TAGLINES = [
    "Learn English,\nOne Word a Day",
    "Master Vocabulary\nWith Smart Study",
    "Real Stories.\nReal English.",
    "Look Up Any Word\nInstantly",
    "Daily Words.\nDaily Progress.",
    "Build Your\nVocabulary Fast",
    "Search. Learn.\nRemember.",
]

SCREENSHOTS = sorted([
    os.path.join(BASE, f) for f in os.listdir(BASE)
    if f.endswith('.jpg') and 'Speaky' in f
])

# ── helpers ───────────────────────────────────────────────────────────────────

def lerp_color(c1, c2, t):
    return tuple(int(c1[i] + (c2[i]-c1[i])*t) for i in range(3))


def make_gradient(w, h, c_left, c_right):
    img = Image.new('RGB', (w, h))
    px  = img.load()
    for x in range(w):
        t = x / (w - 1)
        c = lerp_color(c_left, c_right, t)
        for y in range(h):
            px[x, y] = c
    return img


def add_radial_glow(canvas, cx, cy, radius, color, alpha=60):
    glow = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    d    = ImageDraw.Draw(glow)
    steps = 18
    for i in range(steps, 0, -1):
        r    = int(radius * i / steps)
        a    = int(alpha * (1 - i / steps) ** 1.4)
        bbox = (cx - r, cy - r, cx + r, cy + r)
        d.ellipse(bbox, fill=(*color, a))
    canvas.alpha_composite(glow)


def rounded_rect_mask(w, h, radius):
    mask = Image.new('L', (w, h), 0)
    d    = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, w-1, h-1], radius=radius, fill=255)
    return mask


def phone_mockup(screenshot_path, phone_h=380):
    """Returns a phone-shaped RGBA image with the screenshot inside."""
    src  = Image.open(screenshot_path).convert('RGBA')
    sw, sh = src.size
    # screen area inside the phone frame (we crop top UI bar slightly)
    crop_top = int(sh * 0.03)
    src = src.crop((0, crop_top, sw, sh))
    sw, sh = src.size

    aspect  = sw / sh
    scr_h   = phone_h - 24          # leave room for top/bottom bezel
    scr_w   = int(scr_h * aspect)
    scr     = src.resize((scr_w, scr_h), Image.LANCZOS)

    ph_w    = scr_w + 18            # horizontal bezel
    ph_h    = phone_h
    phone   = Image.new('RGBA', (ph_w, ph_h), (0, 0, 0, 0))
    d       = ImageDraw.Draw(phone)

    # phone body
    corner  = 28
    d.rounded_rectangle([0, 0, ph_w-1, ph_h-1], radius=corner,
                         fill=(20, 22, 50, 255), outline=(50, 55, 100, 200), width=2)

    # screen cutout with rounded corners
    sx, sy  = 9, 12
    mask    = rounded_rect_mask(scr_w, scr_h, 20)
    phone.paste(scr, (sx, sy), mask)

    # top notch
    notch_w, notch_h = 40, 8
    nx = (ph_w - notch_w) // 2
    d.rounded_rectangle([nx, 4, nx+notch_w, 4+notch_h], radius=4,
                         fill=(10, 12, 35, 255))

    # camera dot
    d.ellipse([nx + notch_w - 10, 5, nx + notch_w - 4, 11],
              fill=(30, 35, 70, 200))

    return phone


def add_shadow(canvas_rgba, phone_img, x, y, blur=22, opacity=0.55):
    shadow_layer = Image.new('RGBA', canvas_rgba.size, (0, 0, 0, 0))
    shadow_color = Image.new('RGBA', phone_img.size, (0, 0, 0, int(255*opacity)))
    shadow_color.putalpha(phone_img.split()[3])
    shadow_layer.paste(shadow_color, (x + 10, y + 14))
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(blur))
    canvas_rgba.alpha_composite(shadow_layer)


def draw_dots(d, cx, cy, color, count=6, spacing=18, alpha_base=80):
    for i in range(count):
        r = 3 - min(i, 2)
        ox = i * spacing
        fill = (*color, max(20, alpha_base - i * 12))
        # ImageDraw doesn't support alpha directly, skip alpha dots
        d.ellipse([cx+ox-r, cy-r, cx+ox+r, cy+r],
                  fill=(*color[:3], 0))   # placeholder; actual drawn below
    # We just skip alpha on the dots — draw on RGBA layer instead


def draw_dots_rgba(img, cx, cy, color, count=6, spacing=18, alpha_base=100):
    layer = Image.new('RGBA', img.size, (0,0,0,0))
    ld = ImageDraw.Draw(layer)
    for i in range(count):
        r    = max(1, 3 - i)
        ox   = i * spacing
        a    = max(15, alpha_base - i * 15)
        ld.ellipse([cx+ox-r, cy-r, cx+ox+r, cy+r], fill=(*color[:3], a))
    img.alpha_composite(layer)


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


# ── main loop ─────────────────────────────────────────────────────────────────

for idx, (ss_path, tagline) in enumerate(zip(SCREENSHOTS, TAGLINES)):
    # 1. gradient background
    bg   = make_gradient(W, H, BG_LEFT, BG_RIGHT).convert('RGBA')

    # 2. radial glows
    add_radial_glow(bg, int(W * 0.72), H // 2, 320, GLOW1, alpha=45)
    add_radial_glow(bg, int(W * 0.60), H // 2, 220, GLOW2, alpha=30)
    add_radial_glow(bg, 180,           H // 2, 260, GLOW2, alpha=25)

    # 3. subtle noise-like hex dots grid (decorative)
    dot_layer = Image.new('RGBA', (W, H), (0,0,0,0))
    dd = ImageDraw.Draw(dot_layer)
    for gx in range(0, W, 55):
        for gy in range(0, H, 55):
            a = 18 if (gx + gy) % 110 == 0 else 9
            dd.ellipse([gx-1, gy-1, gx+1, gy+1], fill=(100, 150, 255, a))
    bg.alpha_composite(dot_layer)

    # 4. phone mockup (main, slightly tilted via rotate)
    phone = phone_mockup(ss_path, phone_h=400)
    ph_w, ph_h = phone.size
    phone_rot  = phone.rotate(-4, expand=True, resample=Image.BICUBIC)
    pr_w, pr_h = phone_rot.size

    # position: right side
    px = W - pr_w - 55
    py = (H - pr_h) // 2 - 10

    add_shadow(bg, phone_rot, px, py, blur=30, opacity=0.6)
    bg.paste(phone_rot, (px, py), phone_rot)

    # 5. second smaller phone behind (ghost, if we have a prev screenshot)
    if idx > 0:
        phone2     = phone_mockup(SCREENSHOTS[idx-1], phone_h=340)
        phone2_rot = phone2.rotate(6, expand=True, resample=Image.BICUBIC)
        p2w, p2h   = phone2_rot.size
        p2x = px + 90
        p2y = (H - p2h) // 2 + 20
        # darken / fade it
        r, g, b, a_ch = phone2_rot.split()
        a_ch = a_ch.point(lambda p: int(p * 0.38))
        phone2_faded = Image.merge('RGBA', (r, g, b, a_ch))
        bg.paste(phone2_faded, (p2x, p2y), phone2_faded)

    # 6. text — left side
    d    = ImageDraw.Draw(bg)
    tx   = 52
    ty   = 90

    # app name
    font_name = get_font(72, bold=True)
    # cyan gradient text trick — draw twice for glow
    glow_layer = Image.new('RGBA', (W, H), (0,0,0,0))
    gd = ImageDraw.Draw(glow_layer)
    gd.text((tx+1, ty+1), "Speaky", font=font_name, fill=(0, 210, 255, 60))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(8))
    bg.alpha_composite(glow_layer)
    d.text((tx, ty), "Speaky", font=font_name, fill=(255, 255, 255, 255))

    # tagline
    font_tag = get_font(28)
    ty2 = ty + 82
    for line in tagline.split('\n'):
        d.text((tx, ty2), line, font=font_tag, fill=(160, 220, 255, 230))
        ty2 += 38

    # thin cyan accent line
    d.rectangle([tx, ty + 76, tx + 140, ty + 78], fill=(*ACCENT, 200))

    # decorative dots row
    draw_dots_rgba(bg, tx, H - 70, ACCENT, count=8, spacing=20, alpha_base=120)

    # small "AI-Powered English" badge
    font_badge = get_font(17)
    bx, by     = tx, H - 115
    badge_txt  = "✦  AI-Powered English Learning"
    bbox       = d.textbbox((bx, by), badge_txt, font=font_badge)
    bw         = bbox[2] - bbox[0] + 22
    bh_        = bbox[3] - bbox[1] + 12
    badge_layer = Image.new('RGBA', (W, H), (0,0,0,0))
    bd = ImageDraw.Draw(badge_layer)
    bd.rounded_rectangle([bx-2, by-6, bx+bw, by+bh_],
                          radius=8, fill=(0, 180, 230, 45), outline=(0, 200, 255, 100), width=1)
    bg.alpha_composite(badge_layer)
    d.text((bx + 8, by), badge_txt, font=font_badge, fill=(0, 210, 255, 210))

    # 7. save
    out_name = f'feature_{idx+1:02d}.jpg'
    out_path = os.path.join(OUT, out_name)
    bg.convert('RGB').save(out_path, 'JPEG', quality=95)
    print(f'Saved {out_name}')

print('Done — all feature graphics in', OUT)
