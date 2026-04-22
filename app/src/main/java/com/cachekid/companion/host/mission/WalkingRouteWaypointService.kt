package com.cachekid.companion.host.mission

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class WalkingRouteWaypointService(
    private val responseFetcher: (String) -> String? = ::defaultFetch,
    private val maxWaypoints: Int = 24,
) {

    fun buildWaypoints(
        origin: MissionTarget,
        target: MissionTarget,
    ): List<MissionWaypoint> {
        if (!origin.isValid() || !target.isValid()) {
            debugLog("routing skipped because origin or target is invalid")
            return emptyList()
        }

        val routeUrl = buildRouteUrl(origin, target)
        debugLog(
            "routing start origin=${origin.latitude},${origin.longitude} target=${target.latitude},${target.longitude}",
        )
        val response = responseFetcher(routeUrl)
        if (response == null) {
            debugLog("routing fetch returned no body")
            return emptyList()
        }
        val coordinates = extractCoordinates(response)
        debugLog("routing parsed rawCoordinates=${coordinates.size}")
        if (coordinates.size <= 2) {
            debugLog("routing produced no interior route points")
            return emptyList()
        }

        val interiorPoints = coordinates.drop(1).dropLast(1)
            .distinctBy { waypointKey(it.latitude, it.longitude) }
        if (interiorPoints.isEmpty()) {
            debugLog("routing interior points collapsed to empty set")
            return emptyList()
        }

        val sampledPoints = sampleInteriorPoints(interiorPoints)
        debugLog("routing sampledWaypoints=${sampledPoints.size}")
        return sampledPoints.map { point ->
            MissionWaypoint(
                latitude = point.latitude,
                longitude = point.longitude,
            )
        }
    }

    private fun buildRouteUrl(
        origin: MissionTarget,
        target: MissionTarget,
    ): String {
        return buildString {
            append("https://router.project-osrm.org/route/v1/foot/")
            append(origin.longitude)
            append(",")
            append(origin.latitude)
            append(";")
            append(target.longitude)
            append(",")
            append(target.latitude)
            append("?overview=full&geometries=geojson&steps=false")
        }
    }

    private fun extractCoordinates(response: String): List<MissionTarget> {
        val geometryIndex = response.indexOf(""""geometry"""")
        if (geometryIndex < 0) {
            debugLog("routing response contains no geometry block")
            return emptyList()
        }

        val coordinatesKeyIndex = response.indexOf(""""coordinates"""", startIndex = geometryIndex)
        if (coordinatesKeyIndex < 0) {
            debugLog("routing geometry contains no coordinates key")
            return emptyList()
        }

        val arrayStart = response.indexOf('[', startIndex = coordinatesKeyIndex)
        if (arrayStart < 0) {
            debugLog("routing coordinates array start missing")
            return emptyList()
        }

        val arrayEnd = findMatchingBracket(response, arrayStart) ?: run {
            debugLog("routing coordinates array end missing")
            return emptyList()
        }

        val coordinatesBlock = response.substring(arrayStart, arrayEnd + 1)

        val pairMatches = Regex(
            """\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]""",
        ).findAll(coordinatesBlock)

        return buildList {
            pairMatches.forEach { match ->
                val longitude = match.groupValues[1].toDoubleOrNull() ?: return@forEach
                val latitude = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                val target = MissionTarget(latitude = latitude, longitude = longitude)
                if (target.isValid()) {
                    add(target)
                }
            }
        }
    }

    private fun findMatchingBracket(text: String, startIndex: Int): Int? {
        var depth = 0
        for (index in startIndex until text.length) {
            when (text[index]) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }

    private fun sampleInteriorPoints(points: List<MissionTarget>): List<MissionTarget> {
        if (points.size <= maxWaypoints) {
            return points
        }

        if (maxWaypoints <= 0) {
            return emptyList()
        }

        if (maxWaypoints == 1) {
            return listOf(points[points.lastIndex / 2])
        }

        return buildList {
            val denominator = maxWaypoints - 1
            val maxIndex = points.lastIndex
            for (step in 0 until maxWaypoints) {
                val index = ((step.toDouble() * maxIndex) / denominator)
                    .toInt()
                    .coerceIn(0, maxIndex)
                add(points[index])
            }
        }.distinctBy { waypointKey(it.latitude, it.longitude) }
    }

    private fun waypointKey(latitude: Double, longitude: Double): String {
        return "%.6f,%.6f".format(latitude, longitude)
    }

    private companion object {
        const val LOG_TAG = "CacheKidRoute"

        fun defaultFetch(url: String): String? {
            val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
            return try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "CacheKidCompanion/0.1")
                val statusCode = connection.responseCode
                runCatching {
                    Log.d(LOG_TAG, "routing http status=$statusCode url=$url")
                }
                if (statusCode !in 200..299) {
                    null
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (error: Exception) {
                runCatching {
                    Log.d(LOG_TAG, "routing fetch failed: ${error::class.java.simpleName}: ${error.message}")
                }
                null
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun debugLog(message: String) {
        runCatching {
            Log.d(LOG_TAG, message)
        }
    }
}
