package com.cachekid.companion.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writes mission packages to a portable format.
 * Shared between Android and iOS.
 */
object MissionPackageWriter {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    fun writePackage(mission: ActiveMission, routePoints: List<MissionWaypoint> = emptyList()): String {
        val package_data = MissionPackage(
            mission = mission,
            routePoints = routePoints,
            createdAt = currentTimeMillis(),
        )
        return json.encodeToString(package_data)
    }
    
    fun readPackage(jsonString: String): MissionPackage? {
        return try {
            json.decodeFromString<MissionPackage>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun currentTimeMillis(): Long {
        // Common code — platform-specific implementations can override
        return 0L // Placeholder, actual implementation uses expect/actual
    }
}
