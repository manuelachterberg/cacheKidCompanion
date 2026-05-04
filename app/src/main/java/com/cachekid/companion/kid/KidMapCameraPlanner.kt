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
            CameraMode.FOLLOW_HEADING_UP -> planFollowHeadingUp(location, headingDegrees, viewport)
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
        viewport: Viewport,
    ): CameraPlan {
        val playerLocation = if (location != null) {
            LatLng(location.latitude, location.longitude)
        } else {
            LatLng(52.52, 13.405)
        }

        val bearing = headingDegrees ?: 0.0

        // Offset target in heading direction so the player appears in the
        // lower portion of the screen instead of dead centre.
        val target = if (location != null) {
            val offsetMeters = followOffsetMeters(viewport)
            offsetLatLng(playerLocation.latitude, playerLocation.longitude, bearing, offsetMeters)
        } else {
            playerLocation
        }

        return CameraPlan(
            target = target,
            bearing = normalizeDegrees(bearing),
            zoom = FOLLOW_ZOOM,
            tilt = 0.0,
        )
    }

    /**
     * How many metres to shift the camera target forward along the heading
     * so that the player dot sits in the lower third of the screen.
     */
    private fun followOffsetMeters(viewport: Viewport): Double {
        val visibleHeightPx = (viewport.heightPx - viewport.topPaddingPx - viewport.bottomPaddingPx)
            .coerceAtLeast(100)
        // Place player roughly one-third up from the bottom -> offset is
        // 1/6 of visible height below centre.
        val offsetPx = visibleHeightPx / 6.0
        // At zoom 18 one vertical pixel ≈ 0.6 m.
        return offsetPx * METERS_PER_PIXEL_AT_ZOOM_18
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
        private const val METERS_PER_PIXEL_AT_ZOOM_18 = 0.6

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
