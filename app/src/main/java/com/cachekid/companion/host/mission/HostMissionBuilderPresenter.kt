package com.cachekid.companion.host.mission

import com.cachekid.companion.host.resolution.CacheResolutionResult
import com.cachekid.companion.host.resolution.CacheResolutionStatus

class HostMissionBuilderPresenter(
    private val missionTargetParser: MissionTargetParser = MissionTargetParser(),
) {

    fun present(
        importSummary: String?,
        shareDebug: String?,
        resolutionResult: CacheResolutionResult?,
        missionDraft: MissionDraft?,
        storedMissionResult: MissionPackageStoreResult?,
        sendMissionResult: MissionPackageSendResult?,
        defaultTargetText: String,
    ): HostMissionBuilderPanelState {
        val isVisible = importSummary != null || shareDebug != null
        if (!isVisible) {
            return HostMissionBuilderPanelState(isVisible = false)
        }

        val needsManualResolution =
            missionDraft == null &&
                resolutionResult?.status == CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION
        val hasDraftReadyMission = missionDraft != null

        val panelTitle = when {
            hasDraftReadyMission -> "Mission vorbereiten"
            needsManualResolution -> "Cache erkannt"
            else -> "Import"
        }

        val panelStatus = when {
            hasDraftReadyMission -> "Titel, Kurztext und Ziel fuer das Kind pruefen."
            else -> importSummary ?: "Share-Intent empfangen."
        }

        val builderHint = when {
            !hasDraftReadyMission -> null
            sendMissionResult?.isSuccess == true -> "Mission wurde erfolgreich an das Tablet gesendet."
            storedMissionResult?.isSuccess == true -> "Mission ist lokal gespeichert und bereit zum Senden."
            else -> "Danach lokal speichern oder direkt an das Tablet senden."
        }

        return HostMissionBuilderPanelState(
            isVisible = true,
            panelTitle = panelTitle,
            panelStatus = panelStatus,
            debugText = if (needsManualResolution) shareDebug else null,
            showManualResolution = needsManualResolution,
            manualResolutionTitle = resolutionResult?.cacheCodeHint.orEmpty(),
            manualResolutionTarget = defaultTargetText,
            showMissionBuilder = hasDraftReadyMission,
            missionCacheCode = missionDraft?.cacheCode.orEmpty(),
            missionSourceTitle = missionDraft?.sourceTitle.orEmpty(),
            missionChildTitle = missionDraft?.childTitle.orEmpty(),
            missionSummary = missionDraft?.summary.orEmpty(),
            missionTargetText = missionDraft?.let { missionTargetParser.format(it.target) }.orEmpty(),
            builderHint = builderHint,
            showStoredStatus = storedMissionResult?.isSuccess == true,
            storedStatus = storedMissionResult?.missionDirectory?.name?.let { missionId ->
                "Gespeichert in: $missionId"
            },
            showSendStatus = sendMissionResult != null,
            sendStatus = sendMissionResult?.message,
        )
    }
}

data class HostMissionBuilderPanelState(
    val isVisible: Boolean,
    val panelTitle: String = "",
    val panelStatus: String = "",
    val debugText: String? = null,
    val showManualResolution: Boolean = false,
    val manualResolutionTitle: String = "",
    val manualResolutionTarget: String = "",
    val showMissionBuilder: Boolean = false,
    val missionCacheCode: String = "",
    val missionSourceTitle: String = "",
    val missionChildTitle: String = "",
    val missionSummary: String = "",
    val missionTargetText: String = "",
    val builderHint: String? = null,
    val showStoredStatus: Boolean = false,
    val storedStatus: String? = null,
    val showSendStatus: Boolean = false,
    val sendStatus: String? = null,
)
