#!/usr/bin/env python3
"""Build a small installable CacheKid offline map package.

The generated package is intentionally region-sized, not country-sized. It is
used to validate the real PMTiles renderer path on a kid device before we lock
down the larger map production/update workflow.
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import tempfile
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


try:
    import mapbox_vector_tile
    from pmtiles.tile import Compression, TileType, zxy_to_tileid
    from pmtiles.writer import write as write_pmtiles
    from shapely.geometry import LineString, MultiPolygon, Polygon, box, mapping
except ImportError as exc:  # pragma: no cover - exercised by users without deps.
    raise SystemExit(
        "Missing Python dependencies. Install with:\n"
        "  pip install pmtiles==3.7.0 mapbox-vector-tile==2.1.0\n"
    ) from exc


@dataclass(frozen=True)
class Bounds:
    min_latitude: float
    min_longitude: float
    max_latitude: float
    max_longitude: float

    def padded(self, padding_ratio: float) -> "Bounds":
        lat_padding = (self.max_latitude - self.min_latitude) * padding_ratio
        lon_padding = (self.max_longitude - self.min_longitude) * padding_ratio
        return Bounds(
            min_latitude=max(self.min_latitude - lat_padding, -85.0),
            min_longitude=max(self.min_longitude - lon_padding, -180.0),
            max_latitude=min(self.max_latitude + lat_padding, 85.0),
            max_longitude=min(self.max_longitude + lon_padding, 180.0),
        )


@dataclass(frozen=True)
class RenderFeature:
    layer: str
    geometry: Any
    properties: dict[str, Any]


def main() -> None:
    args = parse_args()
    bounds = Bounds(
        min_latitude=args.min_lat,
        min_longitude=args.min_lon,
        max_latitude=args.max_lat,
        max_longitude=args.max_lon,
    ).padded(args.padding)

    if args.overpass_json:
        map_data = json.loads(Path(args.overpass_json).read_text(encoding="utf-8"))
    else:
        map_data = fetch_overpass(bounds, args.overpass_endpoint)

    features = extract_features(map_data)
    if not features:
        raise SystemExit("Overpass data did not contain renderable map features.")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    build_package(
        package_id=args.package_id,
        display_name=args.display_name,
        version=args.version,
        bounds=bounds,
        min_zoom=args.min_zoom,
        max_zoom=args.max_zoom,
        features=features,
        output_zip=output,
    )
    print(f"Wrote {output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-id", required=True)
    parser.add_argument("--display-name", required=True)
    parser.add_argument("--version", default="dev")
    parser.add_argument("--output", required=True)
    parser.add_argument("--min-lat", type=float, required=True)
    parser.add_argument("--min-lon", type=float, required=True)
    parser.add_argument("--max-lat", type=float, required=True)
    parser.add_argument("--max-lon", type=float, required=True)
    parser.add_argument("--padding", type=float, default=0.15)
    parser.add_argument("--min-zoom", type=int, default=12)
    parser.add_argument("--max-zoom", type=int, default=15)
    parser.add_argument("--overpass-json")
    parser.add_argument(
        "--overpass-endpoint",
        default="https://overpass-api.de/api/interpreter",
    )
    return parser.parse_args()


def fetch_overpass(bounds: Bounds, endpoint: str) -> dict[str, Any]:
    query = f"""
        [out:json][timeout:60];
        (
          way["highway"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["waterway"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["natural"="water"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          relation["natural"="water"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["landuse"="forest"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["natural"="wood"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["building"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["leisure"="park"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["leisure"="grass"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["natural"="grassland"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
          way["landuse"="residential"]({bounds.min_latitude},{bounds.min_longitude},{bounds.max_latitude},{bounds.max_longitude});
        );
        out tags geom;
    """
    payload = urllib.parse.urlencode({"data": query}).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
            "User-Agent": "CacheKidCompanionOfflineMapBuilder/1.0",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.loads(response.read().decode("utf-8"))


def extract_features(map_data: dict[str, Any]) -> list[RenderFeature]:
    features: list[RenderFeature] = []
    for element in map_data.get("elements", []):
        geometry = element.get("geometry") or []
        points = [
            (point["lon"], point["lat"])
            for point in geometry
            if "lat" in point and "lon" in point
        ]
        if len(points) < 2:
            continue

        tags = element.get("tags") or {}
        layer = classify_layer(tags)
        if layer is None:
            continue

        polygon_layers = {"water", "land", "building", "park", "landuse"}
        if layer in polygon_layers and len(points) >= 4 and points[0] == points[-1]:
            shape = Polygon(points)
            if not shape.is_valid:
                shape = shape.buffer(0)
            if shape.is_empty:
                continue
        else:
            shape = LineString(points)
            if shape.is_empty or shape.length <= 0:
                continue

        features.append(
            RenderFeature(
                layer=layer,
                geometry=shape,
                properties={
                    "class": classify_class(tags),
                    "name": tags.get("name", ""),
                },
            )
        )
    return features


def classify_layer(tags: dict[str, Any]) -> str | None:
    if tags.get("natural") == "water" or tags.get("waterway"):
        return "water"
    if tags.get("landuse") == "forest" or tags.get("natural") == "wood":
        return "land"
    if tags.get("building"):
        return "building"
    if tags.get("leisure") in {"park", "grass"} or tags.get("natural") == "grassland":
        return "park"
    if tags.get("landuse") == "residential":
        return "landuse"
    if tags.get("highway"):
        return "roads"
    return None


def classify_class(tags: dict[str, Any]) -> str:
    highway = tags.get("highway", "")
    if highway in {"motorway", "trunk", "primary", "secondary", "tertiary"}:
        return "major"
    if highway:
        return "minor"
    if tags.get("waterway"):
        return "waterway"
    if tags.get("natural") == "water":
        return "water"
    if tags.get("landuse") == "forest" or tags.get("natural") == "wood":
        return "forest"
    if tags.get("building"):
        return "building"
    if tags.get("leisure") in {"park", "grass"} or tags.get("natural") == "grassland":
        return "park"
    if tags.get("landuse") == "residential":
        return "residential"
    return "other"


def build_package(
    package_id: str,
    display_name: str,
    version: str,
    bounds: Bounds,
    min_zoom: int,
    max_zoom: int,
    features: list[RenderFeature],
    output_zip: Path,
) -> None:
    with tempfile.TemporaryDirectory() as temp_dir_name:
        temp_dir = Path(temp_dir_name)
        pmtiles_path = temp_dir / "map.pmtiles"
        write_vector_pmtiles(
            output_path=pmtiles_path,
            bounds=bounds,
            min_zoom=min_zoom,
            max_zoom=max_zoom,
            features=features,
        )
        (temp_dir / "offline-map.json").write_text(
            json.dumps(
                build_manifest(package_id, display_name, version, bounds, min_zoom, max_zoom),
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        (temp_dir / "style.json").write_text(build_style_json(min_zoom, max_zoom), encoding="utf-8")

        with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in ("offline-map.json", "map.pmtiles", "style.json"):
                archive.write(temp_dir / name, arcname=name)


def write_vector_pmtiles(
    output_path: Path,
    bounds: Bounds,
    min_zoom: int,
    max_zoom: int,
    features: list[RenderFeature],
) -> None:
    with write_pmtiles(str(output_path)) as writer:
        for zoom in range(min_zoom, max_zoom + 1):
            min_x, max_y = lonlat_to_tile(bounds.min_longitude, bounds.min_latitude, zoom)
            max_x, min_y = lonlat_to_tile(bounds.max_longitude, bounds.max_latitude, zoom)
            for tile_x in range(min_x, max_x + 1):
                for tile_y in range(min_y, max_y + 1):
                    tile = encode_tile(features, zoom, tile_x, tile_y)
                    if tile:
                        writer.write_tile(zxy_to_tileid(zoom, tile_x, tile_y), gzip.compress(tile))

        writer.finalize(
            {
                "tile_compression": Compression.GZIP,
                "tile_type": TileType.MVT,
                "min_lon_e7": round(bounds.min_longitude * 10_000_000),
                "min_lat_e7": round(bounds.min_latitude * 10_000_000),
                "max_lon_e7": round(bounds.max_longitude * 10_000_000),
                "max_lat_e7": round(bounds.max_latitude * 10_000_000),
                "center_lon_e7": round(((bounds.min_longitude + bounds.max_longitude) / 2) * 10_000_000),
                "center_lat_e7": round(((bounds.min_latitude + bounds.max_latitude) / 2) * 10_000_000),
                "center_zoom": min(max_zoom, max(min_zoom, 14)),
            },
            {
                "name": "CacheKid offline map",
                "format": "pbf",
                "bounds": [
                    bounds.min_longitude,
                    bounds.min_latitude,
                    bounds.max_longitude,
                    bounds.max_latitude,
                ],
                "vector_layers": [
                    {"id": "land", "description": "Forest and wooded areas", "fields": {"class": "String"}},
                    {"id": "water", "description": "Water bodies and waterways", "fields": {"class": "String"}},
                    {"id": "roads", "description": "Road and path network", "fields": {"class": "String"}},
                    {"id": "building", "description": "Buildings", "fields": {"class": "String"}},
                    {"id": "park", "description": "Parks and grassland", "fields": {"class": "String"}},
                    {"id": "landuse", "description": "Landuse areas", "fields": {"class": "String"}},
                ],
            },
        )


def encode_tile(features: list[RenderFeature], zoom: int, tile_x: int, tile_y: int) -> bytes:
    west, south, east, north = tile_bounds(tile_x, tile_y, zoom)
    tile_box = box(west, south, east, north)
    layers: list[dict[str, Any]] = []

    for layer_name in ("land", "water", "roads", "building", "park", "landuse"):
        layer_features = []
        for feature in features:
            if feature.layer != layer_name or not feature.geometry.intersects(tile_box):
                continue
            clipped = feature.geometry.intersection(tile_box)
            if clipped.is_empty:
                continue
            for geometry in explode_geometry(clipped):
                layer_features.append(
                    {
                        "geometry": mapping(geometry),
                        "properties": feature.properties,
                    }
                )
        if layer_features:
            layers.append({"name": layer_name, "features": layer_features})

    if not layers:
        return b""

    return mapbox_vector_tile.encode(
        layers,
        default_options={
            "quantize_bounds": (west, south, east, north),
            "extents": 4096,
        },
    )


def explode_geometry(geometry: Any) -> list[Any]:
    if geometry.geom_type in {"GeometryCollection", "MultiLineString", "MultiPolygon"}:
        result = []
        for item in geometry.geoms:
            result.extend(explode_geometry(item))
        return result
    if isinstance(geometry, MultiPolygon):
        return list(geometry.geoms)
    return [geometry]


def build_manifest(
    package_id: str,
    display_name: str,
    version: str,
    bounds: Bounds,
    min_zoom: int,
    max_zoom: int,
) -> dict[str, Any]:
    return {
        "id": package_id,
        "displayName": display_name,
        "version": version,
        "format": "pmtiles-vector",
        "tileAssetPath": "map.pmtiles",
        "styleAssetPath": "style.json",
        "minZoom": min_zoom,
        "maxZoom": max_zoom,
        "attribution": "OpenStreetMap contributors",
        "bounds": {
            "minLatitude": bounds.min_latitude,
            "minLongitude": bounds.min_longitude,
            "maxLatitude": bounds.max_latitude,
            "maxLongitude": bounds.max_longitude,
        },
    }


def build_style_json(min_zoom: int, max_zoom: int) -> str:
    """Build an E-ink optimized MapLibre style JSON.

    Colours are chosen for high contrast on reflective (E-ink) displays where
    mid-tone greys can appear muddy.  Roads use dark greys on a white
    background so they remain readable in bright daylight without backlight.
    """
    return json.dumps(
        {
            "version": 8,
            "name": "CacheKid Local Offline Map",
            "sources": {
                "cachekid-offline-basemap-source": {
                    "type": "vector",
                    "url": "pmtiles://cachekid-local-map",
                    "minzoom": min_zoom,
                    "maxzoom": max_zoom,
                    "attribution": "OpenStreetMap contributors",
                }
            },
            "layers": [
                {
                    "id": "background",
                    "type": "background",
                    "paint": {"background-color": "#ffffff"},
                },
                {
                    "id": "landuse-residential",
                    "type": "fill",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "landuse",
                    "filter": ["==", ["get", "class"], "residential"],
                    "paint": {"fill-color": "#f2f2f2", "fill-opacity": 1.0},
                },
                {
                    "id": "park-fill",
                    "type": "fill",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "park",
                    "paint": {"fill-color": "#e8e8e8", "fill-opacity": 1.0},
                },
                {
                    "id": "land-forest",
                    "type": "fill",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "land",
                    "paint": {"fill-color": "#e0e0e0", "fill-opacity": 1.0},
                },
                {
                    "id": "building-fill",
                    "type": "fill",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "building",
                    "paint": {"fill-color": "#dddddd", "fill-opacity": 1.0},
                },
                {
                    "id": "water-fill",
                    "type": "fill",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "water",
                    "paint": {"fill-color": "#cccccc", "fill-opacity": 1.0},
                },
                {
                    "id": "water-line",
                    "type": "line",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "water",
                    "paint": {"line-color": "#999999", "line-width": 1.5},
                },
                {
                    "id": "road-minor",
                    "type": "line",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "roads",
                    "filter": ["==", ["get", "class"], "minor"],
                    "paint": {"line-color": "#777777", "line-width": 1.4, "line-opacity": 1.0},
                },
                {
                    "id": "road-major",
                    "type": "line",
                    "source": "cachekid-offline-basemap-source",
                    "source-layer": "roads",
                    "filter": ["==", ["get", "class"], "major"],
                    "paint": {"line-color": "#444444", "line-width": 2.2, "line-opacity": 1.0},
                },
            ],
        },
        indent=2,
    ) + "\n"


def lonlat_to_tile(longitude: float, latitude: float, zoom: int) -> tuple[int, int]:
    latitude = min(max(latitude, -85.05112878), 85.05112878)
    scale = 1 << zoom
    x = int((longitude + 180.0) / 360.0 * scale)
    lat_rad = math.radians(latitude)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * scale)
    return (
        min(max(x, 0), scale - 1),
        min(max(y, 0), scale - 1),
    )


def tile_bounds(tile_x: int, tile_y: int, zoom: int) -> tuple[float, float, float, float]:
    scale = 1 << zoom
    west = tile_x / scale * 360.0 - 180.0
    east = (tile_x + 1) / scale * 360.0 - 180.0
    north = math.degrees(math.atan(math.sinh(math.pi * (1.0 - 2.0 * tile_y / scale))))
    south = math.degrees(math.atan(math.sinh(math.pi * (1.0 - 2.0 * (tile_y + 1) / scale))))
    return west, south, east, north


if __name__ == "__main__":
    main()
