"""Render Ilmerya launcher/store icons from the game's own walnut and mineral textures.

Three opaque stones (malachite 2, red jasper 4, turquoise 8) sit in carved sockets on walnut:
"connect three". Outputs store PNGs, Android adaptive/legacy mipmaps and the iOS AppIcon.
"""
from pathlib import Path
import math
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "shared/src/commonMain/composeResources/drawable"
walnut = Image.open(DRAWABLE / "walnut_material.png").convert("RGB")
atlas = Image.open(DRAWABLE / "mineral_atlas.png").convert("RGB")
TW, TH = atlas.width // 2, atlas.height // 3
S = 4  # supersampling


def tile(index, crop=0.62, shift=(0.18, 0.2)):
    x0 = (index % 2) * TW + int(TW * shift[0]); y0 = (index // 2) * TH + int(TH * shift[1])
    return atlas.crop((x0, y0, x0 + int(TW * crop), y0 + int(TH * crop)))


def hex_points(cx, cy, r, rounding=0.22, steps=8):
    corners = [(cx + math.cos(math.pi / 3 * i) * r, cy + math.sin(math.pi / 3 * i) * r) for i in range(6)]
    pts = []
    for i, c in enumerate(corners):
        prev, nxt = corners[(i + 5) % 6], corners[(i + 1) % 6]
        a = (c[0] + (prev[0] - c[0]) * rounding, c[1] + (prev[1] - c[1]) * rounding)
        b = (c[0] + (nxt[0] - c[0]) * rounding, c[1] + (nxt[1] - c[1]) * rounding)
        for s in range(steps + 1):
            t = s / steps
            pts.append(((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t * t * b[0],
                        (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t * t * b[1]))
    return pts


def mask(size, pts, blur=0):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m.filter(ImageFilter.GaussianBlur(blur)) if blur else m


def gradient(size, start, end, c0, c1):
    """Linear gradient between points start→end (pixel coords) from colour c0 to c1 (RGBA tuples)."""
    w, h = size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = end[0] - start[0], end[1] - start[1]
    t = np.clip(((x - start[0]) * dx + (y - start[1]) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
    arr = np.array(c0, np.float32) * (1 - t) + np.array(c1, np.float32) * t
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def paste_texture(canvas, texture, pts, box, tone=1.0):
    m = mask(canvas.size, pts)
    tex = texture.resize((int(box[2] - box[0]), int(box[3] - box[1])), Image.LANCZOS)
    if tone != 1.0:
        tex = Image.eval(tex, lambda v: int(min(255, v * tone)))
    layer = Image.new("RGB", canvas.size); layer.paste(tex, (int(box[0]), int(box[1])))
    canvas.paste(layer, (0, 0), m)


def overlay(canvas, color_layer, pts):
    m = mask(canvas.size, pts)
    a = ImageChops.multiply(color_layer.getchannel("A"), m)
    canvas.paste(color_layer.convert("RGB"), (0, 0), a)


def stone(canvas, cx, cy, r, texture_index, value, shift):
    tex = tile(texture_index, shift=shift)
    top = cy - r * 0.08
    body = hex_points(cx, cy + r * 0.12, r * 0.84)
    shoulder = hex_points(cx, top, r * 0.84)
    face = hex_points(cx, top, r * 0.84 * 0.89)
    box = (cx - r, top - r, cx + r, top + r)
    # Contact shadow.
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).polygon(hex_points(cx + r * 0.05, cy + r * 0.2, r * 0.86), fill=(10, 4, 0, 170))
    canvas.paste(shadow.convert("RGB"), (0, 0), shadow.getchannel("A").filter(ImageFilter.GaussianBlur(r * 0.09)))
    # Sidewall: the same mineral in shadow.
    paste_texture(canvas, tex, body, (cx - r, cy + r * 0.12 - r, cx + r, cy + r * 0.12 + r), tone=0.52)
    # Rolled shoulder and honed face.
    paste_texture(canvas, tex, shoulder, box)
    overlay(canvas, gradient(canvas.size, (cx - r, top - r), (cx + r, top + r), (255, 255, 255, 38), (0, 0, 0, 70)), shoulder)
    paste_texture(canvas, tex, face, box)
    overlay(canvas, gradient(canvas.size, (cx - r, top - r), (cx + r, top + r), (255, 255, 255, 18), (0, 0, 0, 22)), face)
    # Light catching the outer rim.
    rim = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(rim).line(shoulder + [shoulder[0]], fill=(255, 255, 255, 60), width=max(2, int(r * 0.022)))
    grad = gradient(canvas.size, (cx - r, top - r), (cx + r, top + r), (255, 255, 255, 255), (0, 0, 0, 0))
    canvas.paste(rim.convert("RGB"), (0, 0), ImageChops.multiply(rim.getchannel("A"), grad.getchannel("A")))
    # Inlaid numeral.
    font = None
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            font = ImageFont.truetype(name, int(r * 0.78)); break
        except OSError:
            continue
    text = str(value)
    d = ImageDraw.Draw(canvas)
    bbox = d.textbbox((0, 0), text, font=font)
    tx, ty = cx - (bbox[2] + bbox[0]) / 2, top - (bbox[3] + bbox[1]) / 2 - r * 0.03
    ivory = value != 8
    d.text((tx + r * 0.02, ty + r * 0.03), text, font=font, fill=(0, 0, 0) if ivory else (0, 0, 0))
    if not ivory:
        d.text((tx, ty + r * 0.028), text, font=font, fill=(150, 230, 230))
    d.text((tx, ty), text, font=font, fill=(255, 248, 232) if ivory else (20, 29, 25))


def socket(canvas, cx, cy, r):
    opening = hex_points(cx, cy, r * 0.89)
    overlay(canvas, gradient(canvas.size, (cx - r * .7, cy - r), (cx + r * .6, cy + r), (41, 25, 15, 255), (184, 143, 91, 255)), opening)
    floor = hex_points(cx + r * 0.013, cy + r * 0.055, r * 0.77)
    overlay(canvas, gradient(canvas.size, (cx, cy - r), (cx, cy + r), (60, 40, 26, 235), (90, 62, 40, 235)), floor)


def render(size, background=True, foreground=True, mono=False, inset=1.0):
    W = size * S
    if background:
        crop = walnut.crop((walnut.width * 0.08, walnut.height * 0.2, walnut.width * 0.92, walnut.height * 0.2 + walnut.width * 0.84))
        canvas = crop.resize((W, W), Image.LANCZOS)
        canvas = Image.blend(canvas, Image.new("RGB", (W, W), (30, 16, 7)), 0.18)
        vignette = gradient((W, W), (0, 0), (W, W), (255, 214, 160, 40), (10, 4, 0, 110))
        canvas.paste(vignette.convert("RGB"), (0, 0), vignette.getchannel("A"))
    else:
        canvas = Image.new("RGB", (W, W), (0, 0, 0))
    alpha = Image.new("L", (W, W), 0)
    if foreground:
        r = W * 0.215 * inset
        c = W / 2
        h = r * math.sqrt(3)
        # Two stones in one column and one in the next column: three mutual hex neighbours, as on the board.
        x0, y0 = c - r * 0.75, c - h * 0.5 - r * 0.04
        centers = [(x0, y0, 0, 2, (0.1, 0.15)),
                   (x0, y0 + h, 1, 4, (0.3, 0.25)),
                   (x0 + r * 1.5, y0 + h * 0.5, 5, 8, (0.2, 0.3))]
        if mono:
            for x, y, *_ in centers:
                ImageDraw.Draw(alpha).polygon(hex_points(x, y, r * 0.84), fill=255)
            return Image.merge("RGBA", (alpha, alpha, alpha, alpha)).resize((size, size), Image.LANCZOS)
        layer = canvas.copy() if background else Image.new("RGB", (W, W), (0, 0, 0))
        for x, y, *_ in centers:
            socket(layer, x, y + r * 0.04, r * 1.02)
        for x, y, index, value, shift in centers:
            stone(layer, x, y, r, index, value, shift)
        if background:
            canvas = layer
        else:
            for x, y, *_ in centers:
                ImageDraw.Draw(alpha).polygon(hex_points(x, y + r * 0.04, r * 1.02 * 0.92), fill=255)
                ImageDraw.Draw(alpha).polygon(hex_points(x + r * .05, y + r * .2, r * 0.95), fill=255)
            alpha = alpha.filter(ImageFilter.GaussianBlur(S))
            canvas = layer
    img = canvas.resize((size, size), Image.LANCZOS)
    if not background:
        img = img.convert("RGBA"); img.putalpha(alpha.resize((size, size), Image.LANCZOS))
    return img


def main():
    store = ROOT / "store-assets/icon"; store.mkdir(parents=True, exist_ok=True)
    full = render(1024)
    full.save(store / "ilmerya-icon-1024.png")
    full.resize((512, 512), Image.LANCZOS).save(store / "ilmerya-play-icon-512.png")
    ios = ROOT / "iosApp/Resources/Assets.xcassets/AppIcon.appiconset"; ios.mkdir(parents=True, exist_ok=True)
    full.save(ios / "AppIcon-1024.png")
    (ios / "Contents.json").write_text(json.dumps({"images": [{"filename": "AppIcon-1024.png", "idiom": "universal",
        "platform": "ios", "size": "1024x1024"}], "info": {"author": "xcode", "version": 1}}, indent=2))
    (ios.parent / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2))
    res = ROOT / "androidApp/src/main/res"
    for density, scale in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
        folder = res / f"mipmap-{density}"; folder.mkdir(exist_ok=True)
        legacy = int(48 * scale)
        # Legacy icon: rounded square with the full composition.
        icon = render(legacy * 2).resize((legacy, legacy), Image.LANCZOS).convert("RGBA")
        rounded = Image.new("L", (legacy * 4, legacy * 4), 0)
        ImageDraw.Draw(rounded).rounded_rectangle((0, 0, legacy * 4 - 1, legacy * 4 - 1), radius=legacy * 0.9, fill=255)
        icon.putalpha(rounded.resize((legacy, legacy), Image.LANCZOS))
        icon.save(folder / "ic_launcher.png")
        circle = Image.new("L", (legacy * 4, legacy * 4), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, legacy * 4 - 1, legacy * 4 - 1), fill=255)
        round_icon = render(legacy * 2).resize((legacy, legacy), Image.LANCZOS).convert("RGBA")
        round_icon.putalpha(circle.resize((legacy, legacy), Image.LANCZOS)); round_icon.save(folder / "ic_launcher_round.png")
        adaptive = int(108 * scale)
        render(adaptive, foreground=False).save(folder / "ic_launcher_background.png")
        # Foreground stays inside the 66 dp safe zone of the 108 dp adaptive canvas.
        render(adaptive, background=False, inset=0.74).save(folder / "ic_launcher_foreground.png")
        render(adaptive, background=False, mono=True, inset=0.74).save(folder / "ic_launcher_monochrome.png")
    anydpi = res / "mipmap-anydpi-v26"; anydpi.mkdir(exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
           '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
    (anydpi / "ic_launcher.xml").write_text(xml); (anydpi / "ic_launcher_round.xml").write_text(xml)
    print("icons written")


if __name__ == "__main__":
    main()
