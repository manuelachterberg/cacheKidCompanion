package com.cachekid.companion.host.mission

import java.security.MessageDigest

class MissionPackageValidator {

    fun validate(missionPackage: MissionPackage): MissionPackageValidationResult {
        val errors = buildList {
            val paths = missionPackage.files.map { it.path }.toSet()
            val missing = MissionPackageSchema.requiredCoreFiles.toSet() - paths
            if (missing.isNotEmpty()) {
                add("Mission package is missing required files: ${missing.sorted().joinToString(", ")}")
            }

            val manifestFile = missionPackage.files.firstOrNull { it.path == MissionPackageSchema.MANIFEST_FILE }
            val integrityFile = missionPackage.files.firstOrNull { it.path == MissionPackageSchema.INTEGRITY_FILE }
            val missionFile = missionPackage.files.firstOrNull { it.path == MissionPackageSchema.MISSION_FILE }
            val mapMetaFile = missionPackage.files.firstOrNull { it.path == MissionPackageSchema.MAP_METADATA_FILE }
            val mapSvgFile = missionPackage.files.firstOrNull { it.path == MissionPackageSchema.MAP_SVG_FILE }

            if (manifestFile != null) {
                val manifestMissionId = extractStringValue(manifestFile.content, "missionId")
                val manifestSchemaVersion = extractIntValue(manifestFile.content, "schemaVersion")
                val manifestFileNames = extractStringArray(manifestFile.content, "files")

                if (manifestMissionId != missionPackage.missionId) {
                    add("Manifest missionId does not match package missionId.")
                }
                if (manifestSchemaVersion != MissionPackageSchema.CURRENT_SCHEMA_VERSION) {
                    add("Manifest schemaVersion is not supported.")
                }
                if (!manifestFileNames.containsAll(MissionPackageSchema.requiredCoreFiles)) {
                    add("Manifest file list is missing required mission package files.")
                }
                if (manifestFileNames.toSet() != paths) {
                    add("Manifest file list does not match the actual mission package files.")
                }
            }

            if (integrityFile != null && missionFile != null) {
                val expectedSha = extractStringValue(integrityFile.content, "missionJsonSha256")
                val actualSha = sha256Hex(missionFile.content)
                if (expectedSha != actualSha) {
                    add("Integrity checksum does not match mission.json.")
                }
            }

            if ((mapMetaFile == null) != (mapSvgFile == null)) {
                add("Offline map assets must include both map-meta.json and map.svg.")
            }
        }

        return MissionPackageValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }

    private fun extractStringValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractIntValue(json: String, key: String): Int? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val regex = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = regex.find(json)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex(""""([^"]+)"""").findAll(body).map { it.groupValues[1] }.toList()
    }

    private fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
