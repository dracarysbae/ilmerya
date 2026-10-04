# Verification — 2026-09-29

- Android debug assembly: PASS. Final APK copied to `artifacts/ilmerya-debug.apk`.
- Common/JVM unit tests: 16 passed, 0 failed, 0 skipped (4 clock tests, 12 rules tests).
- Rules suite includes 30 deterministic simulated games with up to 150 actions each.
- Native Android menu: checked at 1080 × 2400 and 720 × 1600. Reduced the hero orbit to prevent overlap with the explanatory card.
- Native first-play tutorial: checked; Turkish text fits and remains scrollable.
- Native gameplay: checked at 720 × 1600. Current crystal, three previews, board, ghost, column controls, charge, and actions are visible without clipping.
- Actual interaction: placed two stones, used Cycle, verified their order changed and energy fell from 3 to 0 without consuming the next stone.
- Played a further twelve placements across columns. Merges raised score to 96 and replenished energy to 4. The next placement correctly showed a +48 merge preview and outlined the matching neighbours.
- Runtime AndroidRuntime error log: no application crash recorded in the tested session.
- Effects and haptics are implemented; physical-device audio, vibration strength, and frame pacing are not certified by this emulator test.
- The emulator initially showed Android System UI ANRs and disconnected once. After restarting and performing a clean application launch, the native gameplay smoke test completed. This is not a physical-device performance benchmark.
- iOS was not compiled on Windows; inherited iOS audio/haptic stubs are unchanged.

Images: `home.png`, `game.png`, `cycle.png`, `playing.png`. These are native app captures, not design mockups.
