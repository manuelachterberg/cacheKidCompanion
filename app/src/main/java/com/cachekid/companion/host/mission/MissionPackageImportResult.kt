package com.cachekid.companion.host.mission

import java.io.File

data class MissionPackageImportResult(
    val missionDirectory: File?,
    val missionId: String?,
    val errors: List<String>,
) {
    val isSuccess: Boolean
        get() = missionDirectory != null && missionId != null && errors.isEmpty()
}
