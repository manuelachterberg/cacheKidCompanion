package com.cachekid.companion.kid

import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.MissionTarget
import com.cachekid.companion.host.mission.MissionWaypoint
import org.junit.Assert.assertEquals
import org.junit.Test

class KidMapCameraPlannerTest {

    private val planner = KidMapCameraPlanner()
    private val viewport = KidMapCameraPlanner.Viewport(
        widthPx = 1080,
        heightPx = 2400,
        topPaddingPx = 800,
        bottomPaddingPx = 300,
        sidePaddingPx = 100,
    )

    private val berlinMission = ActiveMission(
        missionId = "test",
        cacheCode = "GC123",
        sourceTitle = "Test",
        childTitle = "Test",
        summary = "Test mission",
        target = MissionTarget(52.52, 13.405),
        routeOrigin = MissionTarget(52.51, 13.395),
        waypoints = listOf(
            MissionWaypoint(52.515, 13.40, null),
            MissionWaypoint(52.518, 13.402, null),
        ),
    )

    @Test
    fun `ROUTE_OVERVIEW uses mission target as camera target`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.ROUTE_OVERVIEW,
            location = null,
            headingDegrees = null,
            mission = berlinMission,
            viewport = viewport,
        )
        assertEquals(52.52, plan.target.latitude, 0.001)
        assertEquals(13.405, plan.target.longitude, 0.001)
    }

    @Test
    fun `ROUTE_OVERVIEW falls back to route bearing when no heading`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.ROUTE_OVERVIEW,
            location = null,
            headingDegrees = null,
            mission = berlinMission,
            viewport = viewport,
        )
        // Bearing from 52.51,13.395 to 52.52,13.405 is roughly NE (~33°)
        assertEquals(33.0, plan.bearing, 5.0)
        assertEquals(16.2, plan.zoom, 0.01)
        assertEquals(20.0, plan.tilt, 0.01)
    }

    @Test
    fun `ROUTE_OVERVIEW uses heading when available`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.ROUTE_OVERVIEW,
            location = null,
            headingDegrees = 90.0,
            mission = berlinMission,
            viewport = viewport,
        )
        assertEquals(90.0, plan.bearing, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP offsets target forward so player sits lower on screen`() {
        val playerLat = 52.515
        val playerLon = 13.400
        val heading = 45.0

        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.FOLLOW_HEADING_UP,
            location = KidMapCameraPlanner.LocationSnapshot(
                latitude = playerLat,
                longitude = playerLon,
                accuracyMeters = 10f,
            ),
            headingDegrees = heading,
            mission = berlinMission,
            viewport = viewport,
        )

        // Target must be shifted forward along the heading, not exactly on the player.
        val visibleHeightPx = viewport.heightPx - viewport.topPaddingPx - viewport.bottomPaddingPx
        val minOffsetMeters = (visibleHeightPx / 6.0) * 0.5  // lower bound
        val maxOffsetMeters = (visibleHeightPx / 6.0) * 0.8  // upper bound

        val distanceMeters = haversine(playerLat, playerLon, plan.target.latitude, plan.target.longitude)
        assert(distanceMeters >= minOffsetMeters) { "offset $distanceMeters should be >= $minOffsetMeters" }
        assert(distanceMeters <= maxOffsetMeters) { "offset $distanceMeters should be <= $maxOffsetMeters" }

        // Target should lie roughly on the heading line from the player.
        val bearingToTarget = KidMapCameraPlanner.bearingBetween(
            playerLat, playerLon, plan.target.latitude, plan.target.longitude
        )
        assertEquals(heading, bearingToTarget, 2.0)

        assertEquals(heading, plan.bearing, 0.01)
        assertEquals(KidMapCameraPlanner.FOLLOW_ZOOM, plan.zoom, 0.01)
        assertEquals(0.0, plan.tilt, 0.01)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    @Test
    fun `FOLLOW_HEADING_UP falls back to zero bearing when no heading`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.FOLLOW_HEADING_UP,
            location = KidMapCameraPlanner.LocationSnapshot(
                latitude = 52.515,
                longitude = 13.400,
                accuracyMeters = 10f,
            ),
            headingDegrees = null,
            mission = berlinMission,
            viewport = viewport,
        )
        assertEquals(0.0, plan.bearing, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP falls back to default location when no location`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.FOLLOW_HEADING_UP,
            location = null,
            headingDegrees = null,
            mission = berlinMission,
            viewport = viewport,
        )
        assertEquals(52.52, plan.target.latitude, 0.001)
        assertEquals(13.405, plan.target.longitude, 0.001)
    }

    @Test
    fun `normalizeDegrees handles negative values`() {
        assertEquals(270.0, KidMapCameraPlanner.normalizeDegrees(-90.0), 0.01)
    }

    @Test
    fun `normalizeDegrees handles values above 360`() {
        assertEquals(90.0, KidMapCameraPlanner.normalizeDegrees(450.0), 0.01)
    }

    @Test
    fun `bearingBetween computes correct bearing`() {
        val bearing = KidMapCameraPlanner.bearingBetween(52.51, 13.395, 52.52, 13.405)
        // Roughly NE
        assertEquals(33.0, bearing, 5.0)
    }
}
