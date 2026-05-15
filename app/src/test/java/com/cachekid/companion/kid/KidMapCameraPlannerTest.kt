package com.cachekid.companion.kid

import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.MissionTarget
import com.cachekid.companion.host.mission.MissionWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

class KidMapCameraPlannerTest {

    private val planner = KidMapCameraPlanner()

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
        const val TILE_SIZE_PX = 1024.0
    }

    @Test
    fun `ROUTE_OVERVIEW uses route bounds containing all points`() {
        val mission = activeMission(
            target = MissionTarget(52.53, 13.42),
            routeOrigin = MissionTarget(52.51, 13.40),
            waypoints = listOf(
                MissionWaypoint(52.52, 13.41),
            ),
        )
        val location = locationSnapshot(52.50, 13.39)
        val viewport = viewport()

        val plan = planner.plan(
            CameraMode.ROUTE_OVERVIEW,
            location,
            headingDegrees = 45.0,
            mission,
            viewport,
        )

        assertEquals(CameraMode.ROUTE_OVERVIEW, plan.mode)
        assertNotNull(plan.bounds)
        assertNull(plan.target)
        assertNull(plan.zoom)

        val bounds = plan.bounds!!
        assertEquals(52.53, bounds.latitudeNorth, 0.001)
        assertEquals(52.51, bounds.latitudeSouth, 0.001)
        assertEquals(13.42, bounds.longitudeEast, 0.001)
        assertEquals(13.40, bounds.longitudeWest, 0.001)
    }

    @Test
    fun `ROUTE_OVERVIEW bearing is always 0`() {
        val mission = activeMission(target = MissionTarget(52.52, 13.405))
        val plan = planner.plan(
            CameraMode.ROUTE_OVERVIEW,
            locationSnapshot(52.52, 13.405),
            headingDegrees = 123.0,
            mission,
            viewport(),
        )
        assertEquals(0.0, plan.bearing, 0.01)
        assertEquals(0.0, plan.tilt, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP target is not the player position`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val player = locationSnapshot(52.51, 13.40)
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            player,
            headingDegrees = 90.0,
            mission,
            viewport(),
        )

        assertEquals(CameraMode.FOLLOW_HEADING_UP, plan.mode)
        assertNotNull(plan.target)
        assertFalse(
            "target should not be exactly playerLocation",
            plan.target!!.latitude == player.latitude && plan.target!!.longitude == player.longitude,
        )
    }

    @Test
    fun `FOLLOW_HEADING_UP zoom is dynamic based on distance to target`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val nearby = locationSnapshot(52.529, 13.419)
        val far = locationSnapshot(52.40, 13.30)

        val nearbyPlan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            nearby,
            headingDegrees = 0.0,
            mission,
            viewport(),
        )
        val farPlan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            far,
            headingDegrees = 90.0, // offset heading to avoid auto-switch to overview
            mission,
            viewport(),
        )

        assertNotNull(nearbyPlan.zoom)
        assertNotNull(farPlan.zoom)
        assertTrue("far zoom should be <= nearby zoom (zoomed out)", farPlan.zoom!! <= nearbyPlan.zoom!!)
        assertTrue("nearby zoom should not exceed max", nearbyPlan.zoom!! <= KidMapCameraPlanner.MAX_FOLLOW_ZOOM)
    }

    @Test
    fun `FOLLOW_HEADING_UP auto-switches to ROUTE_OVERVIEW when far and target aligned with heading`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val far = locationSnapshot(52.40, 13.30)
        val alignedHeading = planner.bearingBetween(
            LatLng(far.latitude, far.longitude),
            LatLng(mission.target.latitude, mission.target.longitude),
        )

        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            far,
            headingDegrees = alignedHeading,
            mission,
            viewport(),
        )

        assertEquals(CameraMode.ROUTE_OVERVIEW, plan.mode)
        assertNotNull(plan.bounds)
    }

    @Test
    fun `FOLLOW_HEADING_UP stays in FOLLOW when far but target not aligned with heading`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val far = locationSnapshot(52.40, 13.30)

        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            far,
            headingDegrees = 90.0, // perpendicular to bearing, headingDiff > 30°
            mission,
            viewport(),
        )

        assertEquals(CameraMode.FOLLOW_HEADING_UP, plan.mode)
        assertNotNull(plan.target)
        assertNotNull(plan.zoom)
    }

    @Test
    fun `FOLLOW_HEADING_UP zoom respects viewport padding`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val location = locationSnapshot(52.529, 13.419)

        val smallViewport = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            location,
            headingDegrees = 0.0,
            mission,
            viewport(heightPx = 400, topPaddingPx = 200, bottomPaddingPx = 100),
        )
        val largeViewport = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            location,
            headingDegrees = 0.0,
            mission,
            viewport(heightPx = 1200, topPaddingPx = 100, bottomPaddingPx = 100),
        )

        assertTrue(
            "small visible area should zoom out more",
            smallViewport.zoom!! <= largeViewport.zoom!!,
        )
    }

    @Test
    fun `FOLLOW_HEADING_UP at zero distance does not crash`() {
        val mission = activeMission(target = MissionTarget(52.52, 13.405))
        val location = locationSnapshot(52.52, 13.405)
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            location,
            headingDegrees = 0.0,
            mission,
            viewport(),
        )
        assertNotNull(plan.zoom)
        assertTrue(plan.zoom!!.isFinite())
        assertEquals(KidMapCameraPlanner.MAX_FOLLOW_ZOOM, plan.zoom!!, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP fallback bearing is target bearing when no heading`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val player = locationSnapshot(52.51, 13.40)
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            player,
            headingDegrees = null,
            mission,
            viewport(),
        )
        val expectedBearing = planner.bearingBetween(
            LatLng(player.latitude, player.longitude),
            LatLng(mission.target.latitude, mission.target.longitude),
        )
        assertEquals(expectedBearing, plan.bearing, 0.01)
    }

    @Test
    fun `FOLLOW_HEADING_UP normal heading places X above O`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val player = locationSnapshot(52.51, 13.40)
        val heading = planner.bearingBetween(
            LatLng(player.latitude, player.longitude),
            LatLng(mission.target.latitude, mission.target.longitude),
        )
        val vp = viewport()
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            player,
            headingDegrees = heading,
            mission,
            vp,
        )

        val (_, oScreenY) = screenPosition(
            LatLng(player.latitude, player.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )
        val (_, xScreenY) = screenPosition(
            LatLng(mission.target.latitude, mission.target.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )

        // X soll oberhalb von O sein (kleineres screenY = weiter oben)
        assertTrue("X should be above O: xY=$xScreenY oY=$oScreenY", xScreenY < oScreenY)
    }

    @Test
    fun `FOLLOW_HEADING_UP reverse heading places O above X`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val player = locationSnapshot(52.51, 13.40)
        val heading = planner.normalizeDegrees(
            planner.bearingBetween(
                LatLng(player.latitude, player.longitude),
                LatLng(mission.target.latitude, mission.target.longitude),
            ) + 180.0,
        )
        val vp = viewport()
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            player,
            headingDegrees = heading,
            mission,
            vp,
        )

        val (_, oScreenY) = screenPosition(
            LatLng(player.latitude, player.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )
        val (_, xScreenY) = screenPosition(
            LatLng(mission.target.latitude, mission.target.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )

        assertTrue("O should be above X in reverse heading: oY=$oScreenY xY=$xScreenY", oScreenY < xScreenY)
    }

    @Test
    fun `FOLLOW_HEADING_UP both O and X are within visible lower 2-3`() {
        val mission = activeMission(target = MissionTarget(52.53, 13.42))
        val player = locationSnapshot(52.51, 13.40)
        val vp = viewport()
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            player,
            headingDegrees = 45.0,
            mission,
            vp,
        )

        val (_, oScreenY) = screenPosition(
            LatLng(player.latitude, player.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )
        val (_, xScreenY) = screenPosition(
            LatLng(mission.target.latitude, mission.target.longitude),
            plan.target!!,
            plan.zoom!!,
            plan.bearing,
            vp,
        )

        val effectiveTop = max(vp.topPaddingPx, vp.heightPx / 3)

        assertTrue("O should be below top invisible zone (y=$oScreenY top=$effectiveTop)", oScreenY >= effectiveTop)
        assertTrue("X should be below top invisible zone (y=$xScreenY top=$effectiveTop)", xScreenY >= effectiveTop)
        assertTrue("O should be within screen height", oScreenY <= vp.heightPx)
        assertTrue("X should be within screen height", xScreenY <= vp.heightPx)
    }

    @Test
    fun `FOLLOW_HEADING_UP uses fallback origin when GPS null`() {
        val mission = activeMission(
            target = MissionTarget(52.53, 13.42),
            routeOrigin = MissionTarget(52.51, 13.40),
        )
        val vp = viewport()
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            location = null,
            headingDegrees = 0.0,
            mission,
            vp,
        )

        assertNotNull(plan.target)
        assertNotNull(plan.zoom)
        // Der Target sollte ein berechneter Offset-Punkt sein, nicht exakt der routeOrigin
        assertFalse(
            "target should not be exactly routeOrigin",
            plan.target!!.latitude == 52.51 && plan.target!!.longitude == 13.40,
        )
    }

    @Test
    fun `hasArrived returns true when within threshold`() {
        val targetLat = 52.52
        val targetLon = 13.405
        // 10 Meter nördlich des Targets
        val playerLat = 52.52009
        val playerLon = 13.405

        assertTrue(
            planner.hasArrived(playerLat, playerLon, targetLat, targetLon, thresholdMeters = 15.0),
        )
    }

    @Test
    fun `hasArrived returns false when beyond threshold`() {
        val targetLat = 52.52
        val targetLon = 13.405
        // 100 Meter nördlich des Targets
        val playerLat = 52.5209
        val playerLon = 13.405

        assertFalse(
            planner.hasArrived(playerLat, playerLon, targetLat, targetLon, thresholdMeters = 15.0),
        )
    }

    @Test
    fun `hasArrived uses default threshold of 15 meters`() {
        val targetLat = 52.52
        val targetLon = 13.405
        // 12 Meter nördlich — innerhalb von 15m, außerhalb von 5m
        val playerLat = 52.52011
        val playerLon = 13.405

        assertTrue(
            planner.hasArrived(playerLat, playerLon, targetLat, targetLon),
        )
    }

    @Test
    fun `plan handles null location and null mission gracefully`() {
        val plan = planner.plan(
            CameraMode.FOLLOW_HEADING_UP,
            location = null,
            headingDegrees = null,
            mission = null,
            viewport(),
        )
        assertNotNull(plan.target)
        assertEquals(KidMapCameraPlanner.MIN_FOLLOW_ZOOM, plan.zoom!!, 0.01)
    }

    @Test
    fun `normalizeDegrees handles negative values`() {
        assertEquals(270.0, planner.normalizeDegrees(-90.0), 0.01)
    }

    @Test
    fun `normalizeDegrees handles values above 360`() {
        assertEquals(45.0, planner.normalizeDegrees(405.0), 0.01)
    }

    @Test
    fun `bearingBetween computes correct bearing`() {
        val start = LatLng(52.52, 13.405)
        val end = LatLng(52.53, 13.42)
        val bearing = planner.bearingBetween(start, end)
        assertTrue("bearing should be roughly NE", bearing in 20.0..70.0)
    }

    // --- helpers ---

    private fun activeMission(
        target: MissionTarget,
        routeOrigin: MissionTarget? = null,
        waypoints: List<MissionWaypoint> = emptyList(),
    ): ActiveMission = ActiveMission(
        missionId = "test-mission",
        cacheCode = "GC12345",
        sourceTitle = "Test Cache",
        childTitle = "Test",
        summary = "Test summary",
        target = target,
        routeOrigin = routeOrigin,
        waypoints = waypoints,
    )

    private fun locationSnapshot(lat: Double, lon: Double): LocationSnapshot =
        LocationSnapshot(latitude = lat, longitude = lon)

    private fun viewport(
        widthPx: Int = 1080,
        heightPx: Int = 2400,
        topPaddingPx: Int = 200,
        bottomPaddingPx: Int = 200,
        leftPaddingPx: Int = 80,
        rightPaddingPx: Int = 80,
    ): Viewport = Viewport(
        widthPx = widthPx,
        heightPx = heightPx,
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        leftPaddingPx = leftPaddingPx,
        rightPaddingPx = rightPaddingPx,
    )

    private fun latLngToMercator(latLng: LatLng): Pair<Double, Double> {
        val x = Math.toRadians(latLng.longitude) * EARTH_RADIUS_METERS
        val y = ln(
            tan(Math.PI / 4.0 + Math.toRadians(latLng.latitude) / 2.0)
        ) * EARTH_RADIUS_METERS
        return x to y
    }

    private fun screenPosition(
        point: LatLng,
        cameraTarget: LatLng,
        zoom: Double,
        bearing: Double,
        viewport: Viewport,
    ): Pair<Double, Double> {
        val scale0 = TILE_SIZE_PX / EARTH_CIRCUMFERENCE_METERS
        val scale = scale0 * 2.0.pow(zoom)

        val pointM = latLngToMercator(point)
        val targetM = latLngToMercator(cameraTarget)

        val dx = pointM.first - targetM.first
        val dy = pointM.second - targetM.second

        val angleRad = Math.toRadians(-bearing)
        val dxR = dx * cos(angleRad) - dy * sin(angleRad)
        val dyR = dx * sin(angleRad) + dy * cos(angleRad)

        val screenX = viewport.widthPx / 2.0 + dxR * scale
        val screenY = viewport.heightPx / 2.0 - dyR * scale

        return screenX to screenY
    }
}
