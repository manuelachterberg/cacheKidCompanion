package com.cachekid.companion.host.importing

data class SharedTextPayload(
    val action: String?,
    val mimeType: String?,
    val text: String?,
    val sourceApp: String? = null,
)
