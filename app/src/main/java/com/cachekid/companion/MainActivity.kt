package com.cachekid.companion

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cachekid.companion.config.PermissionRequirements
import com.cachekid.companion.databinding.ActivityMainBinding
import com.cachekid.companion.data.HybridSensorRepository
import com.cachekid.companion.host.importing.HostShareImportService
import com.cachekid.companion.host.importing.MissionDraftFactory
import com.cachekid.companion.host.importing.SharedCacheImportResult
import com.cachekid.companion.host.importing.SharedTextPayload
import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.ActiveMissionRepository
import com.cachekid.companion.host.mission.MissionDraft
import com.cachekid.companion.host.mission.MissionPackageFileStore
import com.cachekid.companion.host.mission.MissionPackageReceiverServer
import com.cachekid.companion.host.mission.MissionPackageSendResult
import com.cachekid.companion.host.mission.MissionPackageSenderClient
import com.cachekid.companion.host.mission.MissionPackageStoreResult
import com.cachekid.companion.host.mission.MissionPackageWriter
import com.cachekid.companion.host.mission.MissionTargetParser
import com.cachekid.companion.host.mission.HostMissionBuilderPresenter
import com.cachekid.companion.host.mission.OfflineMissionMapComposer
import com.cachekid.companion.host.resolution.CacheResolutionResult
import com.cachekid.companion.host.resolution.HostCacheResolver
import com.cachekid.companion.host.resolution.ManualCacheResolutionService
import com.cachekid.companion.web.NativeBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nativeBridge: NativeBridge
    private lateinit var sensorRepository: HybridSensorRepository
    private val hostShareImportService = HostShareImportService()
    private val hostCacheResolver = HostCacheResolver()
    private val manualCacheResolutionService = ManualCacheResolutionService()
    private val missionDraftFactory = MissionDraftFactory()
    private val missionPackageWriter = MissionPackageWriter()
    private val missionPackageFileStore = MissionPackageFileStore()
    private val missionPackageSenderClient = MissionPackageSenderClient()
    private val missionTargetParser = MissionTargetParser()
    private val hostMissionBuilderPresenter = HostMissionBuilderPresenter(missionTargetParser)
    private val offlineMissionMapComposer = OfflineMissionMapComposer()
    private val activeMissionRepository = ActiveMissionRepository()

    private var pendingImportResult: SharedCacheImportResult? = null
    private var pendingResolutionResult: CacheResolutionResult? = null
    private var pendingMissionDraft: MissionDraft? = null
    private var activeMission: ActiveMission? = null
    private var pendingShareDebug: String? = null
    private var storedMissionResult: MissionPackageStoreResult? = null
    private var sendMissionResult: MissionPackageSendResult? = null
    private var receiveServer: MissionPackageReceiverServer? = null
    private var receiveServerRunning: Boolean = false
    private var receiveStatusText: String = "Empfang aus."

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        binding.webView.post {
            binding.webView.evaluateJavascript(
                "window.CacheKidNativeHost && window.CacheKidNativeHost.notifyPermissionsChanged();",
                null,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorRepository = HybridSensorRepository(applicationContext)
        nativeBridge = NativeBridge(
            webView = binding.webView,
            sensorRepository = sensorRepository,
            coroutineScope = lifecycleScope,
            requestPermissions = {
                permissionLauncher.launch(requiredPermissions())
            },
            hasLocationPermissions = ::hasLocationPermissions,
            getPendingImportSummary = ::pendingImportSummary,
            getPendingShareDebugAction = { pendingShareDebug },
            getPendingMissionDraft = { pendingMissionDraft },
            getActiveMission = { activeMission },
            getPendingResolution = { pendingResolutionResult },
            updatePendingMissionDraftAction = ::updatePendingMissionDraft,
            resolvePendingCacheManuallyAction = ::resolvePendingCacheManually,
        )

        binding.nativeResolveButton.setOnClickListener {
            val draft = resolvePendingCacheManually(
                title = binding.nativeResolutionTitleInput.text?.toString().orEmpty(),
                coordinateText = binding.nativeResolutionTargetInput.text?.toString().orEmpty(),
            )
            if (draft == null) {
                Toast.makeText(
                    this,
                    "Bitte Titel und Koordinate pruefen.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Mission erstellt.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        binding.nativeUpdateMissionButton.setOnClickListener {
            val draft = updatePendingMissionDraft(
                childTitle = binding.nativeMissionChildTitleInput.text?.toString().orEmpty(),
                summary = binding.nativeMissionSummaryInput.text?.toString().orEmpty(),
                targetText = binding.nativeMissionTargetInput.text?.toString().orEmpty(),
            )
            if (draft == null) {
                Toast.makeText(
                    this,
                    "Bitte Kindertitel, Kurztext und Ziel pruefen.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Missionsdaten aktualisiert.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        binding.nativeSaveMissionButton.setOnClickListener {
            val result = storePendingMissionDraft()
            if (result?.isSuccess == true) {
                Toast.makeText(
                    this,
                    "Mission lokal gespeichert.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    this,
                    result?.errors?.firstOrNull() ?: "Mission konnte nicht gespeichert werden.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        binding.nativeReceiveToggleButton.setOnClickListener {
            toggleReceiveMode()
        }
        binding.nativeSendMissionButton.setOnClickListener {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    sendPendingMissionDraft(
                        host = binding.nativeSendHostInput.text?.toString().orEmpty(),
                        portText = binding.nativeSendPortInput.text?.toString().orEmpty(),
                    )
                }
                refreshNativeImportPanel()
                Toast.makeText(
                    this@MainActivity,
                    result?.message ?: "Mission konnte nicht gesendet werden.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        activeMission = loadActiveMission()
        handleShareIntent(intent)
        refreshNativeImportPanel()
        refreshReceivePanel()
        configureWebView(binding.webView)
        binding.webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        refreshNativeImportPanel()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermissions()) {
            nativeBridge.startNativeSensors()
        }
    }

    override fun onPause() {
        nativeBridge.stopNativeSensors()
        super.onPause()
    }

    override fun onDestroy() {
        receiveServer?.stop()
        binding.webView.apply {
            removeJavascriptInterface("AndroidHost")
            stopLoading()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }

    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.addJavascriptInterface(nativeBridge, "AndroidHost")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, hasLocationPermissions(), false)
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return PermissionRequirements.requiredLocationPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        return PermissionRequirements.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray()
    }

    private fun handleShareIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (
            action != android.content.Intent.ACTION_SEND &&
            action != android.content.Intent.ACTION_VIEW
        ) {
            return
        }

        val sharedText = extractSharedText(intent)
        pendingShareDebug = buildString {
            append("action=")
            append(action)
            append(" | type=")
            append(intent.type ?: "--")
            append(" | text=")
            append(sharedText?.take(160) ?: "--")
        }

        Toast.makeText(
            this,
            "Import erkannt: ${sharedText?.take(48) ?: action}",
            Toast.LENGTH_SHORT,
        ).show()

        val payload = SharedTextPayload(
            action = action,
            mimeType = intent.type ?: sharedText?.let { "text/plain" },
            text = sharedText,
            sourceApp = referrer?.host,
        )

        pendingImportResult = hostShareImportService.importFrom(payload)
        pendingResolutionResult = pendingImportResult?.value?.let { hostCacheResolver.resolve(it) }
        pendingMissionDraft = pendingResolutionResult?.value
            ?.let { missionDraftFactory.createFrom(it) }
            ?.let { offlineMissionMapComposer.prepareDraft(it) }
        storedMissionResult = null
        sendMissionResult = null
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
    }

    private fun refreshNativeImportPanel() {
        val shouldHideForActiveMission =
            activeMission != null &&
                pendingImportResult == null &&
                pendingResolutionResult == null &&
                pendingMissionDraft == null &&
                pendingShareDebug == null
        if (shouldHideForActiveMission) {
            binding.nativeImportPanel.visibility = View.GONE
            return
        }

        val panelState = hostMissionBuilderPresenter.present(
            importSummary = pendingImportSummary(),
            shareDebug = pendingShareDebug,
            resolutionResult = pendingResolutionResult,
            missionDraft = pendingMissionDraft,
            storedMissionResult = storedMissionResult,
            sendMissionResult = sendMissionResult,
            defaultTargetText = DEFAULT_TARGET_TEXT,
        )
        if (!panelState.isVisible) {
            binding.nativeImportPanel.visibility = View.GONE
            return
        }

        binding.nativeImportPanel.visibility = View.VISIBLE
        binding.nativeImportTitle.text = panelState.panelTitle
        binding.nativeImportStatus.text = panelState.panelStatus

        binding.nativeImportDebug.visibility = if (panelState.debugText != null) View.VISIBLE else View.GONE
        binding.nativeImportDebug.text = panelState.debugText.orEmpty()

        binding.nativeResolutionTitleInput.visibility =
            if (panelState.showManualResolution) View.VISIBLE else View.GONE
        binding.nativeResolutionTargetInput.visibility =
            if (panelState.showManualResolution) View.VISIBLE else View.GONE
        binding.nativeResolveButton.visibility =
            if (panelState.showManualResolution) View.VISIBLE else View.GONE

        if (panelState.showManualResolution && binding.nativeResolutionTitleInput.text.isNullOrBlank()) {
            binding.nativeResolutionTitleInput.setText(panelState.manualResolutionTitle)
        }
        if (panelState.showManualResolution && binding.nativeResolutionTargetInput.text.isNullOrBlank()) {
            binding.nativeResolutionTargetInput.setText(panelState.manualResolutionTarget)
        }
        if (!panelState.showManualResolution) {
            binding.nativeResolutionTitleInput.text?.clear()
            binding.nativeResolutionTargetInput.text?.clear()
        }

        binding.nativeMissionReviewSection.visibility =
            if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeUpdateMissionButton.visibility =
            if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeMissionBuilderHint.visibility =
            if (panelState.showMissionBuilder && panelState.builderHint != null) View.VISIBLE else View.GONE
        binding.nativeMissionBuilderHint.text = panelState.builderHint.orEmpty()
        binding.nativeMissionCacheCode.text = panelState.missionCacheCode
        binding.nativeMissionSourceTitle.text = panelState.missionSourceTitle
        binding.nativeMissionChildTitleInput.setText(panelState.missionChildTitle)
        binding.nativeMissionSummaryInput.setText(panelState.missionSummary)
        binding.nativeMissionTargetInput.setText(panelState.missionTargetText)

        binding.nativeSaveMissionButton.visibility = if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeStoredMissionStatus.visibility =
            if (panelState.showStoredStatus) View.VISIBLE else View.GONE
        binding.nativeStoredMissionStatus.text = panelState.storedStatus.orEmpty()
        binding.nativeSendHostInput.visibility = if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeSendPortInput.visibility = if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeSendMissionButton.visibility = if (panelState.showMissionBuilder) View.VISIBLE else View.GONE
        binding.nativeSendMissionStatus.visibility =
            if (panelState.showSendStatus) View.VISIBLE else View.GONE
        binding.nativeSendMissionStatus.text = panelState.sendStatus.orEmpty()

        if (!panelState.showMissionBuilder) {
            storedMissionResult = null
            sendMissionResult = null
            binding.nativeStoredMissionStatus.text = ""
            binding.nativeSendMissionStatus.text = ""
        }
        if (panelState.showMissionBuilder && binding.nativeSendPortInput.text.isNullOrBlank()) {
            binding.nativeSendPortInput.setText(DEFAULT_RECEIVER_PORT.toString())
        }
        if (panelState.showMissionBuilder && binding.nativeSendHostInput.text.isNullOrBlank()) {
            binding.nativeSendHostInput.setText(DEFAULT_RECEIVER_HOST)
        }
    }

    private fun refreshReceivePanel() {
        val shouldHideForActiveMission =
            activeMission != null &&
                pendingImportResult == null &&
                pendingResolutionResult == null &&
                pendingMissionDraft == null &&
                pendingShareDebug == null
        if (shouldHideForActiveMission) {
            binding.nativeReceivePanel.visibility = View.GONE
            return
        }

        binding.nativeReceivePanel.visibility = View.VISIBLE
        binding.nativeReceiveStatus.text = receiveStatusText
        binding.nativeReceiveToggleButton.text = if (receiveServerRunning) {
            "Empfang stoppen"
        } else {
            "Empfang starten"
        }
    }

    private fun extractSharedText(intent: android.content.Intent): String? {
        val parts = linkedSetOf<String>()

        fun addPart(value: String?) {
            val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return
            parts.add(normalized)
        }

        addPart(intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
        addPart(intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT))
        addPart(intent.getStringExtra(android.content.Intent.EXTRA_HTML_TEXT))
        addPart(intent.dataString)

        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(index)
                addPart(item.text?.toString())
                addPart(item.htmlText)
                addPart(item.uri?.toString())
                addPart(item.coerceToText(this)?.toString())
            }
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun pendingImportSummary(): String? {
        val result = pendingImportResult ?: return null
        val resolution = pendingResolutionResult
        if (pendingMissionDraft != null) {
            val messageText = resolution?.messages?.joinToString(" ")
                ?.takeIf { it.isNotBlank() }
                ?: "Mission bereit."
            return "draft-ready: $messageText"
        }

        val draftState = resolution?.status?.name?.lowercase() ?: "draft-missing-data"
        val messages = buildList {
            addAll(result.messages)
            if (resolution != null) {
                addAll(resolution.messages)
            }
        }
        val messageText = if (messages.isEmpty()) {
            "Cache importiert."
        } else {
            messages.joinToString(" ")
        }
        return "${result.status.name.lowercase()}: $draftState: $messageText"
    }

    private fun updatePendingMissionDraft(
        childTitle: String,
        summary: String,
        targetText: String,
    ): MissionDraft? {
        val currentDraft = pendingMissionDraft ?: return null
        val target = missionTargetParser.parse(targetText) ?: return null
        pendingMissionDraft = offlineMissionMapComposer.prepareDraft(currentDraft.copy(
            childTitle = childTitle.trim().ifBlank { currentDraft.childTitle },
            summary = summary.trim().ifBlank { currentDraft.summary },
            target = target,
        ))
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
        return pendingMissionDraft
    }

    private fun resolvePendingCacheManually(title: String, coordinateText: String): MissionDraft? {
        val resolution = pendingResolutionResult ?: return null
        if (resolution.status != com.cachekid.companion.host.resolution.CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION) {
            return null
        }

        val resolvedCache = manualCacheResolutionService.resolve(
            cacheCode = resolution.cacheCodeHint,
            title = title,
            coordinateText = coordinateText,
            sourceApp = pendingImportResult?.value?.sourceApp,
        ) ?: return null

        pendingResolutionResult = resolution.copy(
            status = com.cachekid.companion.host.resolution.CacheResolutionStatus.RESOLVED,
            value = resolvedCache,
            messages = listOf("Cache manuell vervollstaendigt."),
        )
        pendingMissionDraft = offlineMissionMapComposer.prepareDraft(
            missionDraftFactory.createFrom(resolvedCache),
        )
        storedMissionResult = null
        sendMissionResult = null
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
        return pendingMissionDraft
    }

    private fun storePendingMissionDraft(): MissionPackageStoreResult? {
        val draft = pendingMissionDraft ?: return null
        val writeResult = missionPackageWriter.write(draft)
        if (!writeResult.isSuccess) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = writeResult.errors,
            )
        }

        val missionPackage = writeResult.missionPackage ?: return null
        val missionsDirectory = File(filesDir, MISSION_STORAGE_DIRECTORY)
        storedMissionResult = missionPackageFileStore.store(
            baseDirectory = missionsDirectory,
            missionPackage = missionPackage,
        )
        val result = storedMissionResult
        if (result?.isSuccess == true) {
            activeMission = result.missionDirectory?.let { missionDirectory ->
                activeMissionRepository.loadFromDirectory(missionDirectory)
            }
            pendingImportResult = null
            pendingResolutionResult = null
            pendingMissionDraft = null
            pendingShareDebug = null
            storedMissionResult = null
            sendMissionResult = null
            if (::nativeBridge.isInitialized) {
                nativeBridge.notifyActiveMissionUpdated()
                nativeBridge.notifyImportUpdated()
            }
        }
        refreshNativeImportPanel()
        return result
    }

    private fun sendPendingMissionDraft(host: String, portText: String): MissionPackageSendResult? {
        val draft = pendingMissionDraft ?: return null
        val writeResult = missionPackageWriter.write(draft)
        if (!writeResult.isSuccess) {
            sendMissionResult = MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = writeResult.errors.joinToString(" "),
            )
            return sendMissionResult
        }

        val missionPackage = writeResult.missionPackage ?: return null
        val port = portText.trim().toIntOrNull()
        sendMissionResult = missionPackageSenderClient.send(
            host = host,
            port = port ?: -1,
            missionPackage = missionPackage,
        )
        return sendMissionResult
    }

    private fun toggleReceiveMode() {
        if (receiveServerRunning) {
            receiveServer?.stop()
            receiveServer = null
            receiveServerRunning = false
            receiveStatusText = "Empfang aus."
            refreshReceivePanel()
            return
        }

        val server = MissionPackageReceiverServer(
            baseDirectory = File(filesDir, MISSION_STORAGE_DIRECTORY),
            onStatusChanged = { status ->
                runOnUiThread {
                    receiveStatusText = status
                    refreshReceivePanel()
                }
            },
            onMissionImported = { _ ->
                val latestMission = loadActiveMission()
                runOnUiThread {
                    activeMission = latestMission
                    if (::nativeBridge.isInitialized) {
                        nativeBridge.notifyActiveMissionUpdated()
                    }
                }
            },
        )
        val port = server.start()
        if (port == null) {
            receiveServer = null
            receiveServerRunning = false
            refreshReceivePanel()
            return
        }
        receiveServer = server
        receiveServerRunning = true
        receiveStatusText = "Empfang aktiv auf Port $port."
        refreshReceivePanel()
    }

    private fun loadActiveMission(): ActiveMission? {
        return activeMissionRepository.loadLatest(File(filesDir, MISSION_STORAGE_DIRECTORY))
    }

    private companion object {
        const val DEFAULT_TARGET_TEXT = "52.520008,13.404954"
        const val MISSION_STORAGE_DIRECTORY = "missions"
        const val DEFAULT_RECEIVER_HOST = "127.0.0.1"
        const val DEFAULT_RECEIVER_PORT = 8765
    }
}
