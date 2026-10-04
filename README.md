# Ilmerya / İlmerya

A native Kotlin Multiplatform stone puzzle. Android is the verified target for this iteration.

## Identity

Ilmerya is a coined working name, inspired by the Turkish word “ilmek” (a loop or stitch). It is not presented as a historical or mythological name. An exact-name web search on 2026-09-29 returned no results; this is not trademark clearance. Bloxboom was reviewed as a visual quality reference. The new game has its own hexagonal crystal art, palette, identity, and rules.

## Rules

- A 5 × 7 offset hex grid has six-way adjacency.
- Select a column, inspect the landing outline, and place the current crystal.
- Three or more connected equal crystals fuse into one crystal of twice the value. The survivor is placed at the deepest cell, with a deterministic centreward tie break.
- Gravity settles each column. Repeat until there are no further matches.
- Each wave scores `new value × group size × wave index` and earns one energy. Energy is capped at six.
- **Devir / Cycle** costs three energy: move a column's bottom crystal above its current stack, shifting the others down, then resolve any matches. It does not consume the current stone or queue. Empty, one-stone, and uniform columns cannot cycle.
- A full board remains playable if there is a charged Cycle available. Without a legal placement or Cycle, the run ends.
- Calm has no timer. Flow starts at six seconds per placement, reducing by 0.4 seconds every 18 placements to a 2.4-second floor. If the selected column fills, automatic placement uses the nearest open column. A full-board rescue freezes the clock.
- A shuffled twelve-stone bag reduces random droughts. Three future crystals are visible.
- Difficulty grows through new bags: values 2–16 initially, 2–32 after 18 placements, and 2–64 after 54. Already drawn stones and visible previews never change. Small stones remain available at every stage.

## Design and interaction

An anthracite surround holds a carved walnut board with a broad inner wall and recessed hex sockets. Generated photographic material textures provide continuous wood grain and opaque mineral surfaces inspired by malachite, red jasper, purple stone, lapis and turquoise. The 8 stone uses turquoise instead of yellow. Native Canvas geometry supplies rolled stone shoulders, visible sidewalls, contact shadows and inlaid numerals. Wood grain remains aligned between the board and socket floors; stone crops vary by column while remaining stable during motion. The landing marker is an outline in an empty socket. Material assets and exact generation prompts are recorded in `design-review/natural-materials/material-prompts.md`.

Buttons depress on touch. Stones descend in 140 ms; Cycle lifts the bottom stone around the stack in 380 ms. Matching-neighbour outlines and restrained merge ripples preserve board readability. Column selectors and actions are at least 48 dp high. The how-to dialog includes four interactive animated mineral diagrams and a static before/after mode under Reduce motion. The onboarding completion flag persists. Menu navigation offers Continue for an unfinished run; process termination still does not restore the board.

Settings independently control effects and music, their volumes, haptics, language, reduced motion, score previews and board-danger cues. All persist on Android/iOS. Settings are also reachable while paused. Audio uses an original 24-second stereo D-minor pentatonic loop, warm plucks, bass and pads; short resonant stone effects replace the earlier bleeps. PCM generation is shared; Android uses bounded AudioTrack playback and iOS uses AVAudioPlayer. The iOS implementation is not yet compiled on a Mac. The generated audition is `artifacts/ilmerya-original-score.wav`.

The weekly league follows the separate Kotlin + Render + PostgreSQL architecture used by `tetris-kmp`, with İlmerya's own seeded engine replay, Google Play Games identity, current/previous standings, best-run scoring, Monday 00:00 Istanbul settlement, and queued offline submissions. See `server/README.md`. It is not live yet: İlmerya needs its own deployment and Play Games configuration. No simulated opponents are shown. The iOS league identity adapter is not implemented; the UI reports unavailable there.

## Build and verify (Windows)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'C:\Users\ahmet\.gradle'
.\gradlew.bat :androidApp:assembleDebug :shared:testDebugUnitTest --console=plain
```

The 31 shared/Android JVM checks cover engine rules, clock, ad gating, seeded replay verification, invalid/fast-forwarded ranked moves, audio headroom, loop seams and audition export. A 288-run seeded balance audit compares three distributions across three strategies; selected-distribution median survival is 90 placements for random play and 300.5 for a one-step planning policy. These simulations are not human playtests. See `design-review/balance/balance-results.md`.

The Android instrumentation suite in `ExperienceTest.kt` checks the tutorial, settings and unavailable league state. On 2026-10-03 its APK compiled, but the API 36.1 emulator lost Android activity/appops services before any test ran. A second boot also failed to expose the activity service; visual/device verification remains outstanding. This is not a passing UI test.

Latest APK: `artifacts/ilmerya-experience-debug.apk` (Google test ads). Gradle output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Latest visual QA: `design-review/natural-materials/`. Earlier porcelain and anthracite captures remain in `design-review/` for comparison.
Previous source snapshots are retained in `.design-backup/`; the preceding anthracite source is in `before-sculpted-anthracite.zip`.

## Known boundaries

- iOS now has a native audio implementation; haptics remain a placeholder. iOS cannot be built on Windows.
- The inherited Kotlin 2.0.21 / AGP 8.13.2 combination emits a compatibility warning; Android compilation and JVM tests are the verification used here.
- The Android application ID remains `com.bloxtrix.hexdrop` to preserve installation continuity; display names and launcher art use Ilmerya.
- Android and iOS native interstitial adapters use a shared once-per-completed-run gate. No-fill and presentation failure release results immediately. Pauses and restarts do not trigger ads. Production AdMob IDs are configured separately from debug test IDs; store linkage, AdMob review, applicable consent messages and native-device checks remain release requirements. See `androidApp/ADS_SETUP.md` and `iosApp/README.md`.
- Longer human play sessions are still needed to tune difficulty and assess the feel on physical devices.

