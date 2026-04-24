#!/usr/bin/env python3
"""Convert a CacheKid mission.json into a GPX route/track for the Android emulator."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable
from xml.sax.saxutils import escape


def load_mission(path_arg: str) -> tuple[dict, Path]:
    path = Path(path_arg).expanduser().resolve()
    if path.is_dir():
        mission_path = path / "mission.json"
    else:
        mission_path = path
    if not mission_path.exists():
        raise FileNotFoundError(f"Mission file not found: {mission_path}")
    with mission_path.open("r", encoding="utf-8") as handle:
        return json.load(handle), mission_path


def point_name(index: int, total: int, point: dict) -> str:
    label = point.get("label")
    if label:
        return str(label)
    if index == 0:
        return "Start"
    if index == total - 1:
        return "Ziel"
    return f"Wegpunkt {index}"


def point_list(mission: dict) -> list[dict]:
    points: list[dict] = []
    route_origin = mission.get("routeOrigin")
    if route_origin:
        points.append(
            {
                "latitude": route_origin["latitude"],
                "longitude": route_origin["longitude"],
                "label": "Start",
            }
        )

    for waypoint in mission.get("waypoints", []):
        points.append(
            {
                "latitude": waypoint["latitude"],
                "longitude": waypoint["longitude"],
                "label": waypoint.get("label"),
            }
        )

    target = mission.get("target")
    if target:
        points.append(
            {
                "latitude": target["latitude"],
                "longitude": target["longitude"],
                "label": "Ziel",
            }
        )
    return points


def waypoint_xml(points: Iterable[dict], total: int) -> str:
    lines: list[str] = []
    for index, point in enumerate(points):
        name = escape(point_name(index, total, point))
        lat = point["latitude"]
        lon = point["longitude"]
        lines.append(f'  <wpt lat="{lat}" lon="{lon}"><name>{name}</name></wpt>')
    return "\n".join(lines)


def route_point_xml(points: Iterable[dict], total: int) -> str:
    lines: list[str] = []
    for index, point in enumerate(points):
        name = escape(point_name(index, total, point))
        lat = point["latitude"]
        lon = point["longitude"]
        lines.append(f'    <rtept lat="{lat}" lon="{lon}"><name>{name}</name></rtept>')
    return "\n".join(lines)


def track_point_xml(points: Iterable[dict]) -> str:
    lines: list[str] = []
    for point in points:
        lat = point["latitude"]
        lon = point["longitude"]
        lines.append(f'      <trkpt lat="{lat}" lon="{lon}" />')
    return "\n".join(lines)


def build_gpx(mission: dict) -> str:
    points = point_list(mission)
    if len(points) < 2:
        raise ValueError("Mission has too few points for GPX export.")

    mission_name = escape(mission.get("childTitle") or mission.get("sourceTitle") or mission.get("cacheCode") or "CacheKid Route")
    total = len(points)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="CacheKid Companion" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>{mission_name}</name>
  </metadata>
{waypoint_xml(points, total)}
  <rte>
    <name>{mission_name}</name>
{route_point_xml(points, total)}
  </rte>
  <trk>
    <name>{mission_name}</name>
    <trkseg>
{track_point_xml(points)}
    </trkseg>
  </trk>
</gpx>
"""


def build_minimal_gpx(mission: dict) -> str:
    points = point_list(mission)
    if len(points) < 2:
        raise ValueError("Mission has too few points for GPX export.")

    mission_name = escape(mission.get("childTitle") or mission.get("sourceTitle") or mission.get("cacheCode") or "CacheKid Route")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="CacheKid Companion" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>{mission_name}</name>
    <trkseg>
{track_point_xml(points)}
    </trkseg>
  </trk>
</gpx>
"""


def default_output_path(mission_path: Path, mission: dict) -> Path:
    stem = mission.get("cacheCode") or mission_path.stem or "cachekid-route"
    return Path.cwd() / f"{stem.lower()}-emulator-route.gpx"


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert CacheKid mission.json to GPX for emulator playback.")
    parser.add_argument("mission", help="Path to mission.json or a mission directory containing mission.json")
    parser.add_argument("-o", "--output", help="Output GPX path")
    parser.add_argument("--minimal", action="store_true", help="Write only a minimal GPX track segment for emulator compatibility")
    args = parser.parse_args()

    mission, mission_path = load_mission(args.mission)
    output_path = Path(args.output).expanduser().resolve() if args.output else default_output_path(mission_path, mission)
    gpx_text = build_minimal_gpx(mission) if args.minimal else build_gpx(mission)
    output_path.write_text(gpx_text, encoding="utf-8")
    print(output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
