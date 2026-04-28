package com.cachekid.companion.host.mission

data class MissionPackageReceiveResponse(
    val status: MissionPackageReceiveStatus,
    val missionId: String?,
    val message: String,
    val errors: List<String> = emptyList(),
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"status\":\"").append(status.name).append("\",")
            append("\"missionId\":")
            if (missionId == null) {
                append("null")
            } else {
                append("\"").append(escapeJson(missionId)).append("\"")
            }
            append(",\"message\":\"").append(escapeJson(message)).append("\",")
            append("\"errors\":[")
            errors.forEachIndexed { index, error ->
                if (index > 0) append(",")
                append("\"").append(escapeJson(error)).append("\"")
            }
            append("]}")
        }
    }

    companion object {
        fun parse(json: String): MissionPackageReceiveResponse? {
            val status = extractString(json, "status")
                ?.let { runCatching { MissionPackageReceiveStatus.valueOf(it) }.getOrNull() }
                ?: return null
            val message = extractString(json, "message") ?: return null
            val missionId = extractNullableString(json, "missionId")
            val errors = extractStringArray(json, "errors")
            return MissionPackageReceiveResponse(
                status = status,
                missionId = missionId,
                message = message,
                errors = errors,
            )
        }

        private fun extractString(json: String, key: String): String? {
            val regex = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
            return regex.find(json)?.groupValues?.getOrNull(1)?.unescapeJson()
        }

        private fun extractNullableString(json: String, key: String): String? {
            if (Regex(""""$key"\s*:\s*null""").containsMatchIn(json)) {
                return null
            }
            return extractString(json, key)
        }

        private fun extractStringArray(json: String, key: String): List<String> {
            val regex = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            val body = regex.find(json)?.groupValues?.getOrNull(1) ?: return emptyList()
            return Regex(""""((?:\\.|[^"\\])*)"""")
                .findAll(body)
                .map { it.groupValues[1].unescapeJson() }
                .toList()
        }
    }
}

enum class MissionPackageReceiveStatus {
    IMPORTED,
    UNSUPPORTED_ENDPOINT,
    MISSING_LENGTH,
    INVALID_LENGTH,
    UNSUPPORTED_MEDIA_TYPE,
    EMPTY_BODY,
    INCOMPLETE_BODY,
    INVALID_ZIP,
    INVALID_MANIFEST,
    VALIDATION_FAILED,
    STORE_FAILED,
    FAILED,
}

private fun escapeJson(value: String): String {
    return buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

private fun String.unescapeJson(): String {
    return replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
