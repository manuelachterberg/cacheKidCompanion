package com.cachekid.companion.host.mission

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionOfflineMapServiceTest {

    @Test
    fun `service prepares draft with rendered offline map`() {
        val service = MissionOfflineMapService(
            composer = OfflineMissionMapComposer(),
            dataSource = object : MissionMapDataSource {
                override fun fetch(bounds: MissionMapBounds): String {
                    return """
                        {
                          "elements": [
                            {
                              "type": "way",
                              "tags": { "highway": "path" },
                              "geometry": [
                                { "lat": 52.60, "lon": 13.30 },
                                { "lat": 52.55, "lon": 13.40 }
                              ]
                            }
                          ]
                        }
                    """.trimIndent()
                }
            },
            renderer = OsmMissionMapSvgRenderer(),
        )

        val preparedDraft = service.prepareDraft(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                childTitle = "Der Schatz im Wald",
                summary = "Folge der Karte bis zum grossen X.",
                target = MissionTarget(52.520008, 13.404954),
            ),
        )

        assertNotNull(preparedDraft.offlineMap)
        assertTrue(preparedDraft.offlineMap?.svgContent?.contains("kid-map-osm-road") == true)
    }

    @Test
    fun `service skips oversized offline map payloads`() {
        val hugePayload = buildString {
            append("""{"elements":[""")
            repeat(400_000) { append('x') }
            append("]}")
        }
        val service = MissionOfflineMapService(
            composer = OfflineMissionMapComposer(),
            dataSource = object : MissionMapDataSource {
                override fun fetch(bounds: MissionMapBounds): String = hugePayload
            },
            renderer = object : MissionMapRenderer {
                override fun render(mapData: String, bounds: MissionMapBounds): String {
                    return "<g></g>"
                }
            },
        )

        val result = service.prepareDraftWithStatus(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                childTitle = "Der Schatz im Wald",
                summary = "Folge der Karte bis zum grossen X.",
                target = MissionTarget(52.520008, 13.404954),
            ),
        )

        assertFalse(result.hasOfflineMap)
        assertTrue(result.statusMessage.contains("zu gross"))
        assertTrue(result.draft.offlineMap?.svgContent?.isBlank() == true)
    }

    @Test
    fun `service retries with smaller bounds when first map payload is too large`() {
        val service = MissionOfflineMapService(
            composer = OfflineMissionMapComposer(),
            dataSource = object : MissionMapDataSource {
                override fun fetch(bounds: MissionMapBounds): String {
                    val latitudeSpan = bounds.maxLatitude - bounds.minLatitude
                    return if (latitudeSpan > 0.01) {
                        buildString {
                            append("""{"elements":[""")
                            repeat(400_000) { append('x') }
                            append("]}")
                        }
                    } else {
                        """
                            {
                              "elements": [
                                {
                                  "type": "way",
                                  "tags": { "highway": "path" },
                                  "geometry": [
                                    { "lat": 52.60, "lon": 13.30 },
                                    { "lat": 52.55, "lon": 13.40 }
                                  ]
                                }
                              ]
                            }
                        """.trimIndent()
                    }
                }
            },
            renderer = OsmMissionMapSvgRenderer(),
            viewportPlanner = MissionMapViewportPlanner(),
        )

        val result = service.prepareDraftWithStatus(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                childTitle = "Der Schatz im Wald",
                summary = "Folge der Karte bis zum grossen X.",
                target = MissionTarget(52.520008, 13.404954),
            ),
        )

        assertTrue(result.hasOfflineMap)
        assertTrue(result.statusMessage.contains("reduziert"))
        assertTrue(result.draft.offlineMap?.svgContent?.contains("kid-map-osm-road") == true)
        assertEquals(true, result.draft.offlineMap?.bounds != null)
    }
}
