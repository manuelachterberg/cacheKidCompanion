package com.cachekid.companion.kid

import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.MissionTarget

/**
 * Pure Kotlin camera planner for the kid map.
 *
 * Decides target, bearing, zoom and tilt based on camera mode,
 * live location, heading and mission geometry.  Contains no
 * Android or MapLibre dependencies so it can be unit-tested.
 */
class KidMapCameraPlanner {

    enum class CameraMode {
        /** Show the full route (start → waypoints → target). */
        ROUTE_OVERVIEW,

        /** Follow the user's live location, heading points up. */
        FOLLOW_HEADING_UP,
    }

    data class LatLng(val latitude: Double, val longitude: Double)

    data class CameraPlan(
        val target: LatLng,
        val bearing: Double,
        val zoom: Double,
        val tilt: Double,
    )

    data class Viewport(
        val widthPx: Int,
        val heightPx: Int,
        val topPaddingPx: Int,
        val bottomPaddingPx: Int,
        val sidePaddingPx: Int,
    )

    data class LocationSnapshot(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    )

    /**
     * Compute the desired camera state.
     *
     * @param mode current camera mode
     * @param location latest known location, or null
     * @param headingDegrees latest known heading, or null
     * @param mission active mission, or null
     * @param viewport current viewport metrics
     */
    fun plan(
        mode: CameraMode,
        location: LocationSnapshot?,
        headingDegrees: Double?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        return when (mode) {
            CameraMode.ROUTE_OVERVIEW -> planRouteOverview(location, headingDegrees, mission, viewport)
            CameraMode.FOLLOW_HEADING_UP -> planFollowHeadingUp(location, headingDegrees, mission, viewport)
        }
    }

    private fun planRouteOverview(
        location: LocationSnapshot?,
        headingDegrees: Double?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        val fallbackTarget = mission?.target?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(52.52, 13.405)

        val fallbackBearing = headingDegrees
            ?: courseBearing(location, mission)
            ?: routeBearing(mission)
            ?: 0.0

        return CameraPlan(
            target = fallbackTarget,
            bearing = normalizeDegrees(fallbackBearing),
            zoom = 16.2,
            tilt = 20.0,
        )
    }

    private fun planFollowHeadingUp(
        location: LocationSnapshot?,
        headingDegrees: Double?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        val playerLocation = if (location != null) {
            LatLng(location.latitude, location.longitude)
        } else {
            LatLng(52.52, 13.405)
        }

        val bearing = headingDegrees ?: 0.0

        if (location == null || mission == null) {
            return CameraPlan(
                target = playerLocation,
                bearing = normalizeDegrees(bearing),
                zoom = FOLLOW_ZOOM,
                tilt = 0.0,
            )
        }

        // Collect all route points (target + waypoints) and find the
        // farthest one from the player so nothing disappears off-screen.
        val routePoints = buildList {
            add(LatLng(mission.target.latitude, mission.target.longitude))
            mission.waypoints.forEach { add(LatLng(it.latitude, it.longitude)) }
        }
        val maxDistance = routePoints.maxOfOrNull { point ->
            haversineDistance(
                playerLocation.latitude, playerLocation.longitude,
                point.latitude, point.longitude,
            )
        } ?: 0.0

        // Zoom so that the farthest route point stays visible.
        val zoom = followZoomForDistance(maxDistance, viewport)

        // Offset target forward so the player sits in the lower third.
        val offsetMeters = followOffsetMeters(viewport, zoom)
        val target = offsetLatLng(
            playerLocation.latitude, playerLocation.longitude,
            bearing, offsetMeters,
        )

        return CameraPlan(
            target = target,
            bearing = normalizeDegrees(bearing),
            zoom = zoom,
            tilt = 0.0,
        )
    }

    /**
     * How many metres to shift the camera target forward along the heading
     * so that the player dot sits in the lower third of the screen.
     */
    private fun followOffsetMeters(viewport: Viewport, zoom: Double): Double {
        val visibleHeightPx = (viewport.heightPx - viewport.topPaddingPx - viewport.bottomPaddingPx)
            .coerceAtLeast(100)
        // Place player roughly one-third up from the bottom -> offset is
        // 1/6 of visible height below centre.
        val offsetPx = visibleHeightPx / 6.0
        val metersPerPixel = EARTH_CIRCUMFERENCE_METERS / (256.0 * pow2(zoom))
        return offsetPx * metersPerPixel
    }

    /**
     * Choose a zoom level so that [distanceMeters] fills roughly 60 % of the
     * visible screen height, keeping the target in the upper area.
     */
    private fun followZoomForDistance(distanceMeters: Double, viewport: Viewport): Double {
        val visibleHeightPx = (viewport.heightPx - viewport.topPaddingPx - viewport.bottomPaddingPx)
            .coerceAtLeast(100)
        // We want distanceMeters to be ~60 % of visible height in meters.
        val desiredVisibleHeightMeters = distanceMeters / 0.6
        // At zoom z: visibleHeightMeters = visibleHeightPx * EARTH_CIRCUMFERENCE / (256 * 2^z)
        // Solve for z:
        val zoom = kotlin.math.ln(
            visibleHeightPx * EARTH_CIRCUMFERENCE_METERS / (256.0 * desiredVisibleHeightMeters)
        ) / kotlin.math.ln(2.0)
        return zoom.coerceIn(MIN_FOLLOW_ZOOM, MAX_FOLLOW_ZOOM)
    }

    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Move a lat/lon point by [distanceMeters] along [bearingDegrees].
     * Uses the small-angle approximation which is accurate to < 1 m for
     * distances under a few hundred metres.
     */
    private fun offsetLatLng(
        lat: Double,
        lon: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): LatLng {
        val latRad = Math.toRadians(lat)
        val bearingRad = Math.toRadians(bearingDegrees)

        val latOffsetRad = distanceMeters * kotlin.math.cos(bearingRad) / EARTH_RADIUS_METERS
        val lonOffsetRad = distanceMeters * kotlin.math.sin(bearingRad) /
            (EARTH_RADIUS_METERS * kotlin.math.cos(latRad))

        return LatLng(
            lat + Math.toDegrees(latOffsetRad),
            lon + Math.toDegrees(lonOffsetRad),
        )
    }

    private fun courseBearing(
        location: LocationSnapshot?,
        mission: ActiveMission?,
    ): Double? {
        if (location == null || mission == null) return null
        // Simplified: bearing from location to mission target
        return bearingBetween(
            location.latitude,
            location.longitude,
            mission.target.latitude,
            mission.target.longitude,
        )
    }

    private fun routeBearing(mission: ActiveMission?): Double? {
        if (mission == null) return null
        val origin = mission.routeOrigin ?: return null
        return bearingBetween(
            origin.latitude,
            origin.longitude,
            mission.target.latitude,
            mission.target.longitude,
        )
    }

    companion object {
        const val FOLLOW_ZOOM = 18.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
        private const val MIN_FOLLOW_ZOOM = 14.5
        private const val MAX_FOLLOW_ZOOM = 18.0

        private fun pow2(zoom: Double): Double = kotlin.math.exp(zoom * kotlin.math.ln(2.0))

        fun normalizeDegrees(value: Double): Double {
            var result = value % 360.0
            if (result < 0) result += 360.0
            return result
        }

        fun bearingBetween(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val deltaLonRad = Math.toRadians(lon2 - lon1)
            val y = kotlin.math.sin(deltaLonRad) * kotlin.math.cos(lat2Rad)
            val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
                kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(deltaLonRad)
            val bearingRad = kotlin.math.atan2(y, x)
            return normalizeDegrees(Math.toDegrees(bearingRad))
        }
    }
}
