#!/usr/bin/env python3
"""Regression tests for the offline map package builder."""

from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_offline_map_package as builder


class OfflineMapPackageBuilderTest(unittest.TestCase):
    def test_builds_installable_pmtiles_package(self) -> None:
        overpass_data = {
            "elements": [
                {
                    "type": "way",
                    "id": 1,
                    "tags": {"highway": "residential"},
                    "geometry": [
                        {"lat": 52.6169, "lon": 10.0540},
                        {"lat": 52.6172, "lon": 10.0550},
                    ],
                },
                {
                    "type": "way",
                    "id": 2,
                    "tags": {"landuse": "forest"},
                    "geometry": [
                        {"lat": 52.6160, "lon": 10.0530},
                        {"lat": 52.6160, "lon": 10.0560},
                        {"lat": 52.6180, "lon": 10.0560},
                        {"lat": 52.6180, "lon": 10.0530},
                        {"lat": 52.6160, "lon": 10.0530},
                    ],
                },
            ]
        }

        with tempfile.TemporaryDirectory() as temp_dir_name:
            output = Path(temp_dir_name) / "cachekid-test-map.zip"
            features = builder.extract_features(overpass_data)

            builder.build_package(
                package_id="test-celle",
                display_name="Test Celle",
                version="test",
                bounds=builder.Bounds(
                    min_latitude=52.615,
                    min_longitude=10.052,
                    max_latitude=52.619,
                    max_longitude=10.057,
                ),
                min_zoom=14,
                max_zoom=14,
                features=features,
                output_zip=output,
            )

            self.assertTrue(output.exists())
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                self.assertEqual({"offline-map.json", "map.pmtiles", "style.json"}, names)
                manifest = json.loads(archive.read("offline-map.json").decode("utf-8"))
                style = json.loads(archive.read("style.json").decode("utf-8"))
                pmtiles = archive.read("map.pmtiles")

            self.assertEqual("test-celle", manifest["id"])
            self.assertEqual("pmtiles-vector", manifest["format"])
            self.assertEqual("pmtiles://cachekid-local-map", style["sources"]["cachekid-offline-basemap-source"]["url"])
            self.assertTrue(pmtiles.startswith(b"PMTiles\x03"))
            self.assertGreater(len(pmtiles), 127)


if __name__ == "__main__":
    unittest.main()
