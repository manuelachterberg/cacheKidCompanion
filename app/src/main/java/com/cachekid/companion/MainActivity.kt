package com.cachekid.companion

import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import android.widget.FrameLayout
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.cachekid.companion.host.mission.AssetManagerBaseMapAssetProvider
import com.cachekid.companion.host.mission.BundledOfflineBaseMapInstaller
import com.cachekid.companion.host.mission.DeviceOfflineBaseMapRepository
import com.cachekid.companion.host.mission.MissionDraft
import com.cachekid.companion.host.mission.MissionPackageFileStore
import com.cachekid.companion.host.mission.MissionPackageReceiverServer
import com.cachekid.companion.host.mission.MissionPackageSendResult
import com.cachekid.companion.host.mission.MissionPackageSenderClient
import com.cachekid.companion.host.mission.MissionPackageStoreResult
import com.cachekid.companion.host.mission.MissionPackageWriter
import com.cachekid.companion.host.mission.MissionTargetParser
import com.cachekid.companion.host.mission.HostMissionBuilderPresenter
import com.cachekid.companion.host.mission.MissionOfflineMapService
import com.cachekid.companion.host.mission.MissionTarget
import com.cachekid.companion.host.mission.OfflineMissionMapComposer
import com.cachekid.companion.host.mission.WalkingRouteWaypointService
import com.cachekid.companion.host.resolution.CacheResolutionResult
import com.cachekid.companion.host.resolution.AndroidGeocachingSessionStore
import com.cachekid.companion.host.resolution.AuthenticatedCoordInfoPageFetcher
import com.cachekid.companion.host.resolution.CoordInfoOnlineResolver
import com.cachekid.companion.host.resolution.GeocachingAuthenticatedWebViewPageFetcher
import com.cachekid.companion.host.resolution.HostCacheResolver
import com.cachekid.companion.host.resolution.ManualCacheResolutionService
import com.cachekid.companion.kid.KidNativeMapController
import com.cachekid.companion.web.NativeBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nativeBridge: NativeBridge
    private lateinit var sensorRepository: HybridSensorRepository
    private val hostShareImportService = HostShareImportService()
    private val hostCacheResolver = HostCacheResolver()
    private lateinit var geocachingSessionStore: AndroidGeocachingSessionStore
    private lateinit var coordInfoOnlineResolver: CoordInfoOnlineResolver
    private lateinit var geocachingWebViewPageFetcher: GeocachingAuthenticatedWebViewPageFetcher
    private val manualCacheResolutionService = ManualCacheResolutionService()
    private val missionDraftFactory = MissionDraftFactory()
    private val missionPackageWriter = MissionPackageWriter()
    private val missionPackageFileStore = MissionPackageFileStore()
    private val missionPackageSenderClient = MissionPackageSenderClient()
    private val missionTargetParser = MissionTargetParser()
    private val hostMissionBuilderPresenter = HostMissionBuilderPresenter(missionTargetParser)
    private val offlineMissionMapComposer = OfflineMissionMapComposer()
    private val missionOfflineMapService = MissionOfflineMapService(prototypeHostGenerationEnabled = false)
    private val activeMissionRepository = ActiveMissionRepository()
    private val walkingRouteWaypointService = WalkingRouteWaypointService()

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
    private var launchStartedAtMillis: Long = 0L
    private var launchSplashDismissed: Boolean = false
    private var importSessionId: Long = 0L
    private var latestLocation: Location? = null
    private lateinit var deviceOfflineBaseMapRepository: DeviceOfflineBaseMapRepository
    private lateinit var kidNativeMapController: KidNativeMapController

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

    private val geocachingLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        refreshNativeImportPanel()
        if (result.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }

        val hasCookies = result.data?.getBooleanExtra("has_cookies", false) == true
        if (!hasCookies) {
            Toast.makeText(
                this,
                "Kein Geocaching-Login gespeichert.",
                Toast.LENGTH_SHORT,
            ).show()
            return@registerForActivityResult
        }

        Toast.makeText(
            this,
            "Geocaching-Login gespeichert. Cache wird neu aufgeloest.",
            Toast.LENGTH_SHORT,
        ).show()
        maybeResolveOnlineInBackground(importSessionId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        launchStartedAtMillis = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.navigationBarColor = Color.BLACK
        geocachingSessionStore = AndroidGeocachingSessionStore(applicationContext)
        geocachingWebViewPageFetcher = GeocachingAuthenticatedWebViewPageFetcher(this)
        coordInfoOnlineResolver = CoordInfoOnlineResolver(
            pageFetcher = AuthenticatedCoordInfoPageFetcher(geocachingSessionStore),
        )
        BundledOfflineBaseMapInstaller(
            assetProvider = AssetManagerBaseMapAssetProvider(assets),
        ).installOrReplace(File(filesDir, OFFLINE_BASEMAP_DIRECTORY))
        deviceOfflineBaseMapRepository = DeviceOfflineBaseMapRepository(
            File(filesDir, OFFLINE_BASEMAP_DIRECTORY),
        )
        sensorRepository = HybridSensorRepository(applicationContext)
        kidNativeMapController = KidNativeMapController(
            context = this,
            mapContainer = binding.nativeKidMapContainer,
        )
        kidNativeMapController.onCreate(savedInstanceState)
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
        binding.nativeGeocachingLoginButton.setOnClickListener {
            geocachingLoginLauncher.launch(Intent(this, GeocachingLoginActivity::class.java))
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
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    storePendingMissionDraft()
                }
                refreshNativeImportPanel()
                refreshNativeMap()
                val cameraInfo = kidNativeMapController.getLastCameraDebugInfo()
                val message = when {
                    result == null -> "Mission konnte nicht gespeichert werden."
                    result.isSuccess -> {
                        cameraInfo?.let {
                            val target =
                                "Ziel ${"%.6f".format(it.missionTargetLatitude)}, ${"%.6f".format(it.missionTargetLongitude)}"
                            val camera =
                                if (it.usedFallback) {
                                    "Karte Fallback ${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}"
                                } else {
                                    "Karte ${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}"
                                }
                            "$target | $camera"
                        } ?: (result.infoMessage ?: "Mission lokal gespeichert.")
                    }
                    else -> listOfNotNull(
                        result.errors.firstOrNull(),
                        result.infoMessage,
                    ).joinToString(" ")
                }
                Toast.makeText(
                    this@MainActivity,
                    message,
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
        refreshNativeMap()
        configureWebView(binding.webView)
        binding.webView.loadUrl("file:///android_asset/web/index.html")
        observeNativeLocationForMap()
        observeNativeHeadingForMap()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        refreshNativeImportPanel()
    }

    override fun onResume() {
        super.onResume()
        kidNativeMapController.onResume()
        if (hasLocationPermissions()) {
            nativeBridge.startNativeSensors()
        }
    }

    override fun onPause() {
        nativeBridge.stopNativeSensors()
        kidNativeMapController.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        kidNativeMapController.onStart()
    }

    override fun onStop() {
        kidNativeMapController.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        kidNativeMapController.onLowMemory()
    }

    override fun onDestroy() {
        receiveServer?.stop()
        kidNativeMapController.onDestroy()
        binding.webView.apply {
            removeJavascriptInterface("AndroidHost")
            stopLoading()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }

    private fun configureWebView(webView: WebView) {
        webView.setBackgroundColor(Color.TRANSPARENT)
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
        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                dismissLaunchSplashWhenReady()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, hasLocationPermissions(), false)
            }
        }
    }

    private fun dismissLaunchSplashWhenReady() {
        if (launchSplashDismissed) {
            return
        }

        val elapsed = SystemClock.elapsedRealtime() - launchStartedAtMillis
        val remaining = (MIN_LAUNCH_SPLASH_DURATION_MS - elapsed).coerceAtLeast(0L)
        binding.launchSplashOverlay.postDelayed({
            if (launchSplashDismissed) {
                return@postDelayed
            }
            launchSplashDismissed = true
            binding.launchSplashOverlay.animate()
                .alpha(0f)
                .setDuration(LAUNCH_SPLASH_FADE_DURATION_MS)
                .withEndAction {
                    binding.launchSplashOverlay.visibility = View.GONE
                }
                .start()
        }, remaining)
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

        importSessionId += 1
        val currentImportSessionId = importSessionId
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
        Log.d(
            RESOLVER_LOG_TAG,
            "share-import raw=${sharedText?.take(200)} | import=${pendingImportResult?.status} | resolver=${pendingResolutionResult?.status}",
        )
        pendingMissionDraft = pendingResolutionResult?.value
            ?.let { missionDraftFactory.createFrom(it) }
            ?.let { offlineMissionMapComposer.prepareDraft(it) }
        storedMissionResult = null
        sendMissionResult = null
        binding.nativeResolutionTitleInput.text?.clear()
        binding.nativeResolutionTargetInput.text?.clear()
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
        maybeResolveOnlineInBackground(currentImportSessionId)
        refreshNativeMap()
    }

    private fun maybeResolveOnlineInBackground(sessionId: Long) {
        val import = pendingImportResult?.value ?: return
        val resolution = pendingResolutionResult ?: return
        if (resolution.status != com.cachekid.companion.host.resolution.CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION) {
            return
        }

        lifecycleScope.launch {
            val prefetchedPage = try {
                if (geocachingSessionStore.hasCookieHeader()) {
                    val coordInfoUrl = coordInfoOnlineResolver.buildCoordInfoUrl(import)
                    if (coordInfoUrl != null) {
                        geocachingWebViewPageFetcher.fetch(coordInfoUrl)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            val onlineResolution = withContext(Dispatchers.IO) {
                coordInfoOnlineResolver.resolve(import, prefetchedPage)
            } ?: return@launch
            Log.d(
                RESOLVER_LOG_TAG,
                "online-resolution status=${onlineResolution.status} | messages=${onlineResolution.messages.joinToString(" ")}",
            )
            onlineResolution.debugInfo?.let { debugInfo ->
                Log.d(RESOLVER_LOG_TAG, debugInfo)
            }
            withContext(Dispatchers.Main) {
                if (sessionId != importSessionId) {
                    return@withContext
                }
                pendingResolutionResult = onlineResolution
                pendingMissionDraft = onlineResolution.value
                    ?.let { missionDraftFactory.createFrom(it) }
                    ?.let { offlineMissionMapComposer.prepareDraft(it) }
                storedMissionResult = null
                sendMissionResult = null
                refreshNativeImportPanel()
                if (::nativeBridge.isInitialized) {
                    nativeBridge.notifyImportUpdated()
                }
                val resolvedTarget = onlineResolution.value?.target
                val resolvedMessage = when {
                    resolvedTarget != null -> {
                        "Cache online aufgeloest: ${resolvedTarget.latitude}, ${resolvedTarget.longitude}"
                    }
                    else -> onlineResolution.messages.joinToString(" ")
                }
                Toast.makeText(
                    this@MainActivity,
                    resolvedMessage,
                    Toast.LENGTH_LONG,
                ).show()
            }
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
            refreshNativeMap()
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
            refreshNativeMap()
            return
        }

        binding.nativeImportPanel.visibility = View.VISIBLE
        binding.nativeImportTitle.text = panelState.panelTitle
        binding.nativeImportStatus.text = panelState.panelStatus
        binding.nativeGeocachingLoginStatus.text =
            if (geocachingSessionStore.hasCookieHeader()) {
                "Geocaching-Login: Session gespeichert."
            } else {
                "Geocaching-Login: nicht verbunden."
            }
        binding.nativeGeocachingLoginButton.text =
            if (geocachingSessionStore.hasCookieHeader()) {
                "Geocaching-Login erneuern"
            } else {
                "Geocaching-Login verbinden"
            }

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
        refreshNativeMap()
    }

    private fun refreshReceivePanel() {
        if (activeMission != null) {
            binding.nativeReceivePanel.visibility = View.GONE
            refreshNativeMap()
            return
        }

        binding.nativeReceivePanel.visibility = View.VISIBLE
        binding.nativeReceiveStatus.text = receiveStatusText
        binding.nativeReceiveToggleButton.text = if (receiveServerRunning) {
            "Empfang stoppen"
        } else {
            "Empfang starten"
        }
        refreshNativeMap()
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
            routeOrigin = null,
            waypoints = emptyList(),
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
        val routeDraftResult = applyAutomaticRoute(draft)
        val offlineMapResult = missionOfflineMapService.prepareDraftWithStatus(routeDraftResult.draft)
        val writeResult = missionPackageWriter.write(offlineMapResult.draft)
        if (!writeResult.isSuccess) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = writeResult.errors,
                infoMessage = combineStatusMessages(
                    routeDraftResult.statusMessage,
                    offlineMapResult.statusMessage,
                ),
            )
        }

        val missionPackage = writeResult.missionPackage ?: return null
        val missionsDirectory = File(filesDir, MISSION_STORAGE_DIRECTORY)
        storedMissionResult = missionPackageFileStore.store(
            baseDirectory = missionsDirectory,
            missionPackage = missionPackage,
        ).copy(
            infoMessage = combineStatusMessages(
                routeDraftResult.statusMessage,
                offlineMapResult.statusMessage,
            ),
        )
        val result = storedMissionResult
        if (result?.isSuccess == true) {
            activeMission = result.missionDirectory?.let { missionDirectory ->
                activeMissionRepository.loadFromDirectory(missionDirectory)
            }?.let(::attachDeviceBaseMap)
            activeMission?.let { mission ->
                val liveLocationDebug = latestLocation?.let { location ->
                    "live=${"%.6f".format(location.latitude)},${"%.6f".format(location.longitude)}"
                } ?: "live=--"
                val routeOriginDebug = mission.routeOrigin?.let { origin ->
                    "routeOrigin=${"%.6f".format(origin.latitude)},${"%.6f".format(origin.longitude)}"
                } ?: "routeOrigin=--"
                val firstWaypointDebug = mission.waypoints.firstOrNull()?.let { waypoint ->
                    "firstWaypoint=${"%.6f".format(waypoint.latitude)},${"%.6f".format(waypoint.longitude)}"
                } ?: "firstWaypoint=--"
                Log.d(
                    RESOLVER_LOG_TAG,
                    "saved-mission-route $liveLocationDebug | $routeOriginDebug | $firstWaypointDebug | waypointCount=${mission.waypoints.size}",
                )
            }
            val baseMapMessage = describeBaseMapStatus(activeMission)
            storedMissionResult = result.copy(
                infoMessage = combineStatusMessages(
                    result.infoMessage,
                    baseMapMessage,
                ),
            )
            pendingImportResult = null
            pendingResolutionResult = null
            pendingMissionDraft = null
            pendingShareDebug = null
            sendMissionResult = null
            if (::nativeBridge.isInitialized) {
                nativeBridge.notifyActiveMissionUpdated()
                nativeBridge.notifyImportUpdated()
            }
        }
        return storedMissionResult ?: result
    }

    private fun applyAutomaticRoute(draft: MissionDraft): DraftRoutingResult {
        val origin = latestLocation
            ?.let { MissionTarget(latitude = it.latitude, longitude = it.longitude) }
            ?.takeIf { it.isValid() }
            ?: return DraftRoutingResult(
                draft = draft.copy(routeOrigin = null, waypoints = emptyList()),
                statusMessage = "Keine Host-Position fuer Navi-Route verfuegbar.",
            )

        val waypoints = runCatching {
            walkingRouteWaypointService.buildWaypoints(origin, draft.target)
        }.getOrDefault(emptyList())

        return if (waypoints.isNotEmpty()) {
            DraftRoutingResult(
                draft = draft.copy(routeOrigin = origin, waypoints = waypoints),
                statusMessage = "Navi-Route mit ${waypoints.size} Punkten erzeugt.",
            )
        } else {
            DraftRoutingResult(
                draft = draft.copy(routeOrigin = origin, waypoints = emptyList()),
                statusMessage = "Keine Navi-Route erzeugt.",
            )
        }
    }

    private fun combineStatusMessages(vararg messages: String?): String? {
        return messages
            .mapNotNull { message -> message?.trim()?.takeIf { it.isNotBlank() } }
            .distinct()
            .joinToString(" ")
            .ifBlank { null }
    }

    private fun sendPendingMissionDraft(host: String, portText: String): MissionPackageSendResult? {
        val draft = pendingMissionDraft ?: return null
        val routeDraftResult = applyAutomaticRoute(draft)
        val offlineMapResult = missionOfflineMapService.prepareDraftWithStatus(routeDraftResult.draft)
        val writeResult = missionPackageWriter.write(offlineMapResult.draft)
        if (!writeResult.isSuccess) {
            sendMissionResult = MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = combineStatusMessages(
                    writeResult.errors.joinToString(" "),
                    routeDraftResult.statusMessage,
                    offlineMapResult.statusMessage,
                ).orEmpty(),
            )
            return sendMissionResult
        }

        val missionPackage = writeResult.missionPackage ?: return null
        val port = portText.trim().toIntOrNull()
        sendMissionResult = missionPackageSenderClient.send(
            host = host,
            port = port ?: -1,
            missionPackage = missionPackage,
        ).let { result ->
            result.copy(
                message = combineStatusMessages(
                    result.message,
                    routeDraftResult.statusMessage,
                    offlineMapResult.statusMessage,
                ).orEmpty(),
            )
        }
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
                    refreshNativeMap()
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
            ?.let(::attachDeviceBaseMap)
    }

    private fun attachDeviceBaseMap(mission: ActiveMission): ActiveMission {
        return mission.copy(
            baseMap = deviceOfflineBaseMapRepository.loadFor(mission.target),
        )
    }

    private fun refreshNativeMap() {
        if (!::kidNativeMapController.isInitialized) {
            return
        }

        val shouldShowMap = activeMission != null
        updateWebViewForKidMode(shouldShowMap)
        binding.nativeKidMapContainer.visibility = if (shouldShowMap) View.VISIBLE else View.GONE
        val rootHeight = binding.root.height.toFloat().takeIf { it > 0f }
        val topInset = binding.webView.height.toFloat().takeIf { it > 0f }
        val bottomInset = rootHeight?.times(0.14f)
        if (topInset != null && bottomInset != null) {
            kidNativeMapController.updateViewportInsets(topInset, bottomInset)
        }
        if (!shouldShowMap) {
            kidNativeMapController.showMission(null, latestLocation)
            syncNativeMapOrientation()
            return
        }

        binding.root.post {
            kidNativeMapController.showMission(activeMission, latestLocation)
            syncNativeMapOrientation()
        }
    }

    private fun updateWebViewForKidMode(isKidMode: Boolean) {
        val layoutParams = (binding.webView.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )

        if (isKidMode) {
            binding.webView.visibility = View.VISIBLE
            binding.kidTopWhiteInset.visibility = View.VISIBLE
            binding.kidZoneDebugOverlay.visibility = View.GONE
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = (resources.displayMetrics.heightPixels * 0.37f).toInt()
            layoutParams.gravity = android.view.Gravity.TOP
            binding.webView.layoutParams = layoutParams
        } else {
            binding.webView.visibility = View.VISIBLE
            binding.kidTopWhiteInset.visibility = View.GONE
            binding.kidZoneDebugOverlay.visibility = View.GONE
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.gravity = android.view.Gravity.TOP
            binding.webView.layoutParams = layoutParams
        }
    }

    private fun updateKidZoneDebugOverlay() {
        val rootHeight = binding.root.height
        if (rootHeight <= 0) {
            return
        }

        val topPadding = binding.webView.height.toFloat()
        val bottomPadding = rootHeight * 0.14f
        val availableHeight = (rootHeight - topPadding - bottomPadding).coerceAtLeast(1f)
        val targetCenterY = topPadding + (availableHeight * 0.02f)
        val originCenterY = topPadding + (availableHeight * 0.96f)
        val halfBandHeight = resources.displayMetrics.density * 18f

        binding.kidTargetZoneMarker.translationY = targetCenterY - halfBandHeight
        binding.kidOriginZoneMarker.translationY = originCenterY - halfBandHeight
    }

    private fun observeNativeLocationForMap() {
        lifecycleScope.launch {
            sensorRepository.locationUpdates.collectLatest { location ->
                latestLocation = location
                if (activeMission != null) {
                    kidNativeMapController.updateLocation(location)
                    syncNativeMapOrientation()
                }
            }
        }
    }

    private fun observeNativeHeadingForMap() {
        lifecycleScope.launch {
            sensorRepository.headingDegrees.collectLatest { heading ->
                if (activeMission != null) {
                    kidNativeMapController.updateHeading(heading)
                    syncNativeMapOrientation()
                }
            }
        }
    }

    private fun syncNativeMapOrientation() {
        if (!::nativeBridge.isInitialized) {
            return
        }
        nativeBridge.notifyMapOrientation(
            mapBearingDegrees = kidNativeMapController.getCurrentMapBearingDegrees(),
            targetBearingDegrees = kidNativeMapController.getCurrentTargetBearingDegrees(),
        )
    }

    private data class DraftRoutingResult(
        val draft: MissionDraft,
        val statusMessage: String?,
    )

    override fun onSaveInstanceState(outState: Bundle) {
        kidNativeMapController.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun describeBaseMapStatus(mission: ActiveMission?): String {
        val currentMission = mission ?: return "Lokale Basemap: keine aktive Mission."
        val baseMap = currentMission.baseMap
        return if (baseMap != null) {
            "Lokale Basemap gefunden."
        } else {
            "Keine lokale Basemap gefunden."
        }
    }

    private companion object {
        const val RESOLVER_LOG_TAG = "CacheKidResolver"
        const val DEFAULT_TARGET_TEXT = ""
        const val MISSION_STORAGE_DIRECTORY = "missions"
        const val OFFLINE_BASEMAP_DIRECTORY = "offline-basemaps"
        const val DEFAULT_RECEIVER_HOST = "127.0.0.1"
        const val DEFAULT_RECEIVER_PORT = 8765
        const val MIN_LAUNCH_SPLASH_DURATION_MS = 900L
        const val LAUNCH_SPLASH_FADE_DURATION_MS = 220L
    }
}
