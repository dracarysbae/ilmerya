# İlmerya iOS host

The SwiftUI host displays the shared Compose game and supplies a native AdMob normal interstitial adapter. The common game-over gate decides when to show each game-end ad; the adapter preloads and never displays an ad simply because a load finishes. Missing inventory, consent, or a valid presenter releases the gate immediately. Dismissal and presentation failure complete the pending callback once. Ads older than one hour are discarded.

This host was added on Windows. It has **not been compiled or run with Xcode**. Kotlin shared Android tests do not validate Swift interop, Apple linking, consent forms, or iOS ad delivery. Existing iOS sound and haptic implementations remain no-op implementations.

## Build on a Mac

Install Xcode 16 or newer, a compatible Java 17+ JDK, the Android SDK required by the existing root Gradle project, and XcodeGen 2.42 or newer. Replace the Windows `sdk.dir` in the root `local.properties` with this Mac's Android SDK path. Select the JDK for Gradle in the shell launching Xcode.

```sh
cd iosApp
xcodegen generate
open Ilmerya.xcodeproj
```

Select the **Ilmerya** scheme and an iOS simulator, then Run. The Xcode build phase calls `:shared:embedAndSignAppleFrameworkForXcode`, including shared Compose resources. It links the static `Shared` framework and resolves pinned Swift packages:

- Google Mobile Ads **13.10.0**
- Google User Messaging Platform **3.1.0**

A command-line simulator check, after project generation:

```sh
xcodebuild -project Ilmerya.xcodeproj -scheme Ilmerya \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Debug uses only Google's dedicated iOS sample app ID and interstitial ID. The build rejects a Debug app ID override, and the Swift adapter hardcodes the test unit under `#if DEBUG`. Do not click real ads during development.

## Production configuration

`Config/Release.xcconfig` contains the ILMERYA iOS IDs created in AdMob on 2026-10-03:

```xcconfig
ADMOB_APP_ID = ca-app-pub-1875904677314834~6655058128
ADMOB_INTERSTITIAL_ID = ca-app-pub-1875904677314834/2541476737
```

The first value is the iOS AdMob app ID; the second is its normal interstitial. Configure your Apple signing team in Xcode or an optional `Config/Production.xcconfig` containing `DEVELOPMENT_TEAM = your-team-id`. Do not leave empty ad-ID overrides in that file. Release archives reject missing, malformed, or Google test IDs. The new AdMob app still needs its store listing and review.

Create and publish the applicable privacy messages in the AdMob app's Privacy & messaging area. The adapter requests current UMP consent at launch, gates all SDK initialization and requests on `canRequestAds`, and exposes the required privacy-options form through shared settings. Changing privacy options invalidates previously loaded ads and in-flight results. It does not request ATT authorization or opt users into tracking. Before distribution, complete the actual App Store privacy disclosures, privacy-policy URL, signing and app icon; those cannot be inferred from this code.

## Verify before release

On a Mac/iPhone, finish two real games and confirm one test interstitial per game, dismissal returns to the result flow, and no ad interrupts play. Repeat offline and with no fill, rapidly tap result actions, and background/foreground during ad display. Verify first-launch consent and privacy-options updates, and check that scores/settings persist after relaunch. Do not claim a release is ready until the iOS build and these native checks pass.

The app pauses on inactive/background transitions and persists preferences and best score in its own UserDefaults namespace. The app privacy manifest declares that storage access; Google SDKs carry their own manifests. The host disables multiple scenes so a single ad manager cannot target a different game window.

## Official references checked on 2026-09-29

- [Google iOS quick start and SKAdNetwork identifiers](https://developers.google.com/admob/ios/quick-start)
- [Normal interstitial integration](https://developers.google.com/admob/ios/interstitial)
- [UMP consent and privacy options](https://developers.google.com/admob/ios/privacy)
- [Mobile Ads 13.10.0 package manifest](https://github.com/googleads/swift-package-manager-google-mobile-ads/blob/13.10.0/Package.swift)
- [UMP 3.1.0 package manifest](https://github.com/googleads/swift-package-manager-google-user-messaging-platform/blob/3.1.0/Package.swift)
- [Kotlin direct integration](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-direct-integration.html)
