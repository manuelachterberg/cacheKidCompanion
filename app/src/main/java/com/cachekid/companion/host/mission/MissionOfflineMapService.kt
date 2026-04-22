package com.cachekid.companion.host.mission

class MissionOfflineMapService(
    private val composer: OfflineMissionMapComposer = OfflineMissionMapComposer(),
    private val dataSource: MissionMapDataSource = OverpassMissionMapDataSource(),
    private val renderer: MissionMapRenderer = OsmMissionMapSvgRenderer(),
    private val viewportPlanner: MissionMapViewportPlanner = MissionMapViewportPlanner(),
    private val prototypeHostGenerationEnabled: Boolean = true,
) {

    fun prepareDraft(draft: MissionDraft): MissionDraft {
        return prepareDraftWithStatus(draft).draft
    }

    fun prepareDraftWithStatus(draft: MissionDraft): MissionOfflineMapPreparationResult {
        val scaffoldedDraft = composer.prepareDraft(draft)
        val offlineMap = scaffoldedDraft.offlineMap ?: return MissionOfflineMapPreparationResult(
            draft = scaffoldedDraft,
            hasOfflineMap = false,
            statusMessage = "Keine Offline-Karte vorbereitet.",
        )
        if (offlineMap.svgContent.isNotBlank()) {
            return MissionOfflineMapPreparationResult(
                draft = scaffoldedDraft,
                hasOfflineMap = true,
                statusMessage = "Offline-Karte bereits vorhanden.",
            )
        }
        if (!prototypeHostGenerationEnabled) {
            return MissionOfflineMapPreparationResult(
                draft = scaffoldedDraft,
                hasOfflineMap = false,
                statusMessage = "Host-Kartengenerierung deaktiviert. Lokale Offline-Basemap auf dem Meebook erwartet.",
            )
        }

        var lastTooLargeReason: String? = null

        for (scaleFactor in MAP_SCALE_FACTORS) {
            val candidateBounds = if (scaleFactor == 1.0) {
                offlineMap.bounds
            } else {
                viewportPlanner.scaleBounds(offlineMap.bounds, scaleFactor)
            }
            val mapData = dataSource.fetch(candidateBounds) ?: continue
            if (mapData.length > MAX_MAP_DATA_CHARS) {
                lastTooLargeReason = "Offline-Karte war zu gross fuer das Geraet."
                continue
            }

            val svgContent = renderer.render(mapData, candidateBounds).trim()
            if (svgContent.isBlank()) {
                continue
            }
            if (svgContent.length > MAX_SVG_CONTENT_CHARS) {
                lastTooLargeReason = "Offline-Karte war zu detailliert fuer das Geraet."
                continue
            }

            val statusMessage = if (scaleFactor == 1.0) {
                "Offline-Karte erzeugt."
            } else {
                "Offline-Karte erzeugt, mit reduziertem Ausschnitt."
            }

            return MissionOfflineMapPreparationResult(
                draft = scaffoldedDraft.copy(
                    offlineMap = offlineMap.copy(
                        svgContent = svgContent,
                        bounds = candidateBounds,
                    ),
                ),
                hasOfflineMap = true,
                statusMessage = statusMessage,
            )
        }

        return MissionOfflineMapPreparationResult(
            draft = scaffoldedDraft,
            hasOfflineMap = false,
            statusMessage = lastTooLargeReason ?: "Offline-Karte konnte nicht geladen werden.",
        )
    }

    private companion object {
        const val MAX_MAP_DATA_CHARS = 350_000
        const val MAX_SVG_CONTENT_CHARS = 120_000
        val MAP_SCALE_FACTORS = listOf(1.0, 0.7, 0.5, 0.35, 0.22, 0.14, 0.09)
    }
}
