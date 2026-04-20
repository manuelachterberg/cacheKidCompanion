package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.mission.MissionTarget

class ManualCacheResolutionService {

    fun resolve(
        cacheCode: String?,
        title: String,
        coordinateText: String,
        sourceApp: String? = null,
    ): ResolvedCacheDetails? {
        val normalizedCode = cacheCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedTitle = title.trim().takeIf { it.isNotBlank() } ?: return null
        val target = parseTarget(coordinateText) ?: return null

        return ResolvedCacheDetails(
            cacheCode = normalizedCode,
            title = normalizedTitle,
            target = target,
            sourceApp = sourceApp,
        )
    }

    private fun parseTarget(text: String): MissionTarget? {
        val match = DECIMAL_COORDINATE_REGEX.matchEntire(text.trim()) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return MissionTarget(latitude = latitude, longitude = longitude).takeIf { it.isValid() }
    }

    private companion object {
        val DECIMAL_COORDINATE_REGEX = Regex("""\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*""")
    }
}
