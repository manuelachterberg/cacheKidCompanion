package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Test

class OsmMissionMapSvgRendererTest {

    private val renderer = OsmMissionMapSvgRenderer()

    @Test
    fun `renderer converts overpass way geometry into svg paths`() {
        val svg = renderer.render(
            mapData = """
                {
                  "elements": [
                    {
                      "type": "way",
                      "tags": { "highway": "residential" },
                      "geometry": [
                        { "lat": 52.60, "lon": 13.30 },
                        { "lat": 52.55, "lon": 13.40 },
                        { "lat": 52.50, "lon": 13.50 }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            bounds = MissionMapBounds(
                minLatitude = 52.5,
                minLongitude = 13.3,
                maxLatitude = 52.6,
                maxLongitude = 13.5,
            ),
        )

        assertTrue(svg.contains("kid-map-osm-road"))
        assertTrue(svg.contains("""M 0 0"""))
        assertTrue(svg.contains("""L 50 70"""))
    }
}
