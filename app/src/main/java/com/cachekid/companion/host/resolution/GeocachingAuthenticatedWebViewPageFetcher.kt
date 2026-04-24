package com.cachekid.companion.host.resolution

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.cachekid.companion.host.mission.MissionTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

class GeocachingAuthenticatedWebViewPageFetcher(
    private val activity: AppCompatActivity,
) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(url: String): CoordInfoPage? = suspendCancellableCoroutine { continuation ->
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val webView = WebView(activity)
        webView.layoutParams = FrameLayout.LayoutParams(1, 1)
        webView.visibility = View.INVISIBLE

        fun cleanup() {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }

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
            private var completed = false

            override fun onPageFinished(view: WebView?, currentUrl: String?) {
                super.onPageFinished(view, currentUrl)
                if (completed) {
                    return
                }
                pollRenderedPage(
                    view = view ?: return,
                    currentUrl = currentUrl,
                    attemptsLeft = MAX_POLL_ATTEMPTS,
                    onComplete = { page ->
                        completed = true
                        if (!continuation.isCompleted) {
                            continuation.resume(page)
                        }
                        cleanup()
                    },
                )
            }
        }

        container.addView(webView)
        continuation.invokeOnCancellation { cleanup() }
        try {
            webView.loadUrl(url)
        } catch (error: Exception) {
            cleanup()
            if (!continuation.isCompleted) {
                continuation.resumeWithException(error)
            }
        }
    }

    private fun decodeJavascriptString(raw: String?): String {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return ""
        return try {
            JSONObject("{\"value\":$value}").getString("value")
        } catch (_: Exception) {
            value
        }
    }

    private fun pollRenderedPage(
        view: WebView,
        currentUrl: String?,
        attemptsLeft: Int,
        onComplete: (CoordInfoPage) -> Unit,
    ) {
        view.evaluateJavascript(
            """
            (function() {
              return JSON.stringify({
                html: document.documentElement ? document.documentElement.outerHTML : "",
                text: document.body ? document.body.innerText : "",
                url: window.location.href || ""
              });
            })();
            """.trimIndent(),
        ) { rawPayload ->
            val payload = decodeJavascriptString(rawPayload)
            val parsed = try {
                JSONObject(payload)
            } catch (_: Exception) {
                null
            }
            val html = parsed?.optString("html").orEmpty()
            val text = parsed?.optString("text").orEmpty()
            val resolvedUrl = parsed?.optString("url").takeUnless { it.isNullOrBlank() } ?: currentUrl.orEmpty()
            val combined = buildString {
                append(html)
                if (text.isNotBlank()) {
                    append("\n<!-- rendered-text -->\n")
                    append(text)
                }
            }

            if (attemptsLeft <= 1 || looksResolvedEnough(combined)) {
                onComplete(
                    CoordInfoPage(
                        html = combined.ifBlank { html },
                        source = CoordInfoPageSource.AUTHENTICATED,
                    ),
                )
                return@evaluateJavascript
            }

            view.postDelayed(
                {
                    pollRenderedPage(
                        view = view,
                        currentUrl = resolvedUrl,
                        attemptsLeft = attemptsLeft - 1,
                        onComplete = onComplete,
                    )
                },
                POLL_DELAY_MS,
            )
        }
    }

    private fun looksResolvedEnough(content: String): Boolean {
        if (content.contains("Join now to view geocache location details", ignoreCase = true)) {
            return true
        }

        val directionalCoordinateRegex = Regex(
            """([NS])\s*(\d{1,2})[°\s]+(\d{1,2}\.\d+)\s*[, ]+\s*([EW])\s*(\d{1,3})[°\s]+(\d{1,2}\.\d+)""",
            setOf(RegexOption.IGNORE_CASE),
        )
        val match = directionalCoordinateRegex.find(content) ?: return false
        val latitude = directionalToDecimal(
            direction = match.groupValues[1],
            degrees = match.groupValues[2].toDoubleOrNull(),
            minutes = match.groupValues[3].toDoubleOrNull(),
        )
        val longitude = directionalToDecimal(
            direction = match.groupValues[4],
            degrees = match.groupValues[5].toDoubleOrNull(),
            minutes = match.groupValues[6].toDoubleOrNull(),
        )
        val target = if (latitude != null && longitude != null) {
            MissionTarget(latitude, longitude)
        } else {
            null
        }
        return target?.isValid() == true
    }

    private fun directionalToDecimal(
        direction: String,
        degrees: Double?,
        minutes: Double?,
    ): Double? {
        if (degrees == null || minutes == null) {
            return null
        }
        val absolute = kotlin.math.abs(degrees) + (minutes / 60.0)
        return when (direction.uppercase()) {
            "N", "E" -> absolute
            "S", "W" -> -absolute
            else -> null
        }
    }

    private companion object {
        const val MAX_POLL_ATTEMPTS = 8
        const val POLL_DELAY_MS = 500L
    }
}
