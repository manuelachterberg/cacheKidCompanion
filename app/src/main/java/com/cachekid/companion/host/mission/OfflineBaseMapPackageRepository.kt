package com.cachekid.companion.host.mission

import java.io.File

class OfflineBaseMapPackageRepository(
    private val baseDirectory: File,
) {

    fun listInstalled(): List<OfflineBaseMapPackage> {
        if (!baseDirectory.exists() || !baseDirectory.isDirectory) {
            return emptyList()
        }

        return baseDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { packageDirectory -> loadPackage(packageDirectory) }
            ?.sortedBy { it.displayName }
            .orEmpty()
    }

    fun findCovering(target: MissionTarget): OfflineBaseMapPackage? {
        return listInstalled().firstOrNull { offlinePackage -> offlinePackage.covers(target) }
    }

    private fun loadPackage(packageDirectory: File): OfflineBaseMapPackage? {
        val metadata = File(packageDirectory, MissionPackageSchema.OFFLINE_MAP_METADATA_FILE)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val format = OfflineBaseMapPackageFormat.fromMetadataValue(extractStringValue(metadata, "format"))
            ?: return null
        val tileAssetPath = extractStringValue(metadata, "tileAssetPath")
            ?: MissionPackageSchema.OFFLINE_MAP_PMTILES_FILE
        val styleAssetPath = extractStringValue(metadata, "styleAssetPath")
            ?: MissionPackageSchema.OFFLINE_MAP_STYLE_FILE
        val boundsJson = extractObject(metadata, "bounds") ?: return null

        if (!File(packageDirectory, tileAssetPath).isFile || !File(packageDirectory, styleAssetPath).isFile) {
            return null
        }

        return OfflineBaseMapPackage(
            id = extractStringValue(metadata, "id") ?: packageDirectory.name,
            displayName = extractStringValue(metadata, "displayName") ?: packageDirectory.name,
            version = extractStringValue(metadata, "version") ?: "0",
            format = format,
            bounds = MissionMapBounds(
                minLatitude = extractDoubleValue(boundsJson, "minLatitude") ?: return null,
                minLongitude = extractDoubleValue(boundsJson, "minLongitude") ?: return null,
                maxLatitude = extractDoubleValue(boundsJson, "maxLatitude") ?: return null,
                maxLongitude = extractDoubleValue(boundsJson, "maxLongitude") ?: return null,
            ),
            tileAssetPath = tileAssetPath,
            styleAssetPath = styleAssetPath,
            minZoom = extractIntValue(metadata, "minZoom") ?: 0,
            maxZoom = extractIntValue(metadata, "maxZoom") ?: 14,
            attribution = extractStringValue(metadata, "attribution"),
        )
    }

    private fun extractStringValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractDoubleValue(json: String, key: String): Double? {
        val regex = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractIntValue(json: String, key: String): Int? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
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
