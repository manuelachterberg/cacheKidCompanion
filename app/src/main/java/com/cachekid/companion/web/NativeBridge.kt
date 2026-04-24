package com.cachekid.companion.web

import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.MissionDraft
import com.cachekid.companion.data.HybridSensorRepository
import com.cachekid.companion.host.resolution.CacheResolutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class NativeBridge(
    private val webView: WebView,
    private val sensorRepository: HybridSensorRepository,
    private val coroutineScope: CoroutineScope,
    private val requestPermissions: () -> Unit,
    private val hasLocationPermissions: () -> Boolean,
    private val getPendingImportSummary: () -> String?,
    private val getPendingShareDebugAction: () -> String?,
    private val getPendingMissionDraft: () -> MissionDraft?,
    private val getActiveMission: () -> ActiveMission?,
    private val getPendingResolution: () -> CacheResolutionResult?,
    private val updatePendingMissionDraftAction: (String, String, String) -> MissionDraft?,
    private val resolvePendingCacheManuallyAction: (String, String) -> MissionDraft?,
) {

    private var headingJob: Job? = null
    private var locationJob: Job? = null

    @JavascriptInterface
    fun getDeviceProfile(): String {
        return JSONObject().apply {
            put("platform", "android")
            put("apiLevel", Build.VERSION.SDK_INT)
            put("bluetoothSupported", sensorRepository.isBluetoothSupported())
            put("webViewHost", true)
        }.toString()
    }

    @JavascriptInterface
    fun requestNativePermissions() {
        requestPermissions()
    }

    @JavascriptInterface
    fun connectBleSensor() {
        emitEvent(
            type = "ble-status",
            payload = JSONObject().apply {
                put(
                    "status",
                    if (sensorRepository.isBluetoothSupported()) {
                        "BLE verfuegbar. Trage jetzt echte UUIDs und GATT-Logik im Android-Host ein."
                    } else {
                        "BLE wird auf diesem Geraet nicht unterstuetzt."
                    },
                )
            },
        )
    }

    @JavascriptInterface
    fun getPendingImportStatus(): String? = getPendingImportSummary()

    @JavascriptInterface
    fun getPendingShareDebug(): String? = getPendingShareDebugAction()

    @JavascriptInterface
    fun getPendingMissionDraftJson(): String? {
        val draft = getPendingMissionDraft() ?: return null
        return draft.toJson()
    }

    @JavascriptInterface
    fun getActiveMissionJson(): String? {
        val mission = getActiveMission() ?: return null
        return mission.toJson()
    }

    @JavascriptInterface
    fun getPendingResolutionJson(): String? {
        val resolution = getPendingResolution() ?: return null
        return JSONObject().apply {
            put("status", resolution.status.name)
            put("cacheCodeHint", resolution.cacheCodeHint)
            put("messages", resolution.messages.joinToString(" "))
        }.toString()
    }

    @JavascriptInterface
    fun updatePendingMissionDraft(childTitle: String, summary: String, targetText: String): String? {
        val draft = updatePendingMissionDraftAction(childTitle, summary, targetText) ?: return null
        return draft.toJson()
    }

    @JavascriptInterface
    fun resolvePendingCacheManually(title: String, coordinateText: String): String? {
        val draft = resolvePendingCacheManuallyAction(title, coordinateText) ?: return null
        return draft.toJson()
    }

    @JavascriptInterface
    fun startNativeSensors() {
        if (!hasLocationPermissions()) {
            emitEvent(
                type = "native-status",
                payload = JSONObject().apply {
                    put("status", "Standortberechtigung fehlt. Onboard-GPS ist aus, externe Navigation kann weiter genutzt werden.")
                },
            )
        }

        if (headingJob == null) {
            headingJob = coroutineScope.launch {
                sensorRepository.headingReadings.collectLatest { reading ->
                    if (reading != null) {
                        emitEvent(
                            type = "heading",
                            payload = JSONObject().apply {
                                put("headingDegrees", reading.headingDegrees.toDouble())
                                put("source", reading.source.name.lowercase())
                            },
                        )
                    }
                }
            }
        }

        if (locationJob == null) {
            locationJob = coroutineScope.launch {
                sensorRepository.locationReadings.collectLatest { reading ->
                    reading?.let { emitLocationEvent(it.location, it.source.name.lowercase()) }
                }
            }
        }
    }

    fun stopNativeSensors() {
        headingJob?.cancel()
        headingJob = null
        locationJob?.cancel()
        locationJob = null
    }

    fun notifyImportUpdated() {
        emitEvent(
            type = "import-updated",
            payload = JSONObject().apply {
                put("status", getPendingImportSummary())
                put("shareDebug", getPendingShareDebugAction())
                put("missionDraft", getPendingMissionDraft()?.let { JSONObject(it.toJson()) })
                put(
                    "resolution",
                    getPendingResolution()?.let { resolution ->
                        JSONObject().apply {
                            put("status", resolution.status.name)
                            put("cacheCodeHint", resolution.cacheCodeHint)
                            put("messages", resolution.messages.joinToString(" "))
                        }
                    },
                )
            },
        )
    }

    fun notifyActiveMissionUpdated() {
        emitEvent(
            type = "active-mission-updated",
            payload = JSONObject().apply {
                put("activeMission", getActiveMission()?.let { JSONObject(it.toJson()) })
            },
        )
    }

    fun notifyMapOrientation(mapBearingDegrees: Double?, targetBearingDegrees: Double?) {
        emitEvent(
            type = "map-orientation",
            payload = JSONObject().apply {
                put("mapBearingDegrees", mapBearingDegrees)
                put("targetBearingDegrees", targetBearingDegrees)
            },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun emitLocationEvent(location: Location, source: String) {
        emitEvent(
            type = "location",
            payload = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracyMeters", location.accuracy.toDouble())
                put("source", source)
            },
        )
    }

    private fun emitEvent(type: String, payload: JSONObject) {
        val envelope = JSONObject().apply {
            put("type", type)
            put("payload", payload)
        }
        val escapedPayload = JSONObject.quote(envelope.toString())
        webView.post {
            webView.evaluateJavascript(
                "window.CacheKidNativeHost && window.CacheKidNativeHost.dispatch($escapedPayload);",
                null,
            )
        }
    }

    private fun MissionDraft.toJson(): String {
        return JSONObject().apply {
            put("cacheCode", cacheCode)
            put("sourceTitle", sourceTitle)
            put("childTitle", childTitle)
            put("summary", summary)
            put(
                "target",
                JSONObject().apply {
                    put("latitude", target.latitude)
                    put("longitude", target.longitude)
                },
            )
            put(
                "routeOrigin",
                routeOrigin?.let {
                    JSONObject().apply {
                        put("latitude", it.latitude)
                        put("longitude", it.longitude)
                    }
                },
            )
            put("sourceApp", sourceApp)
        }.toString()
    }

    private fun ActiveMission.toJson(): String {
        return JSONObject().apply {
            put("missionId", missionId)
            put("cacheCode", cacheCode)
            put("sourceTitle", sourceTitle)
            put("childTitle", childTitle)
            put("summary", summary)
            put(
                "target",
                JSONObject().apply {
                    put("latitude", target.latitude)
                    put("longitude", target.longitude)
                },
            )
            put(
                "routeOrigin",
                routeOrigin?.let {
                    JSONObject().apply {
                        put("latitude", it.latitude)
                        put("longitude", it.longitude)
                    }
                },
            )
            put("sourceApp", sourceApp)
            put(
                "offlineMap",
                offlineMap?.let { map ->
                    JSONObject().apply {
                        put("assetPath", map.assetPath)
                        put("svgContent", map.svgContent)
                        put(
                            "bounds",
                            JSONObject().apply {
                                put("minLatitude", map.bounds.minLatitude)
                                put("minLongitude", map.bounds.minLongitude)
                                put("maxLatitude", map.bounds.maxLatitude)
                                put("maxLongitude", map.bounds.maxLongitude)
                            },
                        )
                    }
                },
            )
            put(
                "baseMap",
                baseMap?.let { map ->
                    JSONObject().apply {
                        put("assetPath", map.assetPath)
                        put("svgContent", map.svgContent)
                        put(
                            "bounds",
                            JSONObject().apply {
                                put("minLatitude", map.bounds.minLatitude)
                                put("minLongitude", map.bounds.minLongitude)
                                put("maxLatitude", map.bounds.maxLatitude)
                                put("maxLongitude", map.bounds.maxLongitude)
                            },
                        )
                    }
                },
            )
        }.toString()
    }
}
