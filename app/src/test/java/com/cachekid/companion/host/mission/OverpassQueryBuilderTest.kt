package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassQueryBuilderTest {

    private val builder = OverpassQueryBuilder()

    @Test
    fun `builder creates encoded overpass query with bbox and tags`() {
        val query = builder.build(
            MissionMapBounds(
                minLatitude = 52.5,
                minLongitude = 13.3,
                maxLatitude = 52.6,
                maxLongitude = 13.5,
            ),
        )

        assertTrue(query.contains("highway"))
        assertTrue(query.contains("waterway"))
        assertTrue(query.contains("52.5%2C13.3%2C52.6%2C13.5"))
    }
}
