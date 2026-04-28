package com.cachekid.companion.host.mission

data class MissionPackageSendResult(
    val isSuccess: Boolean,
    val statusCode: Int?,
    val message: String,
    val status: MissionPackageSendStatus = if (isSuccess) {
        MissionPackageSendStatus.SENT
    } else {
        MissionPackageSendStatus.FAILED
    },
    val receiverStatus: MissionPackageReceiveStatus? = null,
)

enum class MissionPackageSendStatus {
    SENT,
    MISSING_ADDRESS,
    INVALID_ADDRESS,
    INVALID_PORT,
    CONNECTION_FAILED,
    TIMEOUT,
    HTTP_ERROR,
    FAILED,
}
