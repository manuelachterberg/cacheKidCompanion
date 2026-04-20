package com.cachekid.companion.host.mission

data class MissionPackage(
    val missionId: String,
    val manifest: MissionManifest,
    val files: List<MissionPackageFile>,
)
