package com.cachekid.companion

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.cachekid.companion.web.NativeBridge

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nativeBridge: NativeBridge
    private lateinit var sensorRepository: HybridSensorRepository

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
        )

        configureWebView(binding.webView)
        binding.webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        nativeBridge.startNativeSensors()
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
}
