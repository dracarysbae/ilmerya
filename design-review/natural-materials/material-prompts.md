# Natural material sources

Created on 2026-09-29 with the built-in image_gen tool. These are generated material textures inspired by natural materials, not photographs of identified geological specimens.

The original generated PNGs are stored unchanged in the project:

- `shared/src/commonMain/composeResources/drawable/mineral_atlas.png`
- `shared/src/commonMain/composeResources/drawable/walnut_material.png`

The app clips the opaque textures into native Canvas geometry. Thickness, bevels, contact shadows, inscriptions and animation are rendered by the app. The atlas is sampled as two columns and three rows, with an inset to avoid boundary bleed.

## Mineral atlas prompt

Use case: photorealistic-natural. Asset type: game material texture atlas, one single bitmap, exact flat UV albedo atlas. Create a photorealistic 2-column by 3-row contact sheet filling the entire image with SIX equally sized square material swatches, perfectly straight shared boundaries, no padding no labels no frames. Top left: rich dark green opaque malachite with irregular mineral bands, deep emerald green. Top right: vivid warm rust-red opaque red jasper, red iron-rich stone with subtle irregular dark veins. Middle left: rich golden ochre yellow jasper with fine mineral mottling. Middle right: saturated purple opaque charoite-inspired mineral, violet and deep plum irregular swirls. Bottom left: intense ultramarine blue lapis lazuli with very scarce tiny dull gold inclusions, deep blue overall. Bottom right: vivid turquoise opaque mineral with fine charcoal matrix veins. Every tile is a seamless-looking macro photograph of a continuous honed slab of that stone, front-on orthographic, soft flat neutral light, dry tactile opaque solid material, microstructure visible, no shine glare, no transparent crystals, no gemstones or object silhouettes, no text numbers symbols branding or borders. Texture scale broad irregular natural mineral patterns, not tiny uniform noise. Medium contrasts so a small ivory or dark engraved number placed later remains readable. Crisp photographic material detail. This is material texture data, not a UI mockup. Aspect ratio 2:3, exactly two columns and three rows.

## Walnut prompt

Use case: photorealistic-natural. Asset type: single game material wood texture, UV albedo map. A macro photograph of a beautiful continuous solid natural American black walnut board viewed exactly straight down, perfectly flat orthographic, warm rich medium brown walnut, fine longitudinal fibres traveling from top to bottom, irregular subtle cathedral grain toward the upper right, very small restrained closed knots, pores and subtly varied brown grain, softly oiled and hand-finished matte, dry touchable real timber. Fill every pixel with the wood surface; no visible edge of a board, no plank seams, no holes, no surrounding surface, no frame, no objects, no text, no numbers. Consistent diffuse ambient illumination, no directional shadows or specular glare baked into the texture. Natural restrained material contrast and complex photographic microtexture, no exaggerated wavy cartoon lines. A luxuriously crafted tactile wooden game board texture, believable walnut brown, not orange or grey, not plastic, not stylized. Portrait 2:3 aspect ratio.
