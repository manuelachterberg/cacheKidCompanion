package com.cachekid.companion.kid

import com.cachekid.companion.host.mission.ActiveMission
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class CameraMode {
    ROUTE_OVERVIEW,
    FOLLOW_HEADING_UP,
}

data class CameraPlan(
    val mode: CameraMode,
    val target: LatLng? = null,
    val bounds: LatLngBounds? = null,
    val zoom: Double? = null,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
)

data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val topPaddingPx: Int = 0,
    val bottomPaddingPx: Int = 0,
    val leftPaddingPx: Int = 0,
    val rightPaddingPx: Int = 0,
)

class KidMapCameraPlanner {

    companion object {
        const val MIN_FOLLOW_ZOOM = 10.0
        const val MAX_FOLLOW_ZOOM = 18.0
        const val OVERVIEW_MIN_ZOOM = 8.5
        const val OVERVIEW_MAX_ZOOM = 19.2
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
        private const val TILE_SIZE_PX = 1024.0
        private const val DESIRED_ORIGIN_SCREEN_FRACTION = 0.85
        private const val DESIRED_TARGET_SCREEN_FRACTION = 0.333
    }

    fun plan(
        mode: CameraMode,
        location: LocationSnapshot?,
        headingDegrees: Double?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        return when (mode) {
            CameraMode.ROUTE_OVERVIEW -> planRouteOverview(location, mission, viewport)
            CameraMode.FOLLOW_HEADING_UP -> planFollowHeadingUp(location, headingDegrees, mission, viewport)
        }
    }

    private fun planRouteOverview(
        location: LocationSnapshot?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        val routePoints = buildRoutePoints(location, mission)
        val bounds = latLngBoundsForPoints(routePoints)
            ?: LatLngBounds.from(52.52, 13.405, 52.52, 13.405)

        return CameraPlan(
            mode = CameraMode.ROUTE_OVERVIEW,
            bounds = bounds,
            bearing = 0.0,
            tilt = 0.0,
        )
    }

    private fun planFollowHeadingUp(
        location: LocationSnapshot?,
        headingDegrees: Double?,
        mission: ActiveMission?,
        viewport: Viewport,
    ): CameraPlan {
        if (mission == null) {
            return CameraPlan(
                mode = CameraMode.FOLLOW_HEADING_UP,
                target = LatLng(52.52, 13.405),
                zoom = MIN_FOLLOW_ZOOM,
                bearing = normalizeDegrees(headingDegrees ?: 0.0),
                tilt = 0.0,
            )
        }

        val playerLocation = if (location != null) {
            LatLng(location.latitude, location.longitude)
        } else if (mission.routeOrigin != null && mission.routeOrigin.isValid()) {
            LatLng(mission.routeOrigin.latitude, mission.routeOrigin.longitude)
        } else {
            LatLng(mission.target.latitude, mission.target.longitude)
        }

        val targetLocation = LatLng(mission.target.latitude, mission.target.longitude)

        // Spieler direkt auf Ziel → maximal reinzoomen, Target = Zielposition
        if (playerLocation.latitude == targetLocation.latitude &&
            playerLocation.longitude == targetLocation.longitude
        ) {
            return CameraPlan(
                mode = CameraMode.FOLLOW_HEADING_UP,
                target = targetLocation,
                zoom = MAX_FOLLOW_ZOOM,
                bearing = normalizeDegrees(headingDegrees ?: 0.0),
                tilt = 0.0,
            )
        }

        val effectiveHeading = headingDegrees ?: bearingBetween(playerLocation, targetLocation)

        val playerM = latLngToMercator(playerLocation)
        val targetM = latLngToMercator(targetLocation)

        // Wenn die Distanz groß ist und das Target fast in Blickrichtung liegt,
        // würden die Punkte im Heading-Up-Frame fast übereinander liegen.
        // In diesem Fall auf Overview umschalten für maximale Spannweite.
        val distanceMeters = haversineDistance(
            playerLocation.latitude, playerLocation.longitude,
            targetLocation.latitude, targetLocation.longitude,
        )
        val bearingToTarget = bearingBetween(playerLocation, targetLocation)
        val headingTargetDiff = headingDelta(effectiveHeading, bearingToTarget)
        // Wenn Distanz > 5km UND Target innerhalb von 30° der Blickrichtung
        if (distanceMeters > 5000.0 && headingTargetDiff < 30.0) {
            return planRouteOverview(location, mission, viewport)
        }

        // Relative Position im rotierten System (Heading-Up)
        val dx = targetM.first - playerM.first
        val dy = targetM.second - playerM.second

        val angleRad = Math.toRadians(effectiveHeading)
        val cosAngle = cos(angleRad)
        val sinAngle = sin(angleRad)
        val dxR = dx * cosAngle - dy * sinAngle
        val dyR = dx * sinAngle + dy * cosAngle

        val boundsWidth = kotlin.math.abs(dxR)
        val boundsHeight = kotlin.math.abs(dyR)

        val effectiveTopPx = max(viewport.topPaddingPx, (viewport.heightPx / 3.0).toInt())
        val effectiveBottomPx = viewport.bottomPaddingPx
        val visibleWidthPx = (viewport.widthPx - viewport.leftPaddingPx - viewport.rightPaddingPx)
            .coerceAtLeast(100)
        val visibleHeightPx = (viewport.heightPx - effectiveTopPx - effectiveBottomPx)
            .coerceAtLeast(100)

        val scale0 = TILE_SIZE_PX / EARTH_CIRCUMFERENCE_METERS

        val zoomX = if (boundsWidth > 0.0) {
            ln(visibleWidthPx * 0.92 / (boundsWidth * scale0)) / ln(2.0)
        } else {
            Double.POSITIVE_INFINITY
        }
        val zoomY = if (boundsHeight > 0.0) {
            ln(visibleHeightPx * 0.92 / (boundsHeight * scale0)) / ln(2.0)
        } else {
            Double.POSITIVE_INFINITY
        }
        val zoom = min(zoomX, zoomY).coerceAtMost(MAX_FOLLOW_ZOOM)

        val scale = scale0 * 2.0.pow(zoom)

        // Höhe der Bounds in Pixeln bei diesem Zoom
        val boundsHeightPixels = boundsHeight * scale

        // Ideal-Mittelpunkt zwischen O und X auf dem Screen
        val desiredMidScreenY = viewport.heightPx *
            (DESIRED_ORIGIN_SCREEN_FRACTION + DESIRED_TARGET_SCREEN_FRACTION) / 2.0

        // Clamp auf den sichtbaren Bereich, damit beide Punkte immer im Screen bleiben
        val effectiveBoundsHeight = min(boundsHeightPixels, visibleHeightPx.toDouble())
        val minMidY = effectiveTopPx + effectiveBoundsHeight / 2.0
        val maxMidY = viewport.heightPx - effectiveBottomPx - effectiveBoundsHeight / 2.0
        val actualMidScreenY = when {
            minMidY > maxMidY -> (effectiveTopPx + viewport.heightPx - effectiveBottomPx) / 2.0
            else -> desiredMidScreenY.coerceIn(minMidY, maxMidY)
        }

        // Target im rotierten Mercator-System (relativ zu O)
        val targetRelX = dxR / 2.0
        val targetRelY = dyR / 2.0 + (actualMidScreenY - viewport.heightPx / 2.0) / scale

        // Rotiere zurück ins Welt-Mercator-System (Inverse der Vorwärtsrotation)
        val backAngleRad = Math.toRadians(-effectiveHeading)
        val cosBack = cos(backAngleRad)
        val sinBack = sin(backAngleRad)
        val targetRelM_x = targetRelX * cosBack - targetRelY * sinBack
        val targetRelM_y = targetRelX * sinBack + targetRelY * cosBack

        val cameraTarget = mercatorToLatLng(
            playerM.first + targetRelM_x,
            playerM.second + targetRelM_y,
        )

        return CameraPlan(
            mode = CameraMode.FOLLOW_HEADING_UP,
            target = cameraTarget,
            zoom = zoom,
            bearing = normalizeDegrees(effectiveHeading),
            tilt = 0.0,
        )
    }

    private fun buildRoutePoints(location: LocationSnapshot?, mission: ActiveMission?): List<LatLng> {
        if (mission == null) return emptyList()
        val result = mutableListOf<LatLng>()

        val routeOrigin = mission.routeOrigin
        if (routeOrigin != null && routeOrigin.isValid()) {
            result.add(LatLng(routeOrigin.latitude, routeOrigin.longitude))
        } else if (location != null) {
            result.add(LatLng(location.latitude, location.longitude))
        }

        mission.waypoints.forEach { waypoint ->
            if (waypoint.isValid()) {
                result.add(LatLng(waypoint.latitude, waypoint.longitude))
            }
        }

        if (mission.target.isValid()) {
            result.add(LatLng(mission.target.latitude, mission.target.longitude))
        }

        return result
    }

    private fun latLngBoundsForPoints(points: List<LatLng>): LatLngBounds? {
        if (points.isEmpty()) return null
        var minLat = points[0].latitude
        var maxLat = points[0].latitude
        var minLon = points[0].longitude
        var maxLon = points[0].longitude
        for (point in points.drop(1)) {
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }
        if (minLat == maxLat && minLon == maxLon) {
            val pad = 0.001
            return LatLngBounds.from(maxLat + pad, maxLon + pad, minLat - pad, minLon - pad)
        }
        return LatLngBounds.from(maxLat, maxLon, minLat, minLon)
    }

    private fun latLngToMercator(latLng: LatLng): Pair<Double, Double> {
        val x = Math.toRadians(latLng.longitude) * EARTH_RADIUS_METERS
        val y = ln(
            kotlin.math.tan(Math.PI / 4.0 + Math.toRadians(latLng.latitude) / 2.0)
        ) * EARTH_RADIUS_METERS
        return x to y
    }

    private fun mercatorToLatLng(x: Double, y: Double): LatLng {
        val lon = Math.toDegrees(x / EARTH_RADIUS_METERS)
        val lat = Math.toDegrees(
            2.0 * kotlin.math.atan(kotlin.math.exp(y / EARTH_RADIUS_METERS)) - Math.PI / 2.0
        )
        return LatLng(lat, lon)
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingBetween(start: LatLng, end: LatLng): Double {
        val startLat = Math.toRadians(start.latitude)
        val startLon = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLon = Math.toRadians(end.longitude)
        val dLon = endLon - startLon
        val y = sin(dLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun normalizeDegrees(value: Double): Double {
        var result = value % 360.0
        if (result < 0) result += 360.0
        return result
    }

    /** Kleinster Winkel zwischen zwei Heading-Werten (0–180°) */
    fun headingDelta(a: Double, b: Double): Double {
        val diff = kotlin.math.abs(normalizeDegrees(a) - normalizeDegrees(b))
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /**
     * Prüft, ob der Spieler das Ziel erreicht hat.
     * Threshold: 15 Meter (konfigurierbar, default aus Issue #58).
     */
    fun hasArrived(
        playerLat: Double,
        playerLon: Double,
        targetLat: Double,
        targetLon: Double,
        thresholdMeters: Double = 15.0,
    ): Boolean {
        return haversineDistance(playerLat, playerLon, targetLat, targetLon) <= thresholdMeters
    }
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
)
