package com.cachekid.companion.host.mission

import kotlin.math.cos

class MissionMapViewportPlanner {

    fun createBounds(
        target: MissionTarget,
        latitudeSpanMeters: Double = DEFAULT_LATITUDE_SPAN_METERS,
        longitudeSpanMeters: Double = DEFAULT_LONGITUDE_SPAN_METERS,
    ): MissionMapBounds {
        val halfLatDelta = metersToLatitudeDegrees(latitudeSpanMeters / 2.0)
        val halfLonDelta = metersToLongitudeDegrees(
            meters = longitudeSpanMeters / 2.0,
            atLatitude = target.latitude,
        )

        return MissionMapBounds(
            minLatitude = target.latitude - halfLatDelta,
            minLongitude = target.longitude - halfLonDelta,
            maxLatitude = target.latitude + halfLatDelta,
            maxLongitude = target.longitude + halfLonDelta,
        )
    }

    fun scaleBounds(bounds: MissionMapBounds, factor: Double): MissionMapBounds {
        val normalizedFactor = factor.coerceAtLeast(0.1)
        val centerLatitude = (bounds.minLatitude + bounds.maxLatitude) / 2.0
        val centerLongitude = (bounds.minLongitude + bounds.maxLongitude) / 2.0
        val halfLatitudeSpan = ((bounds.maxLatitude - bounds.minLatitude) / 2.0) * normalizedFactor
        val halfLongitudeSpan = ((bounds.maxLongitude - bounds.minLongitude) / 2.0) * normalizedFactor

        return MissionMapBounds(
            minLatitude = centerLatitude - halfLatitudeSpan,
            minLongitude = centerLongitude - halfLongitudeSpan,
            maxLatitude = centerLatitude + halfLatitudeSpan,
            maxLongitude = centerLongitude + halfLongitudeSpan,
        )
    }

    private fun metersToLatitudeDegrees(meters: Double): Double {
        return meters / METERS_PER_LATITUDE_DEGREE
    }

    private fun metersToLongitudeDegrees(meters: Double, atLatitude: Double): Double {
        val latitudeRadians = Math.toRadians(atLatitude)
        val metersPerDegree = (METERS_PER_LATITUDE_DEGREE * cos(latitudeRadians)).coerceAtLeast(MIN_METERS_PER_LONGITUDE_DEGREE)
        return meters / metersPerDegree
    }

    private companion object {
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        const val MIN_METERS_PER_LONGITUDE_DEGREE = 1.0
        const val DEFAULT_LATITUDE_SPAN_METERS = 1_600.0
        const val DEFAULT_LONGITUDE_SPAN_METERS = 1_200.0
    }
}
