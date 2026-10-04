# Natural walnut and mineral materials — verification

2026-09-29

- Android debug compilation: PASS, including bundled Compose resources.
- Existing rules and clock tests: 16 passed, zero failures or errors. The final follow-up changes affect drawing only; final Android assembly also passed.
- Native home and game screens checked on API 36.1 at 720 × 1600, density 280.
- Opaque textures are bundled in the app; no network request is needed for material rendering.
- The walnut board has a broad inner wall, directional occlusion and a floor with continuous grain. Socket floors sample the same wood coordinates as the surrounding slab.
- Stone bodies, shoulders and faces sample the same mineral region; native shading supplies thickness. The 8 stone uses the turquoise swatch at the user's request, with column-specific crops and tinted variations above 64. The atlas retains its original six source swatches.
- Numerals are inlaid in ivory or charcoal with a small edge shadow. Readability was visually checked on textured stones; the earlier flat-palette contrast calculation is not a guarantee for all texture pixels.
- Landing indicators use an empty-socket outline rather than a translucent stone. Match outlines are 1.25 dp.
- Home and gameplay use the same material resources; Turkish and English player-facing copy now refers to stones.
- Existing fall, Cycle, fusion, reduced-motion, sound and haptic behaviour remains in place.
- Native taps exercised Cycle on a mixed column and subsequent placements; `motion.mp4` records the turquoise-stone build. No AndroidRuntime error appeared in the captured log.
- Physical-device frame pacing and haptic feel were not measured. iOS was not built on Windows.

Screenshots come from the running Android application, not a generated UI mockup. Texture-generation provenance and full prompts are in `material-prompts.md`.

APK: `artifacts/ilmerya-natural-materials-debug.apk`.
