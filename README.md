# İlmerya / Ilmerya

A Kotlin Multiplatform stone-merge puzzle for Android and iOS, with a separate Kotlin/JVM weekly-league service.
Package / bundle ID: `com.ozgames.ilmerya` (the source namespace stays `com.bloxtrix.hexdrop`). Developer: OzGAMES.
Private repository: `dracarysbae/ilmerya`.

## Rules

- 5 × 7 offset hex grid, six-way adjacency. Choose a column and place the current stone.
- Three or more connected equal stones fuse into one stone of twice the value at the deepest cell (centreward tie break). Gravity settles; new matches cascade.
- Each wave scores `new value × group size × wave index` and earns one energy (cap 6).
- **Devir / Cycle** (3 energy) moves the selected column's bottom stone to the top; the queue is untouched. A full board stays playable while a Cycle exists.
- Calm has no timer. Flow starts at 6 s per placement, −0.4 s every 18 placements, floor 2.4 s.
- Twelve-stone shuffled bags: 2–16 at start; new bags add 32 after 18 placements and 64 after 54. Visible queue and current bag never change at a threshold; small stones never retire.
- Rules are compiled into both the app and the server (`RULESET_VERSION = 1`). Presentation waves (`resolveWaves`) are tested to equal `processBoard`.

## Experience

- Anthracite surround, carved walnut board, opaque mineral stones. 2–32 use the generated base atlas (malachite, red jasper, turquoise instead of yellow, charoite, lapis); 64+ use `mineral_atlas_high.png` derived by `tools/derive_minerals.py` (rhodonite, carnelian, howlite, obsidian, tiger's eye, ruby zoisite).
- Wave-by-wave animation: drop → stones gather into the surviving socket → fuse with ring, mineral chips and score → gravity. Cascades show "ZİNCİR ×n". Sounds and haptics fire per wave; input is locked while a turn animates; the game-end ad and result wait for the last wave. Reduce motion shows results immediately.
- Unfinished runs persist across process death (`persistence/SavedRun.kt`). Ranked runs are rebuilt from seed + recorded moves (storage cannot alter them); an expired ticket continues as free play; a finished ranked run is resent if the process died before it was queued.
- Back: pauses play, leaves the result and league screens. Starting a new game over an unfinished one asks first.
- Settings: effects/music toggles and volumes, haptics, Türkçe/English, reduce motion, score preview, full-board warning, ad privacy choices, version.
- Audio: original 24 s stereo loop and synthesized stone effects (`audio/AudioScore.kt`). `tools/analyze_audio.py` checks levels, clipping, release clicks, harsh-band energy and loop seam. Effects are pitched for phone speakers.

## League

Shared client `competition/LeagueRepository.kt` (Android: Google Play Games; iOS: Game Center via `iosApp/Sources/GameCenterAuth.swift`). Only players who joined are reconnected silently; deleting the account clears the device data and stops automatic sign-in. Server: see `server/README.md`. No league is shown as live until the service URL and identities are configured.

## Build (Windows)

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='C:\Users\ahmet\.gradle'
.\gradlew.bat :androidApp:assembleDebug :shared:testDebugUnitTest
.\gradlew.bat :androidApp:assembleQa        # release code path, R8, Google test ads, debug key
.\gradlew.bat :androidApp:bundleRelease     # signed with the upload key if present
.\gradlew.bat -p server test installDist
```

Toolchain: Kotlin 2.3.10, Compose Multiplatform 1.10.0, AGP 8.13.2, Gradle 8.13, compile/target SDK 36, Google Mobile Ads 25.4.0, UMP 4.0.0, Play Games v2 22.0.0.

Android upload key: `%USERPROFILE%\.ilmerya-signing\` (outside the repository; back it up). Release ad IDs come from `gradle.properties`; `validateReleaseAds` rejects test IDs.

iOS is built on GitHub Actions (`.github/workflows/ios-simulator.yml`, `ios-testflight.yml`, Xcode 26). Signing secrets live in the `apple-distribution` environment; see `iosApp/README.md`.

## Verification status

See `docs/STATUS.md` for what has been verified, how, and what remains.
