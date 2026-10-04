# Android game-over interstitials

The Android host provides a retained `AndroidAdsManager` to the shared game-over flow. It preloads one interstitial after UMP permits ad requests, consumes it at game over, then preloads the next one after dismissal or a presentation error. Ads older than one hour are discarded. Missing inventory, offline loading, an inactive Activity, or denied consent never traps the player behind a loading screen or produces a late ad during the next game.

## Build configuration

Debug builds always use Google's official test app ID `ca-app-pub-3940256099942544~3347511713` and Android interstitial unit `ca-app-pub-3940256099942544/1033173712`, regardless of production properties.

Release builds use the ILMERYA Android app ID `ca-app-pub-1875904677314834~1610529280` and normal interstitial `ca-app-pub-1875904677314834/1855081025`, created in AdMob on 2026-10-03 and recorded in root `gradle.properties`.

Set `ADMOB_ANDROID_INTERSTITIAL_ID` to an **Android interstitial** unit created in AdMob. Supply it in Gradle properties, as an environment variable, or with `-PADMOB_ANDROID_INTERSTITIAL_ID=...`. Gradle properties take precedence. The previous `/3382059031` unit was rewarded inventory and must not be reused. `validateReleaseAds`, wired into `preReleaseBuild`, rejects missing/malformed IDs, Google's test IDs, and that old rewarded ID.

Production identifiers are configured. The debug APK serves test inventory only. The new app is not yet linked to a store listing; AdMob review and native device verification remain necessary before release.

## Consent and lifecycle

UMP consent information is updated at each new app/game-session launch. Required consent forms wait for a resumed Activity. Ad SDK initialization and ad requests depend on UMP's `canRequestAds()`. Configure and publish the applicable messages for the production app in AdMob **Privacy & messaging**. UMP's required privacy-options entry is exposed to the shared settings UI; changing choices discards existing ad inventory.

The manager is retained in Android `GameSession`, holds only a weak resumed Activity reference, and clears callbacks and cached ads when the session is disposed. Activity pauses do not prematurely complete an on-screen ad; only dismissal or SDK failure completes the shared game-over callback. The manager does not add a custom close timer or hide the SDK's close control.

The project remains on its existing Google Mobile Ads SDK 23.3.0 because later versions require a Kotlin toolchain upgrade. UMP is explicitly pinned to 4.0.0. Upgrade the Kotlin/Compose/Ads toolchain together before the legacy SDK reaches its supported serving limit.

## Device verification before release

On 2026-10-03, the debug APK built successfully and all 25 shared JVM tests passed, including repeated game-over events, stale callbacks, delayed result presentation, and no-fill fallback. Generated Debug/Release configuration was checked for the correct separate test/production IDs. Native ad presentation and UMP dialogs have not yet been verified on a device. The installable test artifact is `artifacts/ilmerya-balanced-ads-debug.apk`.

- Verify automatic interstitial presentation at the first game over and again at the next game over, and a single result dialog after each dismissal.
- Verify no-fill/offline behavior, background/foreground transitions, and Activity recreation during an ad.
- Verify UMP on a test device configured for a consent-required region, including rejection and reopening privacy options.
- Keep test IDs during testing. Publish only after the production IDs, store privacy disclosures, and AdMob consent messages are configured.

References: [Android interstitial integration](https://developers.google.com/admob/android/interstitial), [UMP integration](https://developers.google.com/admob/android/privacy?hl=en), [Google Mobile Ads release notes](https://developers.google.com/admob/android/rel-notes).
