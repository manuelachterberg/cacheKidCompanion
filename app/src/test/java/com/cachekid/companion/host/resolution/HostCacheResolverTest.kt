package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.importing.SharedCacheImport
import com.cachekid.companion.host.mission.MissionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCacheResolverTest {

    private val resolver = HostCacheResolver()

    @Test
    fun `complete import resolves immediately`() {
        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "raw",
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                target = MissionTarget(52.520008, 13.404954),
                sourceApp = "geocaching",
            ),
        )

        assertEquals(CacheResolutionStatus.RESOLVED, result.status)
        assertNotNull(result.value)
        assertEquals("GC12345", result.value?.cacheCode)
    }

    @Test
    fun `coord info link requires smartphone online resolution`() {
        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "https://coord.info/GC7NXFT",
                cacheCode = "GC7NXFT",
                sourceTitle = null,
                target = null,
                sourceApp = "geocaching",
            ),
        )

        assertEquals(CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION, result.status)
        assertNull(result.value)
        assertTrue(result.messages.any { it.contains("Online-Aufloesung") })
    }

    @Test
    fun `unstructured payload stays unresolved`() {
        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "hello world",
                cacheCode = null,
                sourceTitle = null,
                target = null,
            ),
        )

        assertEquals(CacheResolutionStatus.UNRESOLVED, result.status)
        assertNull(result.value)
    }
}
