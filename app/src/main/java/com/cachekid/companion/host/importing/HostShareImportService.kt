package com.cachekid.companion.host.importing

class HostShareImportService(
    private val parser: SharedCacheParser = SharedCacheParser(),
) {

    fun importFrom(payload: SharedTextPayload): SharedCacheImportResult {
        if (
            payload.action != "android.intent.action.SEND" &&
            payload.action != "android.intent.action.VIEW"
        ) {
            return SharedCacheImportResult(
                status = SharedCacheImportStatus.INVALID,
                value = null,
                messages = listOf("Unsupported share action."),
            )
        }

        if (payload.mimeType.isNullOrBlank()) {
            return SharedCacheImportResult(
                status = SharedCacheImportStatus.INVALID,
                value = null,
                messages = listOf("Missing share MIME type."),
            )
        }

        if (!payload.mimeType.startsWith("text/")) {
            return SharedCacheImportResult(
                status = SharedCacheImportStatus.INVALID,
                value = null,
                messages = listOf("Only text share payloads are supported."),
            )
        }

        return parser.parse(
            sharedText = payload.text.orEmpty(),
            sourceApp = payload.sourceApp,
        )
    }
}
