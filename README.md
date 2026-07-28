# Poltergeld — homescreen widget for Ghostfolio

A small, open-source Android homescreen **widget** that shows the positions from
your self-hosted [Ghostfolio](https://github.com/ghostfolio/ghostfolio) instance.
No trackers, no analytics, no third-party services — it talks only to the
Ghostfolio server you configure.

> Unofficial project. Not affiliated with Ghostfolio.

## Features

- Native homescreen widget (Jetpack Glance) showing your top 5 and flop 5
  holdings by performance plus the full list of all positions, with value and
  allocation.
- Total portfolio value **and overall performance** in the widget header, with
  the last refresh time and a manual refresh button.
- Switchable time horizon directly in the widget: 24h, week to date, month to
  date, 1 year (the ranges the Ghostfolio API supports — there is no rolling
  7d/30d).
- Responsive layouts: a small widget shows just the summary, larger ones add
  top/flop and the full list. Each widget instance can also be pinned to a
  fixed view via its configuration screen (long-press → reconfigure, or from
  the app's settings, which list every placed widget) — including a **custom
  watchlist** showing only the positions you pick.
- If a refresh fails, the widget keeps showing the last good data, marked with
  a ⚠ and the old timestamp, instead of replacing everything with an error.
- Detects auth proxies (e.g. the Umbrel app proxy) answering instead of
  Ghostfolio and says so explicitly.
- In-app portfolio overview with search, time-range chips, sorting (value /
  performance / name), asset-class filter and a detail page per position
  (Android widgets cannot host text input, so search lives in the app — one
  tap away from the widget).
- Tap a position — in the widget or the app — to open its detail page: a
  price-history chart plus the full buy/sell/dividend activity log for that
  holding.
- Optional **Privacy Mode**: hide monetary amounts by default in the widget
  and/or the app (two independent switches), revealed again with fingerprint
  or device PIN.
- Security token encrypted at rest via the Android Keystore; optional app lock
  (fingerprint / device PIN) that re-locks when the app leaves the foreground
  and, while enabled, blanks the recents preview and blocks screenshots.
- Guided first-run onboarding: pick your language, connect and test the server,
  optionally enable the app lock and pin the widget straight to the homescreen.
- English and German UI (selectable during onboarding and in the settings;
  follows the system language by default).
- Hourly background refresh via WorkManager.
- Dark, compact design that resizes on the homescreen.

## Privacy

- **No trackers and no analytics.** The dependency set is limited to AndroidX,
  Jetpack Glance, Kotlin coroutines and kotlinx.serialization. There is no
  Firebase, no Crashlytics, no ad or analytics SDK.
- Networking uses the platform `HttpURLConnection` — no third-party HTTP client.
- The only network traffic is to the Ghostfolio base URL you enter.
- Cleartext (plain `http://`) is allowed, because self-hosted Ghostfolio
  instances are commonly reached over HTTP on the local network. Prefer an
  `https://` URL whenever your instance offers TLS.
- Your Security Token is encrypted with an AES key held in the Android
  Keystore before being stored in the app's private DataStore.
- Permissions: `INTERNET` and `ACCESS_NETWORK_STATE` are declared by the app.
  `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` are pulled in by
  AndroidX WorkManager (used for the hourly background refresh) — none of them
  enable any form of tracking, and the app defines no analytics code paths.

## Setup

Install the APK (see below) and open **Poltergeld** — the onboarding walks you
through everything: language, base URL (e.g. `https://ghostfolio.example.com`,
no trailing path), your **Security Token** (Ghostfolio → *My Ghostfolio* →
*Security Token*) with a connection test, the optional app lock, and adding
the widget to your homescreen.

### Reaching a Ghostfolio behind a reverse proxy

The app needs the Ghostfolio **API** to be reachable directly. If your instance
sits behind an authentication proxy (for example the Umbrel app proxy, which
redirects unauthenticated requests to a login page), the API calls will fail.
Expose the Ghostfolio API on a reachable URL (direct container port, a reverse
proxy that forwards `/api`, or a VPN such as WireGuard/Tailscale) and use that
URL in the app.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 34, build-tools 34.0.0).

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # minified release APK
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Release signing

Create a keystore and a `keystore.properties` in the project root (both are
gitignored):

```properties
storeFile=release.keystore
storePassword=…
keyAlias=ghostfolio-widget
keyPassword=…
```

With the file present, `assembleRelease` produces a signed APK. Without it the
release build is unsigned.

## Install & updates

- **GitHub Releases + [Obtainium](https://github.com/ImranR98/Obtainium)**
  (recommended on GrapheneOS): add this repository's URL in Obtainium and it
  installs and updates the app straight from the signed release APKs. Releases
  are built by CI on every `v*` tag (`.github/workflows/release.yml`) using the
  keystore stored in the repository secrets `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`.
- **F-Droid:** the repository ships fastlane metadata
  (`fastlane/metadata/android/`), uses only free dependencies and builds
  reproducibly from source, so it is ready for an
  [fdroiddata](https://gitlab.com/fdroid/fdroiddata) merge request.

### Building on ARM64 hosts (e.g. Raspberry Pi)

Google ships only x86_64 `aapt2`/`zipalign` in the Maven build-tools. On an
`aarch64` host, replace the build-tools binaries with native ARM64 builds (for
example from [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools))
and point Gradle at the native `aapt2`:

```properties
# gradle.properties
android.aapt2FromMavenOverride=/path/to/build-tools/34.0.0/aapt2
```

## API

The client talks to these Ghostfolio REST endpoints (all with
`Authorization: Bearer <token>` except the auth call itself):

1. `POST /api/v1/auth/anonymous` with `{"accessToken": "<token>"}` → bearer token.
2. `GET /api/v1/portfolio/holdings` — the position list shown in the widget and overview.
3. `GET /api/v1/user` — base currency (cached, rarely changes).
4. `GET /api/v1/portfolio/performance` — overall portfolio performance for the selected range.
5. `GET /api/v1/portfolio/holding/{dataSource}/{symbol}` — price history and
   exact figures for a single position's detail page.
6. `GET /api/v1/activities?symbol=&dataSource=` — buy/sell/dividend/fee history
   for a single position's detail page.

## Support

If Poltergeld is useful to you, you can leave a tip via Lightning:
**⚡ `tip@muota.li`** (also reachable from the app's *About* section).

## License

[MIT](LICENSE)
