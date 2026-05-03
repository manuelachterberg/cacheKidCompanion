package com.cachekid.companion.kid

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@RunWith(RobolectricTestRunner::class)

class MissionOverlayGeometryTest {

    @Test
    fun `buildTargetFeatures returns single Point feature at target coordinates`() {
        val target = Point.fromLngLat(10.0, 52.0)

        val features = MissionOverlayGeometry.buildTargetFeatures(target)

        assertEquals(1, features.size)
        val geometry = features[0].geometry()
        assertEquals(target, geometry)
    }

    @Test
    fun `buildPlayerFeatures returns single Point feature at player coordinates`() {
        val player = Point.fromLngLat(10.1, 52.1)

        val features = MissionOverlayGeometry.buildPlayerFeatures(player)

        assertEquals(1, features.size)
        val geometry = features[0].geometry()
        assertEquals(player, geometry)
    }

    @Test
    fun `buildWaypointFeatures returns one Point feature per waypoint`() {
        val waypoints = listOf(
            Point.fromLngLat(10.0, 52.0),
            Point.fromLngLat(10.1, 52.1),
            Point.fromLngLat(10.2, 52.2),
        )

        val features = MissionOverlayGeometry.buildWaypointFeatures(waypoints)

        assertEquals(3, features.size)
        waypoints.forEachIndexed { index, expectedPoint ->
            assertEquals(expectedPoint, features[index].geometry())
        }
    }

    @Test
    fun `buildWaypointFeatures returns empty list for no waypoints`() {
        val features = MissionOverlayGeometry.buildWaypointFeatures(emptyList())

        assertEquals(0, features.size)
    }

    @Test
    fun `buildRouteFeatures returns two LineString features with correct points`() {
        val start = Point.fromLngLat(10.0, 52.0)
        val wp1 = Point.fromLngLat(10.1, 52.1)
        val target = Point.fromLngLat(10.2, 52.2)

        val features = MissionOverlayGeometry.buildRouteFeatures(start, listOf(wp1), target)

        assertEquals(2, features.size)
        features.forEach { feature ->
            val geometry = feature.geometry()
            assertEquals(true, geometry is LineString)
            val lineString = geometry as LineString
            val coords = lineString.coordinates()
            assertEquals(3, coords.size)
            assertEquals(start, coords[0])
            assertEquals(wp1, coords[1])
            assertEquals(target, coords[2])
        }
    }

    @Test
    fun `buildRouteFeatures returns two features when exactly 2 route points`() {
        val start = Point.fromLngLat(10.0, 52.0)
        val target = Point.fromLngLat(10.0, 52.0)

        val features = MissionOverlayGeometry.buildRouteFeatures(start, emptyList(), target)

        assertEquals(2, features.size)
    }

    @Test
    fun `buildWaypointFeatureCollection wraps features correctly`() {
        val waypoints = listOf(Point.fromLngLat(10.0, 52.0))

        val collection = MissionOverlayGeometry.buildWaypointFeatureCollection(waypoints)

        assertEquals(1, collection.features()?.size)
    }

    @Test
    fun `buildRouteFeatureCollection wraps two LineString features for start plus target`() {
        val start = Point.fromLngLat(10.0, 52.0)
        val target = Point.fromLngLat(10.2, 52.2)

        val collection = MissionOverlayGeometry.buildRouteFeatureCollection(start, emptyList(), target)

        assertEquals(2, collection.features()?.size)
    }
}
