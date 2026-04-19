package com.cachekid.companion.web

import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.cachekid.companion.data.HybridSensorRepository
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
    fun startNativeSensors() {
        if (headingJob == null) {
            headingJob = coroutineScope.launch {
                sensorRepository.headingDegrees.collectLatest { heading ->
                    emitEvent(
                        type = "heading",
                        payload = JSONObject().apply {
                            put("headingDegrees", heading.toDouble())
                            put("source", "android-rotation-vector")
                        },
                    )
                }
            }
        }

        if (locationJob == null) {
            locationJob = coroutineScope.launch {
                sensorRepository.locationUpdates.collectLatest { location ->
                    location?.let { emitLocationEvent(it) }
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun emitLocationEvent(location: Location) {
        emitEvent(
            type = "location",
            payload = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracyMeters", location.accuracy.toDouble())
                put("source", location.provider ?: "android")
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
}
