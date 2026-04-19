# CacheKidCompanion

Kid-friendly geocaching companion with e-ink display: follow the arrow, not an app.

## Current state

This repository now contains a hybrid baseline:

- a web app UI that can also run inside an Android WebView host
- an Android host app in Kotlin for permissions, sensors, and future BLE integration

Implemented:

- local WebView-served web app from `app/src/main/assets/web/`
- browser-side navigation math for target bearing, distance, and arrow direction
- browser geolocation fallback
- browser device-orientation fallback
- Android host bridge with:
  - native permission request hook
  - native heading stream from Android rotation sensor
  - native location stream from Android `LocationManager`
  - BLE capability status hook
- simple e-ink-friendly black/white UI

## Architecture

The project is split into two runtime layers:

- `web app`
  - main UI
  - navigation logic
  - browser geolocation fallback
  - browser orientation fallback
- `android host`
  - WebView container
  - permission management
  - native sensor collection
  - future BLE and e-ink specific integrations

The bridge contract is intentionally capability-driven. The web app can run without the Android layer and enhance itself when `window.AndroidHost` exists.

## BLE integration

The BLE path is host-only and not sensor-specific yet.

You need to replace the placeholder UUIDs in:

- `app/src/main/java/com/cachekid/companion/data/BleSensorConfig.kt`

After that, the next step is to add:

- BLE scan by service UUID
- GATT connection
- characteristic notifications for heading / gyro / angle
- forwarding parsed values through the existing JS bridge

## Run

Open the project in Android Studio and let it create or sync the Gradle wrapper locally if needed.

The Android host loads:

- `file:///android_asset/web/index.html`

Default target coordinate on first launch:

- `52.520008,13.404954`

## Next steps

Recommended next implementation steps:

1. Add real BLE scan, connect, and characteristic notification handling in the Android host.
2. Formalize the bridge API with versioned message types.
3. Add a pure-browser dev mode so the same web app can be served outside Android during development.
4. Add cache target persistence and kid-safe fullscreen mode.

## Engineering workflow

Repository rules are intentionally strict:

- `main` should only be updated through pull requests
- frontend and Android unit tests are expected to stay green in GitHub Actions
- non-trivial changes should reference an issue
- refactors are expected when a feature would otherwise increase coupling or duplication

See `CONTRIBUTING.md` for the full contribution rules and pull request expectations.
