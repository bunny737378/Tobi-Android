package com.tobi.app.core.network

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.core.protocol.NetPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_vault")

class SessionVault(context: Context) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cookieSnapshot: MutableMap<String, MutableList<String>> = ConcurrentHashMap()
    private val userAgentSnapshot: MutableMap<String, String> = ConcurrentHashMap()
    private val cookiesKey = stringPreferencesKey("vault_cookies")
    private val userAgentsKey = stringPreferencesKey("vault_user_agents")

    init {
        scope.launch {
            appContext.sessionDataStore.data.collect { prefs ->
                prefs[cookiesKey]?.let { cookiesJson ->
                    runCatching {
                        ConnectorJson.decodeFromString(CookieMapSerializer, cookiesJson)
                    }.onSuccess { parsed ->
                        cookieSnapshot.clear()
                        parsed.forEach { (host, values) -> cookieSnapshot[host] = values.toMutableList() }
                    }
                }
                prefs[userAgentsKey]?.let { userAgentsJson ->
                    runCatching {
                        ConnectorJson.decodeFromString(StringMapSerializer, userAgentsJson)
                    }.onSuccess { parsed ->
                        userAgentSnapshot.clear()
                        userAgentSnapshot.putAll(parsed)
                    }
                }
            }
        }
    }

    private fun persistCookies() {
        val copy = HashMap(cookieSnapshot)
        scope.launch {
            appContext.sessionDataStore.edit { prefs ->
                prefs[cookiesKey] = ConnectorJson.encodeToString(CookieMapSerializer, copy)
            }
        }
    }

    private fun persistUserAgents() {
        val copy = HashMap(userAgentSnapshot)
        scope.launch {
            appContext.sessionDataStore.edit { prefs ->
                prefs[userAgentsKey] = ConnectorJson.encodeToString(StringMapSerializer, copy)
            }
        }
    }

    fun storeCookies(host: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val existing = cookieSnapshot.getOrPut(host) { mutableListOf() }
        cookies.forEach { cookie ->
            existing.removeAll { it.substringBefore("=") == cookie.name }
            existing.add(cookie.toString())
        }
        persistCookies()
    }

    fun loadCookies(host: String): List<Cookie> {
        val url = HttpUrl.Builder().scheme("https").host(host).build()
        val matched = mutableListOf<Cookie>()
        cookieSnapshot.forEach { (storedHost, values) ->
            if (host == storedHost || host.endsWith(".$storedHost") || storedHost.endsWith(".$host")) {
                values.forEach { raw ->
                    Cookie.parse(url, raw)?.let { matched.add(it) }
                }
            }
        }
        return matched
    }

    fun cookieHeaderFor(host: String): String {
        val cookies = loadCookies(host)
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun setLockedUserAgent(domain: String, userAgent: String) {
        userAgentSnapshot[domain] = userAgent
        persistUserAgents()
    }

    fun lockedUserAgent(domain: String): String? = userAgentSnapshot[domain]

    fun clearDomain(domain: String) {
        cookieSnapshot.remove(domain)
        userAgentSnapshot.remove(domain)
        persistCookies()
        persistUserAgents()
    }

    fun clearAll() {
        cookieSnapshot.clear()
        userAgentSnapshot.clear()
        persistCookies()
        persistUserAgents()
        scope.launch {
            appContext.sessionDataStore.edit { prefs -> prefs.clear() }
        }
    }

    fun buildHeaders(net: NetPolicy?, connectorDefault: NetPolicy?, url: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        merged["User-Agent"] = DEFAULT_USER_AGENT
        connectorDefault?.headers?.forEach { (key, value) -> merged[key] = value }
        val host = hostOf(url)
        val lockRequested = net?.lockUserAgent == true || connectorDefault?.lockUserAgent == true
        if (lockRequested && host.isNotEmpty()) {
            lockedUserAgent(host)?.let { merged["User-Agent"] = it }
        }
        net?.headers?.forEach { (key, value) -> merged[key] = value }
        val attach = net?.attachCookies != false
        if (attach && host.isNotEmpty()) {
            val scopeHost = net?.cookieScope?.takeIf { it.isNotBlank() } ?: host
            val cookieHeader = cookieHeaderFor(scopeHost)
            if (cookieHeader.isNotEmpty()) {
                merged["Cookie"] = cookieHeader
            }
        }
        return merged
    }

    fun syncToWebView(url: String) {
        val manager = CookieManager.getInstance()
        val host = hostOf(url)
        if (host.isEmpty()) return
        loadCookies(host).forEach { cookie ->
            manager.setCookie(url, "${cookie.name}=${cookie.value}; path=${cookie.path}")
        }
        manager.flush()
    }

    fun syncFromWebView(url: String) {
        val manager = CookieManager.getInstance()
        val raw = manager.getCookie(url) ?: return
        val host = hostOf(url)
        if (host.isEmpty()) return
        val parsed = HttpUrl.Builder().scheme("https").host(host).build()
        val cookies = raw.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) null else Cookie.parse(parsed, trimmed)
        }
        if (cookies.isNotEmpty()) {
            storeCookies(host, cookies)
        }
        val userAgent = WebSettings.getDefaultUserAgent(appContext)
        setLockedUserAgent(host, userAgent)
    }

    fun connectorPrefs(connectorId: String): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            "connector_$connectorId",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun clearConnectorPrefs(connectorId: String) {
        connectorPrefs(connectorId).edit().clear().apply()
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}

private val CookieMapSerializer = MapSerializer(
    String.serializer(),
    ListSerializer(String.serializer())
)

private val StringMapSerializer = MapSerializer(
    String.serializer(),
    String.serializer()
)

fun hostOf(url: String): String {
    return try {
        val parsed = url.toHttpUrlOrNull() ?: return ""
        parsed.host
    } catch (e: Exception) {
        ""
    }
}
