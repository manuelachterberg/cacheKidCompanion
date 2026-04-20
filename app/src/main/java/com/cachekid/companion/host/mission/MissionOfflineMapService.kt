package com.cachekid.companion.host.mission

class MissionOfflineMapService(
    private val composer: OfflineMissionMapComposer = OfflineMissionMapComposer(),
    private val dataSource: MissionMapDataSource = OverpassMissionMapDataSource(),
    private val renderer: MissionMapRenderer = OsmMissionMapSvgRenderer(),
) {

    fun prepareDraft(draft: MissionDraft): MissionDraft {
        val scaffoldedDraft = composer.prepareDraft(draft)
        val offlineMap = scaffoldedDraft.offlineMap ?: return scaffoldedDraft
        if (offlineMap.svgContent.isNotBlank()) {
            return scaffoldedDraft
        }

        val mapData = dataSource.fetch(offlineMap.bounds) ?: return scaffoldedDraft
        val svgContent = renderer.render(mapData, offlineMap.bounds).trim()
        if (svgContent.isBlank()) {
            return scaffoldedDraft
        }

        return scaffoldedDraft.copy(
            offlineMap = offlineMap.copy(svgContent = svgContent),
        )
    }
}
