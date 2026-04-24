package com.cachekid.companion.host.mission

class OfflineBaseMapPackageManifestReader {

    fun read(metadata: String, fallbackId: String): OfflineBaseMapPackage? {
        val packageId = readPackageId(metadata, fallbackId) ?: return null
        val format = OfflineBaseMapPackageFormat.fromMetadataValue(extractStringValue(metadata, "format"))
            ?: return null
        val boundsJson = extractObject(metadata, "bounds") ?: return null

        return OfflineBaseMapPackage(
            id = packageId,
            displayName = extractStringValue(metadata, "displayName") ?: packageId,
            version = extractStringValue(metadata, "version") ?: "0",
            format = format,
            bounds = MissionMapBounds(
                minLatitude = extractDoubleValue(boundsJson, "minLatitude") ?: return null,
                minLongitude = extractDoubleValue(boundsJson, "minLongitude") ?: return null,
                maxLatitude = extractDoubleValue(boundsJson, "maxLatitude") ?: return null,
                maxLongitude = extractDoubleValue(boundsJson, "maxLongitude") ?: return null,
            ),
            tileAssetPath = extractStringValue(metadata, "tileAssetPath")
                ?: MissionPackageSchema.OFFLINE_MAP_PMTILES_FILE,
            styleAssetPath = extractStringValue(metadata, "styleAssetPath")
                ?: MissionPackageSchema.OFFLINE_MAP_STYLE_FILE,
            minZoom = extractIntValue(metadata, "minZoom") ?: 0,
            maxZoom = extractIntValue(metadata, "maxZoom") ?: 14,
            attribution = extractStringValue(metadata, "attribution"),
        )
    }

    fun readPackageId(metadata: String, fallbackId: String): String? {
        return sanitizePackageId(extractStringValue(metadata, "id") ?: fallbackId)
    }

    private fun sanitizePackageId(rawId: String): String? {
        val normalized = rawId.trim()
        if (normalized.isBlank() || normalized.contains("/") || normalized.contains("\\")) {
            return null
        }
        return normalized.takeIf { Regex("""[a-zA-Z0-9._-]+""").matches(it) }
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
