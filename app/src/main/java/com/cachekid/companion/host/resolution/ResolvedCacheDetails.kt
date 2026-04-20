package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.mission.MissionTarget

data class ResolvedCacheDetails(
    val cacheCode: String,
    val title: String,
    val target: MissionTarget,
    val sourceApp: String? = null,
)
