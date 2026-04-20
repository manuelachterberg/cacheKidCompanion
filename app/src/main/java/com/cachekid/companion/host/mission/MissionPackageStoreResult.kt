package com.cachekid.companion.host.mission

import java.io.File

data class MissionPackageStoreResult(
    val missionDirectory: File?,
    val errors: List<String>,
) {
    val isSuccess: Boolean
        get() = missionDirectory != null && errors.isEmpty()
}
