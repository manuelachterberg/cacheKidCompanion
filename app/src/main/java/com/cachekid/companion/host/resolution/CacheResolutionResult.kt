package com.cachekid.companion.host.resolution

data class CacheResolutionResult(
    val status: CacheResolutionStatus,
    val value: ResolvedCacheDetails?,
    val cacheCodeHint: String? = null,
    val messages: List<String>,
)
