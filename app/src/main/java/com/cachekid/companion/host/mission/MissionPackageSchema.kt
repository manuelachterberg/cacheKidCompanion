package com.cachekid.companion.host.mission

object MissionPackageSchema {
    const val CURRENT_SCHEMA_VERSION: Int = 1

    val requiredManifestFiles: List<String> = listOf(
        "manifest.json",
        "mission.json",
    )
}
