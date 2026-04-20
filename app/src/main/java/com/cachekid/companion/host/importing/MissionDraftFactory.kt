package com.cachekid.companion.host.importing

import com.cachekid.companion.host.mission.MissionDraft
import com.cachekid.companion.host.resolution.ResolvedCacheDetails

class MissionDraftFactory {

    fun createFrom(import: SharedCacheImport): MissionDraft? {
        val cacheCode = import.cacheCode ?: return null
        val title = import.sourceTitle ?: return null
        val target = import.target ?: return null

        return MissionDraft(
            cacheCode = cacheCode,
            sourceTitle = title,
            childTitle = title,
            summary = "Folge der Karte bis zum grossen X.",
            target = target,
            sourceApp = import.sourceApp,
        )
    }

    fun createFrom(resolvedCache: ResolvedCacheDetails): MissionDraft {
        return MissionDraft(
            cacheCode = resolvedCache.cacheCode,
            sourceTitle = resolvedCache.title,
            childTitle = resolvedCache.title,
            summary = "Folge der Karte bis zum grossen X.",
            target = resolvedCache.target,
            sourceApp = resolvedCache.sourceApp,
        )
    }
}
