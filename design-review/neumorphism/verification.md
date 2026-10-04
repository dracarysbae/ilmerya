# Porcelain neumorphism — verification

2026-09-29

- Android debug build: PASS.
- Existing rules and clock tests: 16 passed; no failures.
- Native home, first-play tutorial, and game screens checked at 720 × 1600, density 280.
- Warm porcelain palette applies to the game, dialogs, settings, status/navigation bars, splash screen, and launcher icon.
- Menu art, controls and board use dual shadows; hex sockets are recessed and ceramic pieces have softened corners and a raised shoulder.
- Primary/secondary actions have pressed states; selected columns use a recessed state in addition to colour.
- Motion: 140 ms placement, 380 ms curved Cycle lift, restrained ring and elastic merge feedback. Reduced-motion preference skips travel and merge motion.
- Two native placements followed by Cycle exercised stone reordering and charge consumption. Continued with placements in neighbouring columns.
- The model blocks column changes while a move resolves, so the animated column cannot switch midway.
- The APK is a debug build. Physical-device frame pacing and vibration feel were not measured.

Captures are from the running Android app: home.png, dialog.png, game.png, playing.png; motion.mp4 records the actual Cycle and placement animations.
Previous source: `.design-backup/before-neumorphism.zip`.
APK: `artifacts/ilmerya-neumorphism-debug.apk`.
