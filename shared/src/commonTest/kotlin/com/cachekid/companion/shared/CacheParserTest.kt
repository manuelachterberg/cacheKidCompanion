package com.cachekid.companion.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CacheParserTest {
    
    @Test
    fun `parse extracts GC code from text`() {
        val text = "Check out GC12345, it's a great cache!"
        val mission = CacheParser.parse(text)
        
        assertNotNull(mission)
        assertEquals("GC12345", mission.cacheCode)
    }
    
    @Test
    fun `parse extracts coordinates from DMS format`() {
        val text = "N 52° 31.234 E 13° 24.567"
        val mission = CacheParser.parse(text)
        
        assertNotNull(mission)
        // DMS: 52° 31.234' = 52 + 31.234/60 = 52.520566
        assertEquals(52.520566, mission.target.latitude, 0.001)
        assertEquals(13.40945, mission.target.longitude, 0.001)
    }
    
    @Test
    fun `parse handles URL with GC code`() {
        val text = "https://coord.info/GC98765"
        val mission = CacheParser.parse(text)
        
        assertNotNull(mission)
        assertEquals("GC98765", mission.cacheCode)
    }
}
