package com.cachekid.companion.host.mission

data class MissionPackageValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)
