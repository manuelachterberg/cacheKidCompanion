package com.cachekid.companion.host.mission

class MissionDraftValidator {

    fun validate(draft: MissionDraft): MissionDraftValidationResult {
        val errors = buildList {
            if (draft.cacheCode.isBlank()) {
                add("Cache code must not be blank.")
            }
            if (draft.sourceTitle.isBlank()) {
                add("Source title must not be blank.")
            }
            if (draft.childTitle.isBlank()) {
                add("Child title must not be blank.")
            }
            if (draft.summary.isBlank()) {
                add("Summary must not be blank.")
            }
            if (!draft.target.isValid()) {
                add("Target coordinates must be within valid latitude and longitude ranges.")
            }
            if (draft.waypoints.any { !it.isValid() }) {
                add("Waypoint coordinates must be within valid latitude and longitude ranges.")
            }
        }

        return MissionDraftValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }
}
