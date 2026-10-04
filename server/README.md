# İlmerya weekly league

The independent İlmerya service follows the Kotlin / Render / Neon PostgreSQL architecture in `tetris-kmp`. It does not share Bloxboom player tables, OAuth credentials, scores or its production endpoint. The exact İlmerya engine and seeded stone bag are compiled into both Android and the server; a client-supplied score is never trusted.

The best completed Calm run counts each week. Monday 00:00 Europe/Istanbul closes the week; earlier achievement breaks ties. Runs expire after six hours or at the weekly boundary. A new ticket replaces an unfinished run. Replay validation checks legal moves, energy, final game-over, event limits and minimum action timings. This prevents forged scores, but is not proof of human play; bot detection and device attestation are not included.

## Local verification

Use Java 21 and `gradlew.bat -p server test installDist`. Run `server/build/install/ilmerya-league/bin/ilmerya-league.bat`. Default binding is localhost:8080. `GET /health` must identify `game: ilmerya` and `ruleset: 1`. Local SQLite is for development. Guest accounts are disabled by default and must stay disabled publicly.

## Deployment

Build `server/Dockerfile` with the repository root as its context. On Render create a separate Free service; use a separate persistent Neon PostgreSQL database. Do not point at Bloxboom's database. The image refuses to start without PostgreSQL. Configure these in the service environment, never in source or APK:

- `ILMERYA_JDBC_URL`: PostgreSQL JDBC URL, with `sslmode=verify-full`.
- `PGS_APP_ID`, `PGS_WEB_CLIENT_ID`, `PGS_WEB_CLIENT_SECRET`: İlmerya's verified Google Play Games server identity.
- `ALLOW_GUEST_ACCOUNTS=false`.

Android build properties are `ILMERYA_API_URL` (HTTPS), `ILMERYA_PGS_APP_ID`, and `ILMERYA_PGS_WEB_CLIENT_ID`. No secret goes in the app. Configure the Android package `com.bloxtrix.hexdrop`, correct signing SHA-1, and test players in Play Console. The app checks the service game/ruleset before exchanging an authorization code.

Until these are supplied, the league screen explicitly shows setup is incomplete and no fabricated players. The Android implementation includes Google Play Games login, current/previous standings, replay submission, bounded persisted offline retry and account deletion. iOS currently shows the unavailable state; a separate Game Center or shared cross-platform identity implementation is still required.

This service has not yet been deployed. Render sign-in was blocked by automatic approval review; explicit authorization was requested. Do not infer live availability from successful local tests. Free hosting may sleep; the client waits up to 70 seconds without freezing gameplay. Privacy disclosures and real-account/device tests must be completed before distribution.
