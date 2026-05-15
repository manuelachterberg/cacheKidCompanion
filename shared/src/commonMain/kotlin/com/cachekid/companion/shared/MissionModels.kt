package com.cachekid.companion.shared

import kotlinx.serialization.Serializable

@Serializable
data class MissionTarget(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class MissionWaypoint(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class ActiveMission(
    val missionId: String,
    val cacheCode: String,
    val sourceTitle: String,
    val childTitle: String,
    val summary: String,
    val target: MissionTarget,
    val routeOrigin: MissionTarget? = null,
    val waypoints: List<MissionWaypoint> = emptyList(),
)

@Serializable
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class MissionPackage(
    val mission: ActiveMission,
    val routePoints: List<MissionWaypoint>,
    val createdAt: Long,
    val version: String = "1.0",
)
