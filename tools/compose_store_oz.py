"""Store art with the OzGAMES mascot Senior Oz, for Google Play and the App Store (TR and EN).

Gameplay pixels are unchanged simulator captures (store-assets/raw/ios-<lang>/<scene>.png); only the
presentation background, Senior Oz artwork (store-assets/ai-oz, AI-generated from the approved Bloxboom
character reference) and captions are added.
Run: python tools/compose_store_oz.py
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
FONTS = Path("C:/Windows/Fonts")
AI = ROOT / "store-assets/ai-oz"
BRAND = {"tr": "İLMERYA", "en": "ILMERYA"}
SHOTS = {
    "tr": [("board", "hero", "Doğal taşlar. Derin strateji.", "Senior Oz ile ceviz tablada zincir kur."),
           ("tutorial", "plain", "Üç taşı buluştur.", "Birleştir, yükselt, yeni hamleni planla."),
           ("menu", "plain", "Kendi ritmini seç.", "Dingin veya Akış: iki farklı oyun."),
           ("result", "celebrate", "Her zincir yeni bir adım.", "Rekorunu kır, yeniden başla."),
           ("settings", "plain", "Sana göre ayarla.", "Ses, müzik, titreşim ve hareket azaltma.")],
    "en": [("board", "hero", "Natural stones. Deep strategy.", "Build cascades with Senior Oz."),
           ("tutorial", "plain", "Connect three.", "Merge, grow, plan your next move."),
           ("menu", "plain", "Pick your rhythm.", "Calm or Flow: two ways to play."),
           ("result", "celebrate", "Every cascade is a step up.", "Beat your best, then play again."),
           ("settings", "plain", "Make it yours.", "Sound, music, haptics and reduced motion.")],
}
FEATURE = {"tr": ("TAŞ BİRLEŞTİRME", "Ceviz tablada doğal taşlar"), "en": ("STONE MERGE PUZZLE", "Natural stones on a walnut board")}


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def cover(img, w, h):
    s = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    l, t = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((l, t, l + w, t + h))


def text_center(draw, w, y, text, fnt, fill, spacing=0):
    if spacing:
        widths = [draw.textlength(c, font=fnt) for c in text]
        x = (w - sum(widths) - spacing * (len(text) - 1)) / 2
        for c, cw in zip(text, widths):
            draw.text((x, y), c, font=fnt, fill=fill); x += cw + spacing
    else:
        draw.text(((w - draw.textlength(text, font=fnt)) / 2, y), text, font=fnt, fill=fill)


def framed(canvas, capture, x, y, radius):
    """Rounded capture with a brass rim and soft shadow."""
    w, h = capture.size
    shadow = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x - 6, y + 18, x + w + 6, y + h + 30), radius + 6, fill=170)
    canvas.paste((8, 10, 12), (0, 0), shadow.filter(ImageFilter.GaussianBlur(radius * 0.35)))
    ImageDraw.Draw(canvas).rounded_rectangle((x - 7, y - 7, x + w + 7, y + h + 7), radius + 6, fill=(170, 141, 99))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
    canvas.paste(capture, (x, y), mask)


def oz(name, width):
    img = Image.open(AI / name).convert("RGBA")
    img = img.crop(img.getbbox())
    return img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)


def paste_oz(canvas, sprite, x, y):
    alpha = sprite.split()[-1]
    glow = Image.new("L", canvas.size, 0)
    glow.paste(alpha.point(lambda a: 140 if a > 20 else 0), (x + 10, y + 26))
    canvas.paste((6, 8, 10), (0, 0), glow.filter(ImageFilter.GaussianBlur(26)))
    canvas.alpha_composite(sprite, (x, y))


def screenshot(lang, index, scene, layout, title, subtitle, store):
    W, H = (1320, 2868) if store == "appstore" else (1080, 1920)
    k = W / 1320
    capture = Image.open(ROOT / f"store-assets/raw/ios-{lang}/{scene}.png").convert("RGB")
    if store == "play":
        # Google Play: no iPhone status bar or home indicator.
        capture = capture.crop((0, 150, capture.width, capture.height - 70))
    canvas = cover(Image.open(ROOT / "store-assets/play/ai/screenshot-background.png").convert("RGB"), W, H).convert("RGBA")
    top = round(470 * k) if store == "appstore" else 300
    bottom = round(96 * k) if store == "appstore" else 70
    if layout == "hero":
        # Phone on the right just below the caption; Senior Oz below-left, pointing up at it.
        cw = round(W * 0.62); ch = round(capture.height * cw / capture.width)
        cap = capture.resize((cw, ch), Image.LANCZOS)
        x, y = W - cw - round(64 * k), top + round(10 * k)
    else:
        ch = H - top - bottom
        cw = round(capture.width * ch / capture.height)
        cap = capture.resize((cw, ch), Image.LANCZOS)
        x, y = (W - cw) // 2, H - ch - bottom
    framed(canvas, cap.convert("RGB"), x, y, round(86 * k * (cw / 1060)))
    if layout == "hero":
        sprite = oz("oz-point.png", round(W * (0.56 if store == "appstore" else 0.46)))
        paste_oz(canvas, sprite, round(-W * (0.03 if store == "appstore" else 0.05)), H - sprite.height - round(30 * k))
    elif layout == "celebrate":
        sprite = oz("oz-stones.png", round(W * (0.40 if store == "appstore" else 0.34)))
        paste_oz(canvas, sprite, round(-W * 0.05), H - sprite.height - round(40 * k))
    # Caption plate and text.
    plate = Image.new("L", (W, H), 0)
    ImageDraw.Draw(plate).rectangle((0, 0, W, top - round(40 * k)), fill=120)
    canvas.paste((14, 16, 18), (0, 0), plate.filter(ImageFilter.GaussianBlur(60 * k)))
    d = ImageDraw.Draw(canvas)
    s = W / 1320
    text_center(d, W, round(150 * s) if store == "appstore" else 54, BRAND[lang], font("segoeui.ttf", round(34 * s) if store == "appstore" else 26), (199, 174, 135), spacing=round(10 * s))
    text_center(d, W, round(228 * s) if store == "appstore" else 104, title, font("seguisb.ttf", round(76 * s) if store == "appstore" else 58), (244, 238, 223))
    text_center(d, W, round(336 * s) if store == "appstore" else 186, subtitle, font("segoeui.ttf", round(44 * s) if store == "appstore" else 34), (182, 200, 194))
    folder = ROOT / (f"store-assets/appstore/{lang}" if store == "appstore" else f"store-assets/play/screenshots-{lang}")
    folder.mkdir(parents=True, exist_ok=True)
    out = folder / f"{index:02d}-{scene}-{W}x{H}.png"
    canvas.convert("RGB").save(out, optimize=True)
    return {"file": str(out.relative_to(ROOT)).replace("\\", "/"), "scene": scene, "layout": layout, "bytes": out.stat().st_size}


def feature(lang):
    W, H = 1024, 500
    scene = cover(Image.open(AI / "feature-scene.png").convert("RGB"), W, H)
    d = ImageDraw.Draw(scene)
    sub, tag = FEATURE[lang]
    title = BRAND[lang]
    fnt = font("seguisb.ttf", 92)
    tw = sum(d.textlength(c, font=fnt) for c in title) + 14 * (len(title) - 1)
    x0 = 56
    x = x0
    for c in title:
        d.text((x, 150), c, font=fnt, fill=(244, 238, 223)); x += d.textlength(c, font=fnt) + 14
    d.text((x0 + 4, 268), sub, font=font("segoeui.ttf", 30), fill=(199, 174, 135))
    d.text((x0 + 4, 316), tag, font=font("segoeui.ttf", 24), fill=(182, 200, 194))
    d.text((x0 + 4, 440), "OzGAMES", font=font("segoeui.ttf", 18), fill=(150, 160, 160))
    out = ROOT / f"store-assets/play/feature/ilmerya-feature-oz-{lang}-1024x500.png"
    scene.save(out, optimize=True)
    return {"file": str(out.relative_to(ROOT)).replace("\\", "/"), "bytes": out.stat().st_size}


def main():
    manifest = {"note": "Senior Oz artwork AI-generated (Codex image_gen) from the approved Bloxboom character reference; gameplay captures unchanged.",
                "screenshots": [], "feature": []}
    for store in ("appstore", "play"):
        for lang, shots in SHOTS.items():
            for i, s in enumerate(shots):
                manifest["screenshots"].append(screenshot(lang, i + 1, *s, store))
    for lang in FEATURE:
        manifest["feature"].append(feature(lang))
    (ROOT / "store-assets/oz-manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(manifest["screenshots"]), "screenshots,", len(manifest["feature"]), "feature graphics")


if __name__ == "__main__":
    main()
