# Offline Map Architecture

CacheKid needs a real kid-device offline map, not mission-specific static background images.

## Target

The Meebook stores one or more installable offline map packages. A mission package carries route, waypoint, player, and target data only. At runtime the kid map composes:

- a device-local offline basemap package
- mission route and waypoint overlays
- the fixed treasure `X`
- the current player `O`

## Package Format

The target package format is a directory under the device offline-map root:

```text
offline-maps/<package-id>/
  offline-map.json
  map.pmtiles
  style.json
```

`offline-map.json` is the package manifest:

```json
{
  "id": "de-ni",
  "displayName": "Lower Saxony",
  "version": "2026.04",
  "format": "pmtiles-vector",
  "tileAssetPath": "map.pmtiles",
  "styleAssetPath": "style.json",
  "minZoom": 0,
  "maxZoom": 14,
  "attribution": "OpenStreetMap contributors",
  "bounds": {
    "minLatitude": 51.20,
    "minLongitude": 6.30,
    "maxLatitude": 53.90,
    "maxLongitude": 11.70
  }
}
```

The initial supported format is `pmtiles-vector`. This matches the current MapLibre Android dependency line, which supports PMTiles URL protocols in recent 11.x releases. The package must include a local `style.json` that references the local PMTiles archive and keeps the kid presentation label-light or label-free.

## Runtime Rules

- The kid app must only treat packages with `offline-map.json`, `map.pmtiles`, and `style.json` as real offline maps.
- Legacy `map.png` / `map.svg` basemaps are prototype fallback assets, not proof that offline maps are installed.
- A mission target outside all installed package bounds is a missing-map state. The runtime must not silently choose the nearest unrelated map package.
- Online OSM raster tiles are a development fallback only and should not be the production kid-map default.

## Follow-Up Work

- #26 installs and validates offline map packages on-device.
- #25 wires the kid renderer to local PMTiles styles and removes the online raster default from the production path.
- #27 tracks source selection and tile-generation/licensing decisions for the chosen PMTiles basemap.
