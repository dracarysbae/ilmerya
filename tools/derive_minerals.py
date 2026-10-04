"""Derive six higher-tier opaque mineral swatches from the generated base atlas.

Each new tile keeps the natural structure (bands, veins, mottling) of a base tile
and remaps its luminance through a mineral-specific colour ramp. The base atlas is
not modified. Output: shared/src/commonMain/composeResources/drawable/mineral_atlas_high.png
(2 columns x 3 rows, same layout as mineral_atlas.png).
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "shared/src/commonMain/composeResources/drawable"
base = np.asarray(Image.open(DRAWABLE / "mineral_atlas.png").convert("RGB")).astype(np.float32) / 255
TW, TH = base.shape[1] // 2, base.shape[0] // 3


def tile(index: int) -> np.ndarray:
    x, y = (index % 2) * TW, (index // 2) * TH
    return base[y:y + TH, x:x + TW]


def luminance(rgb: np.ndarray) -> np.ndarray:
    lum = rgb[..., 0] * .299 + rgb[..., 1] * .587 + rgb[..., 2] * .114
    lo, hi = np.percentile(lum, 1.5), np.percentile(lum, 98.5)
    return np.clip((lum - lo) / (hi - lo), 0, 1)


def ramp(lum: np.ndarray, stops: list[tuple[float, str]]) -> np.ndarray:
    positions = np.array([p for p, _ in stops])
    colours = np.array([[int(c[i:i + 2], 16) / 255 for i in (1, 3, 5)] for _, c in stops])
    out = np.empty(lum.shape + (3,), np.float32)
    for channel in range(3):
        out[..., channel] = np.interp(lum, positions, colours[:, channel])
    return out


def derive(source: int, stops, gamma: float = 1.0, flip: bool = False, rotate: int = 0) -> np.ndarray:
    t = tile(source)
    if flip:
        t = t[:, ::-1]
    if rotate:
        t = np.rot90(t, rotate)
    lum = luminance(t) ** gamma
    return ramp(lum, stops)


tiles = [
    # 64 — rhodonite: rose body, black manganese veins (structure of red jasper).
    derive(1, [(0, "#1c0c10"), (.20, "#5b1f33"), (.48, "#b2486f"), (.75, "#d9789a"), (1, "#f2b6c8")], .85, flip=True),
    # 128 — carnelian: warm translucent-looking orange rendered opaque (ochre mottling).
    derive(2, [(0, "#3a1004"), (.25, "#8c2c08"), (.55, "#cf5a14"), (.82, "#ec8a36"), (1, "#f8c48a")], 1.1, rotate=1),
    # 256 — howlite: ivory stone with grey-black matrix web (structure of turquoise).
    derive(5, [(0, "#2a2826"), (.22, "#6d6862"), (.45, "#b9b2a6"), (.7, "#e3ddd0"), (1, "#f7f3ea")], .75, flip=True),
    # 512 — obsidian / black onyx with smoky grey banding (malachite bands).
    derive(0, [(0, "#060607"), (.35, "#17171b"), (.62, "#2f3036"), (.85, "#55565e"), (1, "#8a8b93")], 1.15, rotate=2),
    # 1024 — tiger's eye: chatoyant gold-brown swirls (charoite swirls).
    derive(3, [(0, "#1d0f04"), (.28, "#5a3410"), (.55, "#a8701e"), (.8, "#c9933a"), (1, "#e6c27a")], 1.3, rotate=1),
    # 2048+ — ruby zoisite inspired crimson with deep wine inclusions (lapis mottling).
    derive(4, [(0, "#1a0308"), (.25, "#55091d"), (.52, "#9e1b32"), (.78, "#cf3d52"), (1, "#f08a90")], .9, flip=True),
]

atlas = np.zeros_like(base)
for i, t in enumerate(tiles):
    x, y = (i % 2) * TW, (i // 2) * TH
    atlas[y:y + TH, x:x + TW] = t
image = Image.fromarray((np.clip(atlas, 0, 1) * 255).astype(np.uint8), "RGB")
# A very light unsharp pass restores micro-detail flattened by the ramp.
image = image.filter(ImageFilter.UnsharpMask(radius=1.2, percent=40, threshold=2))
image.save(DRAWABLE / "mineral_atlas_high.png", optimize=True)
print("wrote", DRAWABLE / "mineral_atlas_high.png", image.size)
