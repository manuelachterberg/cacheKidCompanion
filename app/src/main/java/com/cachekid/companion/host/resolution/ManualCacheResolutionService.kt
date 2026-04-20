package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.mission.MissionTarget
import com.cachekid.companion.host.mission.MissionTargetParser

class ManualCacheResolutionService {

    private val missionTargetParser = MissionTargetParser()

    fun resolve(
        cacheCode: String?,
        title: String,
        coordinateText: String,
        sourceApp: String? = null,
    ): ResolvedCacheDetails? {
        val normalizedCode = cacheCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedTitle = title.trim().takeIf { it.isNotBlank() } ?: return null
        val target = missionTargetParser.parse(coordinateText) ?: return null

        return ResolvedCacheDetails(
            cacheCode = normalizedCode,
            title = normalizedTitle,
            target = target,
            sourceApp = sourceApp,
        )
    }
}
