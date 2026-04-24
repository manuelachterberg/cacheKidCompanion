package com.cachekid.companion.host.mission

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class OfflineMissionMapComposerTest {

    private val composer = OfflineMissionMapComposer()

    @Test
    fun `composer attaches offline map bounds when draft has none`() {
        val draft = MissionDraft(
            cacheCode = "GC12345",
            sourceTitle = "Old Oak Cache",
            childTitle = "Der Schatz im Wald",
            summary = "Folge der Karte bis zum grossen X.",
            target = MissionTarget(52.520008, 13.404954),
        )

        val preparedDraft = composer.prepareDraft(draft)

        assertNotNull(preparedDraft.offlineMap)
        assertNotNull(preparedDraft.offlineMap?.bounds)
    }

    @Test
    fun `composer preserves existing offline map`() {
        val existingMap = MissionOfflineMap(
            svgContent = "<svg></svg>",
            bounds = MissionMapBounds(52.5, 13.3, 52.6, 13.5),
        )
        val draft = MissionDraft(
            cacheCode = "GC12345",
            sourceTitle = "Old Oak Cache",
            childTitle = "Der Schatz im Wald",
            summary = "Folge der Karte bis zum grossen X.",
            target = MissionTarget(52.520008, 13.404954),
            offlineMap = existingMap,
        )

        val preparedDraft = composer.prepareDraft(draft)

        assertSame(existingMap, preparedDraft.offlineMap)
    }
}
