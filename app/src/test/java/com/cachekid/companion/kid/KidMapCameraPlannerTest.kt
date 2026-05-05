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
    fun `FOLLOW_HEADING_UP uses live location as target`() {
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.FOLLOW_HEADING_UP,
            location = KidMapCameraPlanner.LocationSnapshot(
                latitude = 52.515,
                longitude = 13.400,
                accuracyMeters = 10f,
            ),
            headingDegrees = 45.0,
            mission = berlinMission,
            viewport = viewport,
        )
        assertEquals(52.515, plan.target.latitude, 0.001)
        assertEquals(13.400, plan.target.longitude, 0.001)
        assertEquals(45.0, plan.bearing, 0.01)
        assertEquals(0.0, plan.tilt, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP zoom is dynamic based on distance to target`() {
        // Player ~650 m from target -> zoom should be relaxed, not locked at 18.0
        val plan = planner.plan(
            mode = KidMapCameraPlanner.CameraMode.FOLLOW_HEADING_UP,
            location = KidMapCameraPlanner.LocationSnapshot(
                latitude = 52.515,
                longitude = 13.400,
                accuracyMeters = 10f,
            ),
            headingDegrees = 45.0,
            mission = berlinMission,
            viewport = viewport,
        )
        assert(plan.zoom < 18.0) { "zoom ${plan.zoom} should be < 18.0 for ~650 m distance" }
        assert(plan.zoom >= 14.5) { "zoom ${plan.zoom} should be >= 14.5" }
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
