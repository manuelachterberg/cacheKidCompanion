package com.cachekid.companion.host.mission

data class MissionOfflineMapPreparationResult(
    val draft: MissionDraft,
    val hasOfflineMap: Boolean,
    val statusMessage: String,
)
