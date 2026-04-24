package com.cachekid.companion.host.mission

import java.io.File
import java.util.Base64

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
        val targetJson = extractObject(missionJson, "target") ?: return null
        val targetLatitude = extractDoubleValue(targetJson, "latitude") ?: return null
        val targetLongitude = extractDoubleValue(targetJson, "longitude") ?: return null
        val routeOriginJson = extractObject(missionJson, "routeOrigin")
        val routeOrigin = routeOriginJson?.let { routeJson ->
            val latitude = extractDoubleValue(routeJson, "latitude") ?: return@let null
            val longitude = extractDoubleValue(routeJson, "longitude") ?: return@let null
            MissionTarget(latitude, longitude)
        }
        val waypoints = extractWaypoints(missionJson)
        val sourceApp = extractNullableStringValue(missionJson, "sourceApp")
        val offlineMap = loadOfflineMap(missionDirectory)

        return ActiveMission(
            missionId = missionId,
            cacheCode = cacheCode,
            sourceTitle = sourceTitle,
            childTitle = childTitle,
            summary = summary,
            target = MissionTarget(targetLatitude, targetLongitude),
            routeOrigin = routeOrigin,
            waypoints = waypoints,
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
            ?.let { mapAsset -> renderMapAsset(mapAsset, assetPath) }
            ?: return null

        val boundsJson = extractObject(metaJson, "bounds") ?: return null
        val minLatitude = extractDoubleValue(boundsJson, "minLatitude") ?: return null
        val minLongitude = extractDoubleValue(boundsJson, "minLongitude") ?: return null
        val maxLatitude = extractDoubleValue(boundsJson, "maxLatitude") ?: return null
        val maxLongitude = extractDoubleValue(boundsJson, "maxLongitude") ?: return null

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

    private fun renderMapAsset(assetFile: File, assetPath: String): String {
        return if (assetPath.endsWith(".png", ignoreCase = true)) {
            wrapPngAsSvgSnippet(assetFile.readBytes(), assetPath)
        } else {
            unwrapSvgDocument(assetFile.readText())
        }
    }

    private fun wrapPngAsSvgSnippet(pngBytes: ByteArray, assetPath: String): String {
        val encoded = Base64.getEncoder().encodeToString(pngBytes)
        val filterId = "grayscale-${assetPath.replace(Regex("[^a-zA-Z0-9]+"), "-")}"
        return """
            <defs>
              <filter id="$filterId" color-interpolation-filters="sRGB">
                <feColorMatrix
                  type="matrix"
                  values="0.2126 0.7152 0.0722 0 0
                          0.2126 0.7152 0.0722 0 0
                          0.2126 0.7152 0.0722 0 0
                          0      0      0      1 0"
                />
              </filter>
            </defs>
            <image
              x="0"
              y="0"
              width="100"
              height="140"
              preserveAspectRatio="none"
              filter="url(#$filterId)"
              href="data:image/png;base64,$encoded"
            />
        """.trimIndent()
    }

    private fun unwrapSvgDocument(svgText: String): String {
        val match = Regex(
            """<svg\b[^>]*>(.*)</svg>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(svgText.trim())
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { svgText }
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

    private fun extractObject(json: String, key: String): String? {
        val keyIndex = json.indexOf(""""$key"""")
        if (keyIndex < 0) {
            return null
        }

        val objectStart = json.indexOf('{', startIndex = keyIndex)
        if (objectStart < 0) {
            return null
        }

        var depth = 0
        for (index in objectStart until json.length) {
            when (json[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return json.substring(objectStart, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun extractWaypoints(json: String): List<MissionWaypoint> {
        val arrayMatch = Regex(
            """"waypoints"\s*:\s*\[(.*?)]""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(json) ?: return emptyList()
        val arrayContent = arrayMatch.groupValues[1]
        val objectMatches = Regex(
            """\{(.*?)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).findAll(arrayContent)

        return objectMatches.mapNotNull { match ->
            val objectJson = "{${match.groupValues[1]}}"
            val latitude = extractDoubleValue(objectJson, "latitude") ?: return@mapNotNull null
            val longitude = extractDoubleValue(objectJson, "longitude") ?: return@mapNotNull null
            val label = extractNullableStringValue(objectJson, "label")
            MissionWaypoint(
                latitude = latitude,
                longitude = longitude,
                label = label,
            )
        }.toList()
    }
}
