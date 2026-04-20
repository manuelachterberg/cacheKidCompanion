package com.cachekid.companion.host.mission

import java.io.File

class ActiveMissionRepository {

    fun loadLatest(baseDirectory: File): ActiveMission? {
        if (!baseDirectory.exists() || !baseDirectory.isDirectory) {
            return null
        }

        val latestMissionDirectory = baseDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.lastModified() }
            ?: return null

        return loadFromDirectory(latestMissionDirectory)
    }

    fun loadFromDirectory(missionDirectory: File): ActiveMission? {
        if (!missionDirectory.exists() || !missionDirectory.isDirectory) {
            return null
        }

        val missionJson = File(missionDirectory, "mission.json")
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val missionId = extractStringValue(missionJson, "missionId") ?: return null
        val cacheCode = extractStringValue(missionJson, "cacheCode") ?: return null
        val sourceTitle = extractStringValue(missionJson, "sourceTitle") ?: return null
        val childTitle = extractStringValue(missionJson, "childTitle") ?: return null
        val summary = extractStringValue(missionJson, "summary") ?: return null
        val targetLatitude = extractDoubleValue(missionJson, "latitude") ?: return null
        val targetLongitude = extractDoubleValue(missionJson, "longitude") ?: return null
        val sourceApp = extractNullableStringValue(missionJson, "sourceApp")
        val offlineMap = loadOfflineMap(missionDirectory)

        return ActiveMission(
            missionId = missionId,
            cacheCode = cacheCode,
            sourceTitle = sourceTitle,
            childTitle = childTitle,
            summary = summary,
            target = MissionTarget(targetLatitude, targetLongitude),
            sourceApp = sourceApp,
            offlineMap = offlineMap,
        )
    }

    private fun loadOfflineMap(missionDirectory: File): MissionOfflineMap? {
        val metaJson = File(missionDirectory, MissionPackageSchema.MAP_METADATA_FILE)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val assetPath = extractStringValue(metaJson, "assetPath") ?: MissionPackageSchema.MAP_SVG_FILE
        val svgContent = File(missionDirectory, assetPath)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val minLatitude = extractDoubleValue(metaJson, "minLatitude") ?: return null
        val minLongitude = extractDoubleValue(metaJson, "minLongitude") ?: return null
        val maxLatitude = extractDoubleValue(metaJson, "maxLatitude") ?: return null
        val maxLongitude = extractDoubleValue(metaJson, "maxLongitude") ?: return null

        return MissionOfflineMap(
            svgContent = svgContent,
            assetPath = assetPath,
            bounds = MissionMapBounds(
                minLatitude = minLatitude,
                minLongitude = minLongitude,
                maxLatitude = maxLatitude,
                maxLongitude = maxLongitude,
            ),
        )
    }

    private fun extractStringValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractNullableStringValue(json: String, key: String): String? {
        val nullableRegex = Regex(""""$key"\s*:\s*null""")
        if (nullableRegex.containsMatchIn(json)) {
            return null
        }
        return extractStringValue(json, key)
    }

    private fun extractDoubleValue(json: String, key: String): Double? {
        val regex = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}
