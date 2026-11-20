package com.tobi.app.core.runtime

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebRequest
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.network.hostOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class InterceptedRequest(
    val url: String,
    val headers: Map<String, String>
)

data class SniffResult(
    val finalUrl: String,
    val intercepted: List<InterceptedRequest>,
    val cookies: String? = null
)

object SniffRegistry {
    private var callback: ((SniffResult) -> Unit)? = null

    fun register(callback: (SniffResult) -> Unit) {
        this.callback = callback
    }

    fun complete(result: SniffResult) {
        callback?.invoke(result)
        callback = null
    }

    fun clear() {
        callback = null
    }
}

class WebViewSniffer(
    private val context: Context,
    private val vault: SessionVault
) {
    fun sniff(url: String, waitMs: Long, patterns: List<String>): SniffResult {
        val latch = CountDownLatch(1)
        val holder = AtomicReference<SniffResult?>(null)
        SniffRegistry.register { result ->
            holder.set(result)
            latch.countDown()
        }
        val intent = Intent(context, SniffActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("url", url)
            putExtra("waitMs", waitMs)
            putExtra("patterns", patterns.toTypedArray())
        }
        context.startActivity(intent)
        latch.await(waitMs + 20000, TimeUnit.MILLISECONDS)
        return holder.get() ?: SniffResult(url, emptyList())
    }
}

class SniffActivity : Activity() {
    private var webView: WebView? = null
    private var completed = false
    private val intercepted = mutableListOf<InterceptedRequest>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: ""
        val waitMs = intent.getLongExtra("waitMs", 8000L)
        val patterns = intent.getStringArrayExtra("patterns")?.toList() ?: emptyList()
        val view = WebView(this)
        webView = view
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                super.onPageFinished(view, finishedUrl)
                handler.postDelayed({ finishWithResult(finishedUrl) }, waitMs.coerceIn(1000L, 30000L))
            }

            override fun shouldInterceptRequest(view: WebView, request: WebRequest): WebResourceResponse? {
                val requestUrl = request.url.toString()
                if (matchesPattern(requestUrl, patterns)) {
                    intercepted.add(InterceptedRequest(requestUrl, HashMap(request.requestHeaders)))
                }
                return null
            }

            override fun onReceivedError(view: WebView, request: WebRequest, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, request, errorCode, description, failingUrl)
                finishWithResult(failingUrl)
            }
        }
        setContentView(view)
        if (url.isNotBlank()) {
            view.loadUrl(url)
        } else {
            finishWithResult(url)
        }
    }

    private fun matchesPattern(requestUrl: String, patterns: List<String>): Boolean {
        val lower = requestUrl.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4")) return true
        return patterns.any { pattern -> pattern.isNotBlank() && lower.contains(pattern.lowercase()) }
    }

    private fun finishWithResult(finalUrl: String) {
        if (completed) return
        completed = true
        val cookies = CookieManager.getInstance().getCookie(finalUrl)
        val host = hostOf(finalUrl)
        if (host.isNotEmpty()) {
            val vault = (application as com.tobi.app.TobiApplication).container.vault
            vault.syncFromWebView(finalUrl)
        }
        SniffRegistry.complete(SniffResult(finalUrl, intercepted.toList(), cookies))
        finish()
    }

    override fun onDestroy() {
        if (!completed) {
            SniffRegistry.complete(SniffResult("", intercepted.toList()))
        }
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        super.onDestroy()
    }
}
