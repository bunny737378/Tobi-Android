package com.tobi.app.core.network

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.ProxySelector
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class VaultCookieJar(private val vault: SessionVault) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        vault.storeCookies(url.host, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return vault.loadCookies(url.host)
    }
}

class NetworkModule(
    private val vault: SessionVault,
    private val prefs: UserPreferences
) {
    @Volatile
    private var cachedClient: OkHttpClient? = null

    fun okHttpClient(): OkHttpClient {
        cachedClient?.let { return it }
        synchronized(this) {
            cachedClient?.let { return it }
            val built = buildClient()
            cachedClient = built
            return built
        }
    }

    fun refreshClient() {
        synchronized(this) {
            cachedClient = null
        }
    }

    private fun buildClient(): OkHttpClient {
        val state = prefs.state.value
        val builder = OkHttpClient.Builder()
            .cookieJar(VaultCookieJar(vault))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .proxySelector(ProxySelector.getDefault())
        if (state.proxyEnabled && state.proxyHost.isNotBlank()) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(state.proxyHost, state.proxyPort)))
        }
        val base = builder.build()
        if (state.dohEnabled) {
            val dohUrl = state.dohUrl.toHttpUrlOrNull()
            if (dohUrl != null) {
                val dns = DnsOverHttps.Builder()
                    .client(base)
                    .url(dohUrl)
                    .build()
                return base.newBuilder().dns(dns).build()
            }
        }
        return base
    }

    fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient() }))
            }
            .build()
    }

    fun executeSimple(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Long,
        followRedirects: Boolean
    ): SimpleHttpResult {
        val client = okHttpClient().newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
            .build()
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toByteArray().toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            "PUT" -> requestBuilder.put((body ?: "").toByteArray().toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            "HEAD" -> requestBuilder.head()
            else -> requestBuilder.get()
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseHeaders = LinkedHashMap<String, String>()
            response.headers.forEach { (name, value) -> responseHeaders[name] = value }
            val responseBody = response.body
            val charset = responseBody?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val contentEncoding = response.header("Content-Encoding").orEmpty().lowercase()
            val bodyText = when {
                responseBody == null -> ""
                contentEncoding.contains("gzip") -> GZIPInputStream(responseBody.byteStream()).reader(charset).use { it.readText() }
                contentEncoding.contains("deflate") -> InflaterInputStream(responseBody.byteStream()).reader(charset).use { it.readText() }
                else -> responseBody.string()
            }
            return SimpleHttpResult(
                status = response.code,
                finalUrl = response.request.url.toString(),
                headers = responseHeaders,
                bodyText = bodyText
            )
        }
    }
}

data class SimpleHttpResult(
    val status: Int,
    val finalUrl: String,
    val headers: Map<String, String>,
    val bodyText: String
)
