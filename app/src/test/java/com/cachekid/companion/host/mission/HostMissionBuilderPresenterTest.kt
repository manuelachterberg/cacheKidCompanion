package com.cachekid.companion.host.mission

import com.cachekid.companion.host.resolution.CacheResolutionResult
import com.cachekid.companion.host.resolution.CacheResolutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMissionBuilderPresenterTest {

    private val presenter = HostMissionBuilderPresenter()

    @Test
    fun `presenter shows manual resolution state when only cache code is known`() {
        val state = presenter.present(
            importSummary = "partial: needs_online_resolution: Cache erkannt.",
            shareDebug = "action=android.intent.action.SEND",
            resolutionResult = CacheResolutionResult(
                status = CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION,
                value = null,
                messages = listOf("Cache erkannt."),
                cacheCodeHint = "GC7NXFT",
            ),
            missionDraft = null,
            storedMissionResult = null,
            sendMissionResult = null,
            defaultTargetText = "",
        )

        assertTrue(state.isVisible)
        assertEquals("Cache erkannt", state.panelTitle)
        assertTrue(state.showManualResolution)
        assertFalse(state.showMissionBuilder)
        assertEquals("GC7NXFT", state.manualResolutionTitle)
        assertEquals("", state.manualResolutionTarget)
    }

    @Test
    fun `presenter shows builder state for ready mission`() {
        val state = presenter.present(
            importSummary = "draft-ready: Cache manuell vervollstaendigt.",
            shareDebug = "debug",
            resolutionResult = CacheResolutionResult(
                status = CacheResolutionStatus.RESOLVED,
                value = null,
                messages = listOf("Cache manuell vervollstaendigt."),
                cacheCodeHint = "GC7NXFT",
            ),
            missionDraft = MissionDraft(
                cacheCode = "GC7NXFT",
                sourceTitle = "Old Oak Cache",
                childTitle = "Der alte Baum",
                summary = "Folge der Karte bis zum grossen X.",
                target = MissionTarget(52.520008, 13.404954),
            ),
            storedMissionResult = MissionPackageStoreResult(
                missionDirectory = null,
                errors = emptyList(),
            ),
            sendMissionResult = null,
            defaultTargetText = "",
        )

        assertEquals("Mission vorbereiten", state.panelTitle)
        assertEquals("Der alte Baum", state.missionChildTitle)
        assertEquals("52.520008,13.404954", state.missionTargetText)
        assertTrue(state.showMissionBuilder)
        assertFalse(state.showManualResolution)
    }
}
