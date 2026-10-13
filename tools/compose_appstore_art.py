"""Frame real iOS simulator captures for the App Store (6.9-inch, 1320x2868), TR and EN.

Gameplay pixels come unchanged from store-assets/raw/ios-<lang>/<scene>.png (iosApp/Scripts/store_screenshots.py);
only the presentation background (store-assets/play/ai/screenshot-background.png) and captions are added.
Run: python tools/compose_appstore_art.py
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
W, H = 1320, 2868
FONTS = Path("C:/Windows/Fonts")
SCENES = {
    "tr": [("board", "Doğal taşlar. Derin strateji.", "Ceviz tablada kendi zincirini kur."),
           ("tutorial", "Üç taşı buluştur.", "Birleştir, yükselt, yeni hamleni planla."),
           ("menu", "Kendi ritmini seç.", "Dingin veya Akış: iki farklı oyun."),
           ("result", "Her zincir yeni bir adım.", "Rekorunu kır, yeniden başla."),
           ("settings", "Sana göre ayarla.", "Ses, müzik, titreşim ve hareket azaltma.")],
    "en": [("board", "Natural stones. Deep strategy.", "Build your cascade on a walnut board."),
           ("tutorial", "Connect three.", "Merge, grow, plan your next move."),
           ("menu", "Pick your rhythm.", "Calm or Flow: two ways to play."),
           ("result", "Every cascade is a step up.", "Beat your best, then play again."),
           ("settings", "Make it yours.", "Sound, music, haptics and reduced motion.")],
}


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def background():
    bg = Image.open(ROOT / "store-assets/play/ai/screenshot-background.png").convert("RGB")
    scale = max(W / bg.width, H / bg.height)
    bg = bg.resize((round(bg.width * scale), round(bg.height * scale)), Image.LANCZOS)
    left, top = (bg.width - W) // 2, (bg.height - H) // 2
    return bg.crop((left, top, left + W, top + H))


def centered(draw, y, text, fnt, fill, spacing=0):
    if spacing:
        widths = [draw.textlength(c, font=fnt) for c in text]
        x = (W - sum(widths) - spacing * (len(text) - 1)) / 2
        for c, w in zip(text, widths):
            draw.text((x, y), c, font=fnt, fill=fill); x += w + spacing
    else:
        draw.text(((W - draw.textlength(text, font=fnt)) / 2, y), text, font=fnt, fill=fill)


def compose(lang, index, scene, title, subtitle):
    capture = Image.open(ROOT / f"store-assets/raw/ios-{lang}/{scene}.png").convert("RGB")
    cw = 1060; ch = round(capture.height * cw / capture.width)
    capture = capture.resize((cw, ch), Image.LANCZOS)
    x, y = (W - cw) // 2, H - ch - 96
    canvas = background()
    # Soft shadow and a thin brass frame around the rounded capture.
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x - 6, y + 18, x + cw + 6, y + ch + 30), 92, fill=170)
    canvas.paste((8, 10, 12), (0, 0), shadow.filter(ImageFilter.GaussianBlur(28)))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((x - 7, y - 7, x + cw + 7, y + ch + 7), 92, fill=(170, 141, 99))
    mask = Image.new("L", (cw, ch), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, cw - 1, ch - 1), 86, fill=255)
    canvas.paste(capture, (x, y), mask)
    # Readable caption plate over the background.
    plate = Image.new("L", (W, H), 0)
    ImageDraw.Draw(plate).rectangle((0, 0, W, y - 40), fill=120)
    canvas.paste((14, 16, 18), (0, 0), plate.filter(ImageFilter.GaussianBlur(60)))
    draw = ImageDraw.Draw(canvas)
    centered(draw, 150, "İLMERYA" if lang == "tr" else "ILMERYA", font("segoeui.ttf", 34), (199, 174, 135), spacing=10)
    centered(draw, 228, title, font("seguisb.ttf", 76), (244, 238, 223))
    centered(draw, 336, subtitle, font("segoeui.ttf", 44), (182, 200, 194))
    out = ROOT / f"store-assets/appstore/{lang}/{index:02d}-{scene}-1320x2868.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out, optimize=True)
    check = Image.open(out)
    assert check.size == (W, H) and check.mode == "RGB", out
    return {"file": str(out.relative_to(ROOT)).replace("\\", "/"), "scene": scene, "title": title, "subtitle": subtitle,
            "source": f"store-assets/raw/ios-{lang}/{scene}.png", "bytes": out.stat().st_size}


def main():
    manifest = [compose(lang, i + 1, *s) for lang, scenes in SCENES.items() for i, s in enumerate(scenes)]
    (ROOT / "store-assets/appstore/manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(manifest), "screenshots")


if __name__ == "__main__":
    main()
