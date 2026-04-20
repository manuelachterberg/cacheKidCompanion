package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.importing.SharedCacheImport

class HostCacheResolver : CacheResolver {

    private val coordInfoRegex = Regex(
        """https?://(?:www\.)?coord\.info/(GC[A-Z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )

    override fun resolve(import: SharedCacheImport): CacheResolutionResult {
        if (import.cacheCode != null && import.sourceTitle != null && import.target != null) {
            return CacheResolutionResult(
                status = CacheResolutionStatus.RESOLVED,
                value = ResolvedCacheDetails(
                    cacheCode = import.cacheCode,
                    title = import.sourceTitle,
                    target = import.target,
                    sourceApp = import.sourceApp,
                ),
                cacheCodeHint = import.cacheCode,
                messages = listOf("Cache details are complete enough to build a mission draft."),
            )
        }

        val hasCoordInfoLink = coordInfoRegex.containsMatchIn(import.rawText)
        val hasCacheCode = import.cacheCode != null

        if (hasCoordInfoLink || hasCacheCode) {
            return CacheResolutionResult(
                status = CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION,
                value = null,
                cacheCodeHint = import.cacheCode,
                messages = listOf(
                    "Cache link or GC code erkannt.",
                    "Fuer vollstaendige Cache-Daten ist jetzt eine Online-Aufloesung auf dem Smartphone noetig.",
                ),
            )
        }

        return CacheResolutionResult(
            status = CacheResolutionStatus.UNRESOLVED,
            value = null,
            cacheCodeHint = import.cacheCode,
            messages = listOf("Shared content does not contain enough cache information to continue."),
        )
    }
}
