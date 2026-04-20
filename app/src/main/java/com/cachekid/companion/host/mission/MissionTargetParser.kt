package com.cachekid.companion.host.mission

class MissionTargetParser {

    fun parse(text: String): MissionTarget? {
        val match = DECIMAL_COORDINATE_REGEX.matchEntire(text.trim()) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return MissionTarget(latitude = latitude, longitude = longitude).takeIf { it.isValid() }
    }

    fun format(target: MissionTarget): String {
        return "${target.latitude},${target.longitude}"
    }

    private companion object {
        val DECIMAL_COORDINATE_REGEX = Regex("""\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*""")
    }
}
