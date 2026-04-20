package com.cachekid.companion.host.mission

object MissionPackageSchema {
    const val CURRENT_SCHEMA_VERSION: Int = 2
    const val INTEGRITY_FILE = "integrity.json"
    const val MANIFEST_FILE = "manifest.json"
    const val MISSION_FILE = "mission.json"
    const val MAP_METADATA_FILE = "map-meta.json"
    const val MAP_SVG_FILE = "map.svg"

    val requiredCoreFiles: List<String> = listOf(
        INTEGRITY_FILE,
        MANIFEST_FILE,
        MISSION_FILE,
    )

    val optionalMapFiles: List<String> = listOf(
        MAP_METADATA_FILE,
        MAP_SVG_FILE,
    )
}
