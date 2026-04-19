package com.cachekid.companion.data

import kotlinx.coroutines.flow.Flow

interface DeviceHeadingProvider {
    val headingDegrees: Flow<Float>
}
