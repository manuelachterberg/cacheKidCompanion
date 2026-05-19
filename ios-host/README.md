# CacheKid iOS Host App

iPhone Host App for CacheKid Companion.

> **Note:** This is the iPhone host app. The Android host code in `app/` is prototype/reference only (per AGENTS.md).

## Architecture

```
ios-host/
├── CacheKidHost/              # SwiftUI Main App
│   ├── CacheKidHostApp.swift  # App Entry Point
│   ├── ContentView.swift      # Main UI
│   ├── Features/
│   │   ├── Share/             # Share Extension Integration
│   │   └── MissionBuilder/    # Mission Builder UI
│   └── Shared/
│       └── Models/            # Shared Swift Models
├── CacheKidHostShareExtension/ # iOS Share Extension
└── README.md
```

## Features

### Phase 1: Share Extension (Current)
- Registers as iOS Share Sheet target
- Receives cache links/text from Geocaching apps
- Stores shared content in shared UserDefaults
- Opens main app via custom URL scheme (`cachekid://import`)

### Phase 2: Mission Builder (Planned)
- Parse imported cache data
- Build mission with waypoints
- Select offline map region

### Phase 3: Transfer (Planned)
- Local HTTP server for kid device download
- Bonjour discovery

## Building

Open in Xcode 15+:
```bash
open ios-host/CacheKidHost.xcodeproj
```

Requires:
- iOS 16.0+
- Xcode 15.0+
- Swift 5.9+

## Shared Models

Swift models mirror the Kotlin data classes in `app/src/main/java/.../mission/`:
- `MissionTarget` → `LatLng`
- `MissionWaypoint` → `MissionWaypoint`
- `ActiveMission` → `ActiveMission`
- `MissionPackage` → Mission package format
