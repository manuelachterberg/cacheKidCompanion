package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionMapViewportPlannerTest {

    private val planner = MissionMapViewportPlanner()

    @Test
    fun `planner builds bounds around mission target`() {
        val target = MissionTarget(52.520008, 13.404954)

        val bounds = planner.createBounds(target)

        assertTrue(bounds.minLatitude < target.latitude)
        assertTrue(bounds.maxLatitude > target.latitude)
        assertTrue(bounds.minLongitude < target.longitude)
        assertTrue(bounds.maxLongitude > target.longitude)
    }

    @Test
    fun `planner can scale bounds around same center`() {
        val bounds = MissionMapBounds(
            minLatitude = 52.50,
            minLongitude = 13.30,
            maxLatitude = 52.60,
            maxLongitude = 13.50,
        )

        val scaled = planner.scaleBounds(bounds, 0.5)

        assertEquals(52.55, (scaled.minLatitude + scaled.maxLatitude) / 2.0, 0.000001)
        assertEquals(13.40, (scaled.minLongitude + scaled.maxLongitude) / 2.0, 0.000001)
        assertTrue((scaled.maxLatitude - scaled.minLatitude) < (bounds.maxLatitude - bounds.minLatitude))
        assertTrue((scaled.maxLongitude - scaled.minLongitude) < (bounds.maxLongitude - bounds.minLongitude))
    }
}
