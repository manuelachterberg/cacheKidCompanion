# CacheKidCompanion

![CacheKid Companion](assets/branding/cacheKidLogo.png)

Kid-friendly geocaching companion with e-ink display: follow the arrow, not an app.

## Current state

This repository now contains the first working mission intake, transfer, and kid-mode prototype flow.

Implemented:

- Android kid app in Kotlin with a local WebView UI shell
- browser-side navigation math for target bearing, distance, and arrow direction
- browser geolocation fallback
- Android native bridge with:
  - native permission request hook
  - native heading stream from Android rotation sensor
  - native location stream from Android `LocationManager`
  - BLE capability status hook
- versioned host mission domain:
  - `MissionDraft`
  - `MissionTarget`
  - mission package schema and manifest types
- Android prototype host share intake:
  - `ACTION_SEND` text payloads
  - `coord.info/GC...` links
  - partial vs resolved import states
  - Geocaching login/session capture in a host-side WebView
- first parent-side resolution flow:
  - detect shared cache code
  - manually complete missing title and coordinates
  - create a `MissionDraft`
- mission package writer, local receiver, and local transfer sender
- first child-only mission mode prototype on the Meebook
- simple e-ink-friendly black/white UI

## Architecture

The current repository contains two things at once:

- the actual target runtime: `iPhone host -> Android Meebook kid device`
- an Android-only prototype host flow that helped define mission intake, packaging, and transfer

The Android host path is now reference/prototype code, not the long-term product direction.

The intended split is:

- `iphone host`
  - cache intake from the Geocaching app
  - authenticated online cache resolution against the logged-in Geocaching session
  - mission creation
  - route and waypoint preparation
  - mission transfer to the Meebook
- `shared mission domain`
  - cache import parsing
  - resolution state
  - mission draft creation
  - mission package schema
- `android meebook kid app`
  - local package receiver
  - local mission storage
  - offline map rendering against a device-local offline map base
  - route / waypoint / `X` overlays
  - GPS / heading / BLE sensor integration
  - child-facing mission UI

The bridge contract remains intentionally capability-driven. The WebView UI can still fall back to browser features when `window.AndroidHost` is absent, but the Meebook kid app is the primary Android runtime.

### Data flow

```text
Geocaching App on iPhone
        |
        | share link, usually coord.info/GC...
        v
CacheKid Host App on iPhone
        |
        | extract GC code
        | resolve cache online
        | build MissionDraft / MissionPackage
        v
Local transfer to Meebook
        |
        | hotspot / local Wi-Fi / later transfer channel
        v
CacheKid Kid App on Meebook
        |
        | store mission locally
        | render route, treasure target, and local offline base map
        | use on-device GPS / heading
        v
Offline kid navigation experience
```

Rule of thumb:

- iPhone = online host and mission builder
- Meebook = offline mission player with a locally installed offline map base

Important resolver rule:

- anonymous `coord.info` fetches are not reliable enough for exact coordinates
- the long-term exact resolve path belongs to an authenticated host session
- anonymous HTML fetch and public geocoding are fallback behavior only

### Component flow

```text
iPhone host app
---------------
Geocaching share intake
  -> cache link / code extraction
  -> online cache resolution
  -> route / waypoint preparation
  -> MissionDraft
  -> MissionPackage writer
  -> local transfer client

Meebook kid app
---------------
local transfer receiver
  -> MissionPackage validator
  -> local mission storage
  -> local offline map base
  -> route / waypoint / X overlays
  -> kid-facing navigation UI
  -> GPS / heading providers
```

### Offline map direction

The current repository should no longer assume mission-specific OSM fetching on the Android host.

The intended map architecture is:

- the Meebook stores an offline map base for a region or country
- mission packages carry overlays and mission metadata, not a full generated map
- the kid UI renders:
  - local offline map base
  - fixed player position `O`
  - treasure target `X`
  - route / waypoints

Mission-specific generated map assets may still exist as prototype or fallback code, but they are not the long-term architecture.

### Kid map presentation

The child map should stay:

- grayscale and e-ink-friendly
- text-light to text-free
- quiet in motion
- vertically split:
  - compass on top
  - map below

The intended map camera style is:

- subtle 3D tilt only
- e-ink-safe and low-motion
- no constant cinematic camera movement
- north fallback when no reliable heading is available

## BLE integration

The BLE path is Meebook-side and not sensor-specific yet.

You need to replace the placeholder UUIDs in:

- `app/src/main/java/com/cachekid/companion/data/BleSensorConfig.kt`

After that, the next step is to add:

- BLE scan by service UUID
- GATT connection
- characteristic notifications for heading / gyro / angle
- forwarding parsed values through the existing JS bridge

## Run

Open the project in Android Studio and let it create or sync the Gradle wrapper locally if needed.

The Android app currently loads:

- `file:///android_asset/web/index.html`

Default target coordinate on first launch:

- `52.520008,13.404954`

## Next steps

Recommended next implementation steps:

1. Stop treating Android-host map generation as the primary path.
2. Recut the kid map around a device-local offline map base on the Meebook.
3. Keep mission packages focused on overlays, route, target, and mission metadata.
4. Define the real iPhone host responsibilities and transfer contract explicitly.
5. Continue simplifying the child-only mission view.

## Engineering workflow

Repository rules are intentionally strict:

- `main` should only be updated through pull requests
- frontend and Android unit tests are expected to stay green in GitHub Actions
- non-trivial changes should reference an issue
- refactors are expected when a feature would otherwise increase coupling or duplication

See `CONTRIBUTING.md` for the full contribution rules and pull request expectations.
