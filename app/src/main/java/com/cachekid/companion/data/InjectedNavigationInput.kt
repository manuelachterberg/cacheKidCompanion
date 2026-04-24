package com.cachekid.companion.data

data class InjectedNavigationInput(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val headingDegrees: Float? = null,
    val capturedAtEpochMillis: Long,
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    val hasHeading: Boolean
        get() = headingDegrees != null
}
