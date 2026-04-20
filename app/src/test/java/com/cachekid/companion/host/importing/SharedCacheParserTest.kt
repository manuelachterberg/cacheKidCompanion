package com.cachekid.companion.host.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCacheParserTest {

    private val parser = SharedCacheParser()

    @Test
    fun `parser extracts complete cache import from decimal coordinates`() {
        val result = parser.parse(
            sharedText = """
                Old Oak Cache
                GC12345
                52.520008, 13.404954
                A small cache in the park.
            """.trimIndent(),
            sourceApp = "geocaching",
        )

        assertEquals(SharedCacheImportStatus.SUCCESS, result.status)
        assertEquals("GC12345", result.value?.cacheCode)
        assertEquals("Old Oak Cache", result.value?.sourceTitle)
        assertEquals(52.520008, result.value?.target?.latitude ?: 0.0, 0.000001)
        assertEquals(13.404954, result.value?.target?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `parser extracts directional coordinates from geocaching style payload`() {
        val result = parser.parse(
            sharedText = """
                Pirate Bridge
                GC9TEST
                N 52° 31.201 E 013° 24.297
            """.trimIndent(),
        )

        assertEquals(SharedCacheImportStatus.SUCCESS, result.status)
        assertEquals("GC9TEST", result.value?.cacheCode)
        assertEquals("Pirate Bridge", result.value?.sourceTitle)
        assertNotNull(result.value?.target)
        assertTrue((result.value?.target?.latitude ?: 0.0) > 52.5)
        assertTrue((result.value?.target?.longitude ?: 0.0) > 13.4)
    }

    @Test
    fun `parser returns partial result when title and cache code exist without target`() {
        val result = parser.parse(
            sharedText = """
                Forest Treasure
                GC77777
            """.trimIndent(),
        )

        assertEquals(SharedCacheImportStatus.PARTIAL, result.status)
        assertEquals("GC77777", result.value?.cacheCode)
        assertEquals("Forest Treasure", result.value?.sourceTitle)
        assertNull(result.value?.target)
        assertTrue(result.messages.any { it.contains("Target coordinates") })
    }

    @Test
    fun `parser returns invalid for empty payload`() {
        val result = parser.parse("   ")

        assertEquals(SharedCacheImportStatus.INVALID, result.status)
        assertNull(result.value)
        assertTrue(result.messages.any { it.contains("empty") })
    }

    @Test
    fun `parser extracts cache code from coord info share link`() {
        val result = parser.parse("https://coord.info/GC7NXFT")

        assertEquals(SharedCacheImportStatus.PARTIAL, result.status)
        assertEquals("GC7NXFT", result.value?.cacheCode)
        assertTrue(result.messages.any { it.contains("Cache title") })
        assertTrue(result.messages.any { it.contains("Target coordinates") })
    }
}
