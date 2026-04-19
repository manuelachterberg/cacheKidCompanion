package com.cachekid.companion.data

import java.util.UUID

object BleSensorConfig {
    val serviceUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    val headingCharacteristicUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val gyroCharacteristicUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
}
