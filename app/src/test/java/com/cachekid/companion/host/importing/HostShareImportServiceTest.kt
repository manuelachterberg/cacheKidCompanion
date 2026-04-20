package com.cachekid.companion.host.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostShareImportServiceTest {

    private val service = HostShareImportService()

    @Test
    fun `valid send intent with text payload is parsed`() {
        val result = service.importFrom(
            SharedTextPayload(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                text = """
                    Old Oak Cache
                    GC12345
                    52.520008, 13.404954
                """.trimIndent(),
                sourceApp = "geocaching",
            ),
        )

        assertEquals(SharedCacheImportStatus.SUCCESS, result.status)
        assertEquals("GC12345", result.value?.cacheCode)
    }

    @Test
    fun `non text payload is rejected`() {
        val result = service.importFrom(
            SharedTextPayload(
                action = "android.intent.action.SEND",
                mimeType = "image/png",
                text = null,
            ),
        )

        assertEquals(SharedCacheImportStatus.INVALID, result.status)
        assertTrue(result.messages.any { it.contains("Only text share payloads") })
    }

    @Test
    fun `unsupported action is rejected`() {
        val result = service.importFrom(
            SharedTextPayload(
                action = "android.intent.action.PICK",
                mimeType = "text/plain",
                text = "GC12345",
            ),
        )

        assertEquals(SharedCacheImportStatus.INVALID, result.status)
        assertTrue(result.messages.any { it.contains("Unsupported share action") })
    }

    @Test
    fun `view intent with coord info link is parsed as partial import`() {
        val result = service.importFrom(
            SharedTextPayload(
                action = "android.intent.action.VIEW",
                mimeType = "text/plain",
                text = "https://coord.info/GC7NXFT",
            ),
        )

        assertEquals(SharedCacheImportStatus.PARTIAL, result.status)
        assertEquals("GC7NXFT", result.value?.cacheCode)
    }
}
