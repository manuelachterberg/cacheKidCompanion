package com.cachekid.companion.host.mission

data class ActiveMission(
    val missionId: String,
    val cacheCode: String,
    val sourceTitle: String,
    val childTitle: String,
    val summary: String,
    val target: MissionTarget,
    val sourceApp: String? = null,
    val offlineMap: MissionOfflineMap? = null,
)
