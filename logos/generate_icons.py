#!/usr/bin/env python3
"""Generate Speaky V3 (gradient wave) icons at all required sizes."""

from PIL import Image, ImageDraw, ImageFont
import math
import os

BASE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(BASE)


def draw_v3_icon(size, variant="light"):
    """Draw the V3 gradient wave Speaky icon."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    r = int(size * 0.22)

    # Step 1: Draw gradient background on a separate layer
    # Primary colors: backgroundDark=#0D0B1E, backgroundMid=#1A1147, neonCyan=#00E5FF
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size)
            if variant == "light":
                # Dark navy to slightly lighter navy
                cr = int(13 + (26 - 13) * t)
                cg = int(11 + (17 - 11) * t)
                cb = int(30 + (71 - 30) * t)
            elif variant == "dark":
                cr = int(8 + (16 - 8) * t)
                cg = int(6 + (10 - 6) * t)
                cb = int(18 + (42 - 18) * t)
            else:  # tinted
                cr = 28
                cg = 28
                cb = 30
            bg.putpixel((x, y), (cr, cg, cb, 255))

    # Apply rounded corners mask to background
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    bg.putalpha(mask)

    # Start with the background
    img = bg.copy()
    draw = ImageDraw.Draw(img)

    # Step 2: Draw waves on a separate transparent layer, then composite
    wave_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    wave_draw = ImageDraw.Draw(wave_layer)

    # Wave color = neon cyan #00E5FF
    if variant == "light":
        wc = (0, 229, 255)
    elif variant == "dark":
        wc = (0, 184, 212)
    else:
        wc = (142, 142, 147)

    # Wave 1 - thicker, more subtle
    points1 = []
    for px in range(0, size, max(1, size // 256)):
        amp = size * 0.14
        py = size * 0.5 + amp * math.sin(2.5 * math.pi * px / size)
        points1.append((px, int(py)))
    if len(points1) > 1:
        w1 = max(2, int(size * 0.07))
        alpha1 = 40 if variant == "light" else 20
        wave_draw.line(points1, fill=(*wc, alpha1), width=w1, joint="curve")

    # Wave 2 - thinner
    points2 = []
    for px in range(0, size, max(1, size // 256)):
        amp = size * 0.12
        py = size * 0.56 + amp * math.sin(2.8 * math.pi * px / size + 1.0)
        points2.append((px, int(py)))
    if len(points2) > 1:
        w2 = max(1, int(size * 0.045))
        alpha2 = 30 if variant == "light" else 15
        wave_draw.line(points2, fill=(*wc, alpha2), width=w2, joint="curve")

    # Composite waves onto main image
    img = Image.alpha_composite(img, wave_layer)
    draw = ImageDraw.Draw(img)

    # Step 3: Find a bold font
    font_paths = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Impact.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ]
    font_size = int(size * 0.19)
    font = None
    for fp in font_paths:
        if os.path.exists(fp):
            try:
                font = ImageFont.truetype(fp, font_size)
                break
            except Exception:
                continue
    if font is None:
        font = ImageFont.load_default()

    text = "Speaky"

    # Measure text
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = (size - tw) // 2
    ty = (size - th) // 2 - int(size * 0.01)

    # Step 4: Draw text on a separate layer (shadow + main)
    text_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)

    # Subtle shadow
    shadow_off = max(1, int(size * 0.005))
    text_draw.text((tx + shadow_off, ty + shadow_off), text, font=font, fill=(0, 0, 0, 35))

    # Main text
    if variant == "light":
        text_color = (255, 255, 255, 255)
    elif variant == "dark":
        text_color = (224, 216, 240, 255)
    else:
        text_color = (142, 142, 147, 255)
    text_draw.text((tx, ty), text, font=font, fill=text_color)

    # Composite text onto main image
    img = Image.alpha_composite(img, text_layer)
    draw = ImageDraw.Draw(img)

    # Step 5: Cyan dot accent (matching neonCyan)
    dot_r = int(size * 0.024)
    dot_x = int(size * 0.80)
    dot_y = int(size * 0.35)
    if variant == "light":
        dot_color = (0, 229, 255, 255)
    elif variant == "dark":
        dot_color = (0, 184, 212, 230)
    else:
        dot_color = (142, 142, 147, 255)
    draw.ellipse([dot_x - dot_r, dot_y - dot_r, dot_x + dot_r, dot_y + dot_r], fill=dot_color)

    return img


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG")
    print(f"  {path} ({img.size[0]}x{img.size[1]})")


def save_webp(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "WEBP", quality=90)
    print(f"  {path} ({img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    print("Generating iOS icons...")
    ios_icon_dir = os.path.join(PROJECT, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
    save_png(draw_v3_icon(1024, "light"), os.path.join(ios_icon_dir, "app-icon-1024.png"))
    save_png(draw_v3_icon(1024, "dark"), os.path.join(ios_icon_dir, "app-icon-1024-dark.png"))
    save_png(draw_v3_icon(1024, "tinted"), os.path.join(ios_icon_dir, "app-icon-1024-tinted.png"))

    print("Generating iOS splash logos...")
    splash_dir = os.path.join(PROJECT, "iosApp/iosApp/Assets.xcassets/SplashLogo.imageset")
    save_png(draw_v3_icon(200, "light"), os.path.join(splash_dir, "splash-logo.png"))
    save_png(draw_v3_icon(400, "light"), os.path.join(splash_dir, "splash-logo@2x.png"))
    save_png(draw_v3_icon(600, "light"), os.path.join(splash_dir, "splash-logo@3x.png"))

    print("Generating Android mipmap icons...")
    densities = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
    for folder, sz in densities.items():
        res_dir = os.path.join(PROJECT, f"androidApp/src/main/res/{folder}")
        icon = draw_v3_icon(sz, "light")
        save_webp(icon, os.path.join(res_dir, "ic_launcher.webp"))
        save_webp(icon, os.path.join(res_dir, "ic_launcher_round.webp"))
        save_webp(icon, os.path.join(res_dir, "ic_launcher_foreground.webp"))

    print("Generating Play Store icon...")
    save_png(draw_v3_icon(512, "light"), os.path.join(PROJECT, "androidApp/src/main/ic_launcher-playstore.png"))

    print("\nAll done!")
