package com.cachekid.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cachekid.companion.host.resolution.AndroidGeocachingSessionStore

class GeocachingLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var sessionStore: AndroidGeocachingSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geocaching_login)

        sessionStore = AndroidGeocachingSessionStore(applicationContext)
        statusView = findViewById(R.id.geocachingLoginStatus)
        webView = findViewById(R.id.geocachingLoginWebView)

        findViewById<Button>(R.id.geocachingLoginCancelButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        findViewById<Button>(R.id.geocachingLoginDoneButton).setOnClickListener {
            val cookieHeader = captureCookieHeader()
            val hasCookies = !cookieHeader.isNullOrBlank()
            if (hasCookies) {
                sessionStore.saveCookieHeader(cookieHeader)
            }
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(EXTRA_HAS_COOKIES, hasCookies)
                },
            )
            finish()
        }

        configureWebView(webView)
        updateStatus()
        webView.loadUrl(LOGIN_URL)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun configureWebView(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                captureCookieHeader()?.let { sessionStore.saveCookieHeader(it) }
                updateStatus(url)
            }
        }
    }

    private fun updateStatus(url: String? = null) {
        val hasCookies = !captureCookieHeader().isNullOrBlank()
        statusView.text = buildString {
            append(
                if (hasCookies) {
                    "Geocaching-Cookies erkannt. Wenn die Cache-Seite danach Koordinaten zeigt, kannst du den Login uebernehmen."
                } else {
                    "Bitte in Geocaching einloggen und danach unten bestaetigen."
                },
            )
            url?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
        }
    }

    private fun captureCookieHeader(): String? {
        val cookieManager = CookieManager.getInstance()
        val direct = cookieManager.getCookie("https://www.geocaching.com")
        val coordInfo = cookieManager.getCookie("https://coord.info")
        return listOfNotNull(direct, coordInfo)
            .flatMap { raw -> raw.split(';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        const val LOGIN_URL = "https://www.geocaching.com/account/signin?returnUrl=%2Fplay"
        const val EXTRA_HAS_COOKIES = "has_cookies"
    }
}
