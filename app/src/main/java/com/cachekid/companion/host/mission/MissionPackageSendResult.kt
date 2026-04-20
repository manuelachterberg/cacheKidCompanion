package com.cachekid.companion.host.mission

data class MissionPackageSendResult(
    val isSuccess: Boolean,
    val statusCode: Int?,
    val message: String,
)
