package com.tobi.app.core.runtime

import android.content.Context
import android.util.Base64
import com.tobi.app.core.network.NetworkModule
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.network.hostOf
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.core.protocol.NetPolicy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

interface NativeBridgeApi {
    fun invokeNative(method: String, argsJson: String): String
}

class NativeBridge(
    private val context: Context,
    private val vault: SessionVault,
    private val network: NetworkModule,
    private val connectorId: String,
    private val connectorDefaultNet: NetPolicy?,
    private val sniffer: WebViewSniffer
) : NativeBridgeApi {
    override fun invokeNative(method: String, argsJson: String): String {
        return try {
            val args = ConnectorJson.parseToJsonElement(argsJson).jsonObject
            val result = dispatch(method, args)
            ConnectorJson.encodeToString(JsonElement.serializer(), result)
        } catch (e: Exception) {
            RuntimeLog.log("error", "Bridge $method failed: ${e.message}", connectorId)
            ConnectorJson.encodeToString(
                JsonElement.serializer(),
                buildJsonObject { put("error", e.message ?: "native bridge error") }
            )
        }
    }

    private fun dispatch(method: String, args: JsonObject): JsonElement {
        return when (method) {
            "http.send" -> httpSend(args)
            "dom.query" -> domQuery(args)
            "webview.sniff" -> webviewSniff(args)
            "crypto.decrypt" -> cryptoDecrypt(args)
            "crypto.encrypt" -> cryptoEncrypt(args)
            "crypto.hmacSha256" -> cryptoHmac(args)
            "crypto.sha256" -> JsonPrimitive(sha256Hex(args.string("text") ?: args.string("data") ?: ""))
            "crypto.md5" -> JsonPrimitive(md5Hex(args.string("text") ?: args.string("data") ?: ""))
            "crypto.base64Encode" -> JsonPrimitive(Base64.encodeToString((args.string("text") ?: args.string("data") ?: "").toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            "crypto.base64Decode" -> JsonPrimitive(String(Base64.decode(args.string("text") ?: args.string("data") ?: "", Base64.DEFAULT), Charsets.UTF_8))
            "crypto.hexEncode" -> JsonPrimitive(hexEncode((args.string("text") ?: args.string("data") ?: "").toByteArray(Charsets.UTF_8)))
            "crypto.hexDecode" -> JsonPrimitive(String(hexDecode(args.string("text") ?: args.string("data") ?: ""), Charsets.UTF_8))
            "vault.get" -> vaultGet(args)
            "vault.set" -> vaultSet(args)
            "log" -> {
                RuntimeLog.log(args.string("level") ?: "info", args.string("message") ?: args.string("msg") ?: "", connectorId)
                JsonNull
            }
            else -> buildJsonObject { put("error", "unknown bridge method: $method") }
        }
    }

    private fun httpSend(args: JsonObject): JsonElement {
        val method = args.string("method") ?: "GET"
        val url = args.string("url") ?: return buildJsonObject { put("error", "missing url") }
        val callHeaders = args.headers("headers")
        val base = vault.buildHeaders(null, connectorDefaultNet, url)
        val merged = LinkedHashMap<String, String>()
        merged.putAll(base)
        merged.putAll(callHeaders)
        val body = args.string("body")
        val timeoutMs = args.long("timeoutMs") ?: 30000L
        val followRedirects = args.boolean("followRedirects") ?: true
        val result = network.executeSimple(method, url, merged, body, timeoutMs, followRedirects)
        return buildJsonObject {
            put("status", result.status)
            put("finalUrl", result.finalUrl)
            val headerObject = buildJsonObject {
                result.headers.forEach { (key, value) -> put(key, value) }
            }
            put("headers", headerObject)
            put("bodyText", result.bodyText)
        }
    }

    private fun domQuery(args: JsonObject): JsonElement {
        val html = args.string("html") ?: ""
        val css = args.string("css") ?: ""
        if (html.isBlank() || css.isBlank()) return JsonArray(emptyList())
        val document = Jsoup.parse(html)
        val elements = document.select(css)
        val items = elements.map { element ->
            val attrs = buildJsonObject {
                element.attributes().forEach { attribute ->
                    put(attribute.key, attribute.value)
                }
            }
            buildJsonObject {
                put("text", element.text())
                put("html", element.html())
                put("attrs", attrs)
            }
        }
        return JsonArray(items)
    }

    private fun webviewSniff(args: JsonObject): JsonElement {
        val url = args.string("url") ?: return buildJsonObject { put("error", "missing url") }
        val waitMs = args.long("waitMs") ?: 8000L
        val patterns = args.stringList("interceptPatterns")
        val result = sniffer.sniff(url, waitMs, patterns)
        val intercepted = result.intercepted.map { item ->
            val headerObject = buildJsonObject {
                item.headers.forEach { (key, value) -> put(key, value) }
            }
            buildJsonObject {
                put("url", item.url)
                put("headers", headerObject)
            }
        }
        return buildJsonObject {
            put("finalUrl", result.finalUrl)
            put("intercepted", JsonArray(intercepted))
            put("cookies", result.cookies ?: "")
        }
    }

    private fun cryptoDecrypt(args: JsonObject): JsonElement {
        val algorithm = args.string("algorithm") ?: "AES/CBC/PKCS5Padding"
        val key = keyBytes(args)
        val iv = ivBytes(args)
        val data = dataBytes(args)
        val cipher = Cipher.getInstance(algorithm)
        val keySpec = SecretKeySpec(key, "AES")
        when {
            algorithm.contains("GCM", ignoreCase = true) -> cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            else -> cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        }
        val plain = cipher.doFinal(data)
        return JsonPrimitive(Base64.encodeToString(plain, Base64.NO_WRAP))
    }

    private fun cryptoEncrypt(args: JsonObject): JsonElement {
        val algorithm = args.string("algorithm") ?: "AES/CBC/PKCS5Padding"
        val key = keyBytes(args)
        val iv = ivBytes(args)
        val data = dataBytes(args)
        val cipher = Cipher.getInstance(algorithm)
        val keySpec = SecretKeySpec(key, "AES")
        when {
            algorithm.contains("GCM", ignoreCase = true) -> cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            else -> cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
        }
        val encrypted = cipher.doFinal(data)
        return JsonPrimitive(Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    private fun cryptoHmac(args: JsonObject): JsonElement {
        val key = keyBytes(args)
        val data = dataBytes(args)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return JsonPrimitive(hexEncode(mac.doFinal(data)))
    }

    private fun keyBytes(args: JsonObject): ByteArray {
        args.string("keyHex")?.let { return hexDecode(it) }
        args.string("keyBase64")?.let { return Base64.decode(it, Base64.DEFAULT) }
        val raw = args.string("key") ?: ""
        return try {
            Base64.decode(raw, Base64.DEFAULT)
        } catch (e: Exception) {
            raw.toByteArray(Charsets.UTF_8)
        }
    }

    private fun ivBytes(args: JsonObject): ByteArray {
        args.string("ivHex")?.let { return hexDecode(it) }
        args.string("ivBase64")?.let { return Base64.decode(it, Base64.DEFAULT) }
        val raw = args.string("iv") ?: ""
        return try {
            Base64.decode(raw, Base64.DEFAULT)
        } catch (e: Exception) {
            raw.toByteArray(Charsets.UTF_8)
        }
    }

    private fun dataBytes(args: JsonObject): ByteArray {
        args.string("dataHex")?.let { return hexDecode(it) }
        args.string("dataBase64")?.let { return Base64.decode(it, Base64.DEFAULT) }
        val raw = args.string("data") ?: args.string("text") ?: ""
        return try {
            Base64.decode(raw, Base64.DEFAULT)
        } catch (e: Exception) {
            raw.toByteArray(Charsets.UTF_8)
        }
    }

    private fun vaultGet(args: JsonObject): JsonElement {
        val key = args.string("key") ?: return JsonNull
        val value = vault.connectorPrefs(connectorId).getString(key, null)
        return if (value == null) JsonNull else JsonPrimitive(value)
    }

    private fun vaultSet(args: JsonObject): JsonElement {
        val key = args.string("key") ?: return JsonNull
        val value = args.string("value") ?: args.string("val") ?: ""
        vault.connectorPrefs(connectorId).edit().putString(key, value).apply()
        return JsonPrimitive(true)
    }

    private fun sha256Hex(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return hexEncode(digest.digest(text.toByteArray(Charsets.UTF_8)))
    }

    private fun md5Hex(text: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        return hexEncode(digest.digest(text.toByteArray(Charsets.UTF_8)))
    }

    private fun hexEncode(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            builder.append(String.format("%02x", byte))
        }
        return builder.toString()
    }

    private fun hexDecode(text: String): ByteArray {
        val cleaned = text.trim().replace(" ", "").replace(":", "")
        val output = ByteArray(cleaned.length / 2)
        var index = 0
        while (index < output.size) {
            val pair = cleaned.substring(index * 2, index * 2 + 2)
            output[index] = pair.toInt(16).toByte()
            index++
        }
        return output
    }

    private fun JsonObject.string(key: String): String? {
        val element = this[key] ?: return null
        return try {
            element.jsonPrimitive.content
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.long(key: String): Long? {
        val element = this[key] ?: return null
        return try {
            element.jsonPrimitive.content.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val element = this[key] ?: return null
        return try {
            element.jsonPrimitive.content.toBooleanStrictOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.headers(key: String): Map<String, String> {
        val element = this[key] ?: return emptyMap()
        return try {
            val obj = element.jsonObject
            val map = LinkedHashMap<String, String>()
            obj.forEach { (name, value) ->
                try {
                    map[name] = value.jsonPrimitive.content
                } catch (e: Exception) {
                    map[name] = value.toString()
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val element = this[key] ?: return emptyList()
        return try {
            val array = element as? JsonArray ?: return emptyList()
            array.mapNotNull { item ->
                try {
                    item.jsonPrimitive.content
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
