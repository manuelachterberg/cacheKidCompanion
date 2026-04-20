package com.cachekid.companion.host.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualCacheResolutionServiceTest {

    private val service = ManualCacheResolutionService()

    @Test
    fun `manual resolution builds resolved cache details from valid inputs`() {
        val result = service.resolve(
            cacheCode = "GC7NXFT",
            title = "Old Oak Cache",
            coordinateText = "52.520008, 13.404954",
            sourceApp = "geocaching",
        )

        requireNotNull(result)
        assertEquals("GC7NXFT", result.cacheCode)
        assertEquals("Old Oak Cache", result.title)
        assertEquals(52.520008, result.target.latitude, 0.0)
        assertEquals(13.404954, result.target.longitude, 0.0)
    }

    @Test
    fun `manual resolution rejects blank title`() {
        val result = service.resolve(
            cacheCode = "GC7NXFT",
            title = "   ",
            coordinateText = "52.520008, 13.404954",
        )

        assertNull(result)
    }

    @Test
    fun `manual resolution rejects invalid coordinates`() {
        val result = service.resolve(
            cacheCode = "GC7NXFT",
            title = "Old Oak Cache",
            coordinateText = "not-a-coordinate",
        )

        assertNull(result)
    }
}
