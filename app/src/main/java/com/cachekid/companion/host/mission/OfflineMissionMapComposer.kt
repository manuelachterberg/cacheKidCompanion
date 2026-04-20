package com.cachekid.companion.host.mission

class OfflineMissionMapComposer(
    private val viewportPlanner: MissionMapViewportPlanner = MissionMapViewportPlanner(),
) {

    fun prepareDraft(draft: MissionDraft): MissionDraft {
        if (draft.offlineMap != null) {
            return draft
        }

        return draft.copy(
            offlineMap = MissionOfflineMap(
                svgContent = "",
                bounds = viewportPlanner.createBounds(draft.target),
            ),
        )
    }
}
