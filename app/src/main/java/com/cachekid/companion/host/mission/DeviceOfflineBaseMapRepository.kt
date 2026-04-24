package com.cachekid.companion.host.mission

import java.io.File
import java.util.Base64

class DeviceOfflineBaseMapRepository(
    private val baseDirectory: File,
) {

    fun loadFor(target: MissionTarget): MissionOfflineMap? {
        if (!baseDirectory.exists() || !baseDirectory.isDirectory) {
            return null
        }

        val maps = baseDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { mapDirectory -> loadMapFromDirectory(mapDirectory) }
            .orEmpty()

        return maps.firstOrNull { map -> map.bounds.contains(target) }
            ?: maps
                .map { map -> map to map.bounds.distanceTo(target) }
                .minByOrNull { (_, distance) -> distance }
                ?.first
    }

    private fun loadMapFromDirectory(mapDirectory: File): MissionOfflineMap? {
        val metaJson = File(mapDirectory, MissionPackageSchema.MAP_METADATA_FILE)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val assetPath = selectPreferredAssetPath(
            mapDirectory = mapDirectory,
            configuredAssetPath = extractStringValue(metaJson, "assetPath"),
        ) ?: return null
        val boundsJson = extractObject(metaJson, "bounds") ?: return null
        val svgContent = File(mapDirectory, assetPath)
            .takeIf { it.exists() && it.isFile }
            ?.let { mapAsset -> renderMapAsset(mapAsset, assetPath) }
            ?: return null

        return MissionOfflineMap(
            svgContent = svgContent,
            assetPath = assetPath,
            bounds = MissionMapBounds(
                minLatitude = extractDoubleValue(boundsJson, "minLatitude") ?: return null,
                minLongitude = extractDoubleValue(boundsJson, "minLongitude") ?: return null,
                maxLatitude = extractDoubleValue(boundsJson, "maxLatitude") ?: return null,
                maxLongitude = extractDoubleValue(boundsJson, "maxLongitude") ?: return null,
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

    private fun selectPreferredAssetPath(
        mapDirectory: File,
        configuredAssetPath: String?,
    ): String? {
        val pngFile = File(mapDirectory, MissionPackageSchema.MAP_PNG_FILE)
        if (pngFile.exists() && pngFile.isFile) {
            return MissionPackageSchema.MAP_PNG_FILE
        }

        val configuredFile = configuredAssetPath
            ?.let { File(mapDirectory, it) }
            ?.takeIf { it.exists() && it.isFile }
        if (configuredFile != null) {
            return configuredFile.name
        }

        val svgFile = File(mapDirectory, MissionPackageSchema.MAP_SVG_FILE)
        return if (svgFile.exists() && svgFile.isFile) {
            MissionPackageSchema.MAP_SVG_FILE
        } else {
            null
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
}
