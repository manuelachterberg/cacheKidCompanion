package com.cachekid.companion.host.mission

import java.security.MessageDigest
import java.util.Locale

class MissionPackageWriter(
    private val validator: MissionDraftValidator = MissionDraftValidator(),
) {

    fun write(draft: MissionDraft): MissionPackageWriteResult {
        val validation = validator.validate(draft)
        if (!validation.isValid) {
            return MissionPackageWriteResult(
                missionPackage = null,
                errors = validation.errors,
            )
        }

        val missionId = buildMissionId(draft)
        val missionJson = buildMissionJson(draft, missionId)
        val missionSha256 = sha256Hex(missionJson)
        val integrityJson = buildIntegrityJson(missionSha256)
        val manifest = MissionManifest(
            schemaVersion = MissionPackageSchema.CURRENT_SCHEMA_VERSION,
            missionId = missionId,
            files = MissionPackageSchema.requiredManifestFiles,
        )
        val manifestJson = buildManifestJson(manifest)

        val files = listOf(
            MissionPackageFile(path = "integrity.json", content = integrityJson),
            MissionPackageFile(path = "manifest.json", content = manifestJson),
            MissionPackageFile(path = "mission.json", content = missionJson),
        ).sortedBy { it.path }

        return MissionPackageWriteResult(
            missionPackage = MissionPackage(
                missionId = missionId,
                manifest = manifest,
                files = files,
            ),
            errors = emptyList(),
        )
    }

    private fun buildMissionId(draft: MissionDraft): String {
        val normalizedTitle = draft.childTitle
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .ifBlank { "mission" }

        return "${draft.cacheCode.lowercase(Locale.ROOT)}-$normalizedTitle"
    }

    private fun buildMissionJson(draft: MissionDraft, missionId: String): String {
        val escapedSourceApp = draft.sourceApp?.let { "\"${escapeJson(it)}\"" } ?: "null"
        return """
            {
              "schemaVersion": ${MissionPackageSchema.CURRENT_SCHEMA_VERSION},
              "missionId": "${escapeJson(missionId)}",
              "cacheCode": "${escapeJson(draft.cacheCode)}",
              "sourceTitle": "${escapeJson(draft.sourceTitle)}",
              "childTitle": "${escapeJson(draft.childTitle)}",
              "summary": "${escapeJson(draft.summary)}",
              "target": {
                "latitude": ${draft.target.latitude},
                "longitude": ${draft.target.longitude}
              },
              "sourceApp": $escapedSourceApp
            }
        """.trimIndent()
    }

    private fun buildManifestJson(manifest: MissionManifest): String {
        val filesJson = manifest.files.joinToString(",\n") { file ->
            """    "${escapeJson(file)}""""
        }
        return """
            {
              "schemaVersion": ${manifest.schemaVersion},
              "missionId": "${escapeJson(manifest.missionId)}",
              "files": [
$filesJson
              ]
            }
        """.trimIndent()
    }

    private fun buildIntegrityJson(missionSha256: String): String {
        return """
            {
              "algorithm": "sha256",
              "missionJsonSha256": "$missionSha256"
            }
        """.trimIndent()
    }

    private fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}
