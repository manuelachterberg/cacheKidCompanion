package com.cachekid.companion.host.mission

data class MissionPackageWriteResult(
    val missionPackage: MissionPackage?,
    val errors: List<String>,
) {
    val isSuccess: Boolean
        get() = missionPackage != null && errors.isEmpty()
}
