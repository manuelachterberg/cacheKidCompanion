package com.cachekid.companion.host.mission

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
