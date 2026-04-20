package com.cachekid.companion.host.importing

data class SharedCacheImportResult(
    val status: SharedCacheImportStatus,
    val value: SharedCacheImport?,
    val messages: List<String>,
)
