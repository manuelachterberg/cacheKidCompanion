package com.cachekid.companion.host.mission

data class MissionWaypoint(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
) {
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
