package com.cachekid.companion.host.importing

import com.cachekid.companion.host.mission.MissionTarget

data class SharedCacheImport(
    val rawText: String,
    val cacheCode: String?,
    val sourceTitle: String?,
    val target: MissionTarget?,
    val sourceApp: String? = null,
)
