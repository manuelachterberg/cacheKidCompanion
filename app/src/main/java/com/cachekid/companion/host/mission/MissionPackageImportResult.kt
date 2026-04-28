package com.cachekid.companion.host.mission

import java.io.File

data class MissionPackageImportResult(
    val missionDirectory: File?,
    val missionId: String?,
    val errors: List<String>,
    val status: MissionPackageImportStatus = if (missionDirectory != null && missionId != null && errors.isEmpty()) {
        MissionPackageImportStatus.IMPORTED
    } else {
        MissionPackageImportStatus.FAILED
    },
) {
    val isSuccess: Boolean
        get() = missionDirectory != null && missionId != null && errors.isEmpty()
}

enum class MissionPackageImportStatus {
    IMPORTED,
    ZIP_DECODE_FAILED,
    MANIFEST_INVALID,
    VALIDATION_FAILED,
    STORE_FAILED,
    FAILED,
}
