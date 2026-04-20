package com.cachekid.companion.host.mission

data class MissionDraftValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)
