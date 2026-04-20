package com.cachekid.companion.host.mission

data class MissionManifest(
    val schemaVersion: Int,
    val missionId: String,
    val files: List<String>,
)
