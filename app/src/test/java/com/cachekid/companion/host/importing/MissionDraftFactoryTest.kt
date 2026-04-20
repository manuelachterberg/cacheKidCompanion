package com.cachekid.companion.host.importing

import com.cachekid.companion.host.mission.MissionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MissionDraftFactoryTest {

    private val factory = MissionDraftFactory()

    @Test
    fun `factory creates mission draft from complete import`() {
        val draft = factory.createFrom(
            SharedCacheImport(
                rawText = "raw",
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                target = MissionTarget(52.520008, 13.404954),
                sourceApp = "geocaching",
            ),
        )

        assertNotNull(draft)
        assertEquals("GC12345", draft?.cacheCode)
        assertEquals("Old Oak Cache", draft?.childTitle)
        assertEquals("Folge der Karte bis zum grossen X.", draft?.summary)
    }

    @Test
    fun `factory returns null when required import data is missing`() {
        val draft = factory.createFrom(
            SharedCacheImport(
                rawText = "raw",
                cacheCode = null,
                sourceTitle = "Old Oak Cache",
                target = MissionTarget(52.520008, 13.404954),
            ),
        )

        assertNull(draft)
    }
}
