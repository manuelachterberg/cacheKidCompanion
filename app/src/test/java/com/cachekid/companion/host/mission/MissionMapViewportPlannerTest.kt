package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
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
}
