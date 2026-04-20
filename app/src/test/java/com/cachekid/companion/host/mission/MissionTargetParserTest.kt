package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MissionTargetParserTest {

    private val parser = MissionTargetParser()

    @Test
    fun `parser reads decimal coordinates`() {
        val target = parser.parse("52.520008, 13.404954")

        assertNotNull(target)
        assertEquals(52.520008, target?.latitude ?: 0.0, 0.000001)
        assertEquals(13.404954, target?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `parser rejects invalid coordinate text`() {
        assertNull(parser.parse("not-a-coordinate"))
        assertNull(parser.parse("95.0,13.4"))
    }
}
