package com.cachekid.companion.host.mission

class MissionPackageReader {

    fun read(files: Map<String, String>): MissionPackage? {
        val manifestJson = files["manifest.json"] ?: return null
        val missionId = extractStringValue(manifestJson, "missionId") ?: return null
        val schemaVersion = extractIntValue(manifestJson, "schemaVersion") ?: return null
        val manifestFiles = extractStringArray(manifestJson, "files")

        val manifest = MissionManifest(
            schemaVersion = schemaVersion,
            missionId = missionId,
            files = manifestFiles,
        )

        return MissionPackage(
            missionId = missionId,
            manifest = manifest,
            files = files.toSortedMap().map { (path, content) ->
                MissionPackageFile(path = path, content = content)
            },
        )
    }

    private fun extractStringValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractIntValue(json: String, key: String): Int? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val regex = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = regex.find(json)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex(""""([^"]+)"""").findAll(body).map { it.groupValues[1] }.toList()
    }
}
