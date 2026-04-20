package com.cachekid.companion.host.mission

data class MissionDraft(
    val cacheCode: String,
    val sourceTitle: String,
    val childTitle: String,
    val summary: String,
    val target: MissionTarget,
    val sourceApp: String? = null,
)
