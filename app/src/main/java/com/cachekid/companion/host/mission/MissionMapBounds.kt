package com.cachekid.companion.host.mission

data class MissionMapBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    fun contains(target: MissionTarget): Boolean {
        return target.latitude in minLatitude..maxLatitude &&
            target.longitude in minLongitude..maxLongitude
    }

    fun distanceTo(target: MissionTarget): Double {
        val clampedLatitude = target.latitude.coerceIn(minLatitude, maxLatitude)
        val clampedLongitude = target.longitude.coerceIn(minLongitude, maxLongitude)
        val deltaLatitude = target.latitude - clampedLatitude
        val deltaLongitude = target.longitude - clampedLongitude
        return kotlin.math.sqrt((deltaLatitude * deltaLatitude) + (deltaLongitude * deltaLongitude))
    }
}
