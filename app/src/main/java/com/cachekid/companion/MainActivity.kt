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
import com.cachekid.companion.host.mission.MissionDraft
import com.cachekid.companion.host.resolution.CacheResolutionResult
import com.cachekid.companion.host.resolution.HostCacheResolver
import com.cachekid.companion.host.resolution.ManualCacheResolutionService
import com.cachekid.companion.web.NativeBridge

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nativeBridge: NativeBridge
    private lateinit var sensorRepository: HybridSensorRepository
    private val hostShareImportService = HostShareImportService()
    private val hostCacheResolver = HostCacheResolver()
    private val manualCacheResolutionService = ManualCacheResolutionService()
    private val missionDraftFactory = MissionDraftFactory()

    private var pendingImportResult: SharedCacheImportResult? = null
    private var pendingResolutionResult: CacheResolutionResult? = null
    private var pendingMissionDraft: MissionDraft? = null
    private var pendingShareDebug: String? = null

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

        handleShareIntent(intent)
        refreshNativeImportPanel()
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
        pendingMissionDraft = pendingResolutionResult?.value?.let { missionDraftFactory.createFrom(it) }
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
    }

    private fun refreshNativeImportPanel() {
        val summary = pendingImportSummary()
        val debug = pendingShareDebug
        if (summary == null && debug == null) {
            binding.nativeImportPanel.visibility = View.GONE
            return
        }

        binding.nativeImportPanel.visibility = View.VISIBLE
        binding.nativeImportTitle.text = when {
            pendingMissionDraft != null -> "Mission bereit"
            pendingResolutionResult?.status == com.cachekid.companion.host.resolution.CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION ->
                "Cache erkannt"
            else -> "Import"
        }
        binding.nativeImportStatus.text = summary ?: "Share-Intent empfangen."

        val needsManualResolution =
            pendingMissionDraft == null &&
                pendingResolutionResult?.status == com.cachekid.companion.host.resolution.CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION

        binding.nativeImportDebug.visibility = if (needsManualResolution) View.VISIBLE else View.GONE
        binding.nativeImportDebug.text = if (needsManualResolution) debug.orEmpty() else ""
        binding.nativeResolutionTitleInput.visibility = if (needsManualResolution) View.VISIBLE else View.GONE
        binding.nativeResolutionTargetInput.visibility = if (needsManualResolution) View.VISIBLE else View.GONE
        binding.nativeResolveButton.visibility = if (needsManualResolution) View.VISIBLE else View.GONE

        if (needsManualResolution && binding.nativeResolutionTitleInput.text.isNullOrBlank()) {
            binding.nativeResolutionTitleInput.setText(pendingResolutionResult?.cacheCodeHint.orEmpty())
        }
        if (needsManualResolution && binding.nativeResolutionTargetInput.text.isNullOrBlank()) {
            binding.nativeResolutionTargetInput.setText(DEFAULT_TARGET_TEXT)
        }
        if (!needsManualResolution) {
            binding.nativeResolutionTitleInput.text?.clear()
            binding.nativeResolutionTargetInput.text?.clear()
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

    private fun updatePendingMissionDraft(childTitle: String, summary: String): MissionDraft? {
        val currentDraft = pendingMissionDraft ?: return null
        pendingMissionDraft = currentDraft.copy(
            childTitle = childTitle.trim().ifBlank { currentDraft.childTitle },
            summary = summary.trim().ifBlank { currentDraft.summary },
        )
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
        pendingMissionDraft = missionDraftFactory.createFrom(resolvedCache)
        refreshNativeImportPanel()
        if (::nativeBridge.isInitialized) {
            nativeBridge.notifyImportUpdated()
        }
        return pendingMissionDraft
    }

    private companion object {
        const val DEFAULT_TARGET_TEXT = "52.520008,13.404954"
    }
}
