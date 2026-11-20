package com.tobi.app.core.runtime

import android.content.Context
import android.net.Uri
import com.tobi.app.core.database.InstalledConnectorDao
import com.tobi.app.core.database.InstalledConnectorEntity
import com.tobi.app.core.network.NetworkModule
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.core.protocol.ConnectorManifest
import com.tobi.app.core.protocol.NetPolicy
import com.tobi.app.core.protocol.RepositoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class InstalledConnector(
    val connectorId: String,
    val displayName: String,
    val version: String,
    val scriptPath: String,
    val manifestJson: String,
    val enabled: Boolean,
    val sourceUrl: String?,
    val installedAt: Long,
    val updatedAt: Long
) {
    fun manifest(): ConnectorManifest {
        return try {
            ConnectorJson.decodeFromString(ConnectorManifest.serializer(), manifestJson)
        } catch (e: Exception) {
            ConnectorManifest(connectorId = connectorId, displayName = displayName)
        }
    }
}

class ConnectorManager(
    private val context: Context,
    private val vault: SessionVault,
    private val network: NetworkModule,
    private val dao: InstalledConnectorDao
) {
    fun observeInstalled(): Flow<List<InstalledConnector>> {
        return dao.observeAll().map { entities -> entities.map { it.toInstalled() } }
    }

    suspend fun getInstalled(connectorId: String): InstalledConnector? {
        return dao.getById(connectorId)?.toInstalled()
    }

    suspend fun installFromJsUrl(url: String): InstalledConnector = withContext(Dispatchers.IO) {
        val jsText = downloadText(url)
        val manifest = resolveManifestForJs(url, jsText)
        persist(manifest, jsText, url)
    }

    suspend fun installFromManifestUrl(url: String): InstalledConnector = withContext(Dispatchers.IO) {
        val manifestText = downloadText(url)
        val root = ConnectorJson.parseToJsonElement(manifestText)
        if (root is JsonArray) {
            throw IllegalArgumentException("Repository manifest contains multiple connectors. Use repository import instead.")
        }
        val obj = root.jsonObject
        val jsUrl = obj["jsUrl"]?.let { try { it.jsonPrimitive.content } catch (e: Exception) { null } }
        val manifest = ConnectorJson.decodeFromString(ConnectorManifest.serializer(), manifestText)
        val jsText = when {
            !jsUrl.isNullOrBlank() -> downloadText(jsUrl)
            url.substringBefore('?').endsWith(".json", ignoreCase = true) -> downloadCompanionScript(url)
            else -> downloadText(url)
        }
        persist(manifest, jsText, url)
    }

    suspend fun installFromRepositoryEntry(entry: RepositoryEntry): InstalledConnector = withContext(Dispatchers.IO) {
        val jsText = downloadText(entry.jsUrl)
        val manifest = if (!entry.manifestUrl.isNullOrBlank()) {
            try {
                val manifestText = downloadText(entry.manifestUrl)
                ConnectorJson.decodeFromString(ConnectorManifest.serializer(), manifestText)
            } catch (e: Exception) {
                synthesizeManifest(jsText, entry.jsUrl, entry.connectorId, entry.displayName)
            }
        } else {
            synthesizeManifest(jsText, entry.jsUrl, entry.connectorId, entry.displayName)
        }
        persist(manifest, jsText, entry.jsUrl)
    }

    suspend fun installFromFile(uri: Uri): InstalledConnector = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IllegalArgumentException("Could not read file")
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{")) {
            val root = ConnectorJson.parseToJsonElement(text)
            val obj = root.jsonObject
            val jsUrl = obj["jsUrl"]?.let { try { it.jsonPrimitive.content } catch (e: Exception) { null } }
            val manifest = ConnectorJson.decodeFromString(ConnectorManifest.serializer(), text)
            val jsText = if (!jsUrl.isNullOrBlank()) downloadText(jsUrl) else throw IllegalArgumentException("Manifest file has no jsUrl")
            persist(manifest, jsText, jsUrl)
        } else {
            val manifest = synthesizeManifest(text, uri.toString(), null, null)
            persist(manifest, text, uri.toString())
        }
    }

    suspend fun importRepository(url: String): List<RepositoryEntry> = withContext(Dispatchers.IO) {
        val text = downloadText(url)
        val root = ConnectorJson.parseToJsonElement(text)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val nested = root["connectors"] ?: root["entries"] ?: root["items"]
                if (nested is JsonArray) nested else JsonArray(listOf(root))
            }
            else -> JsonArray(emptyList())
        }
        array.jsonArray.mapNotNull { element ->
            try {
                ConnectorJson.decodeFromJsonElement(RepositoryEntry.serializer(), element)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun setEnabled(connectorId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val entity = dao.getById(connectorId) ?: return@withContext
        dao.upsert(entity.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun update(connectorId: String): InstalledConnector = withContext(Dispatchers.IO) {
        val existing = dao.getById(connectorId) ?: throw IllegalArgumentException("Connector not found")
        val source = existing.sourceUrl ?: throw IllegalArgumentException("Connector has no source URL")
        if (source.endsWith(".json")) {
            installFromManifestUrl(source)
        } else {
            installFromJsUrl(source)
        }
    }

    suspend fun uninstall(connectorId: String) = withContext(Dispatchers.IO) {
        val entity = dao.getById(connectorId) ?: return@withContext
        File(entity.scriptPath).parentFile?.deleteRecursively()
        vault.clearConnectorPrefs(connectorId)
        dao.delete(connectorId)
    }

    suspend fun updateDefaultNet(connectorId: String, headers: Map<String, String>) = withContext(Dispatchers.IO) {
        val entity = dao.getById(connectorId) ?: return@withContext
        val manifest = ConnectorJson.decodeFromString(ConnectorManifest.serializer(), entity.manifestJson)
        val updated = manifest.copy(
            defaultNet = NetPolicy(
                headers = headers,
                attachCookies = manifest.defaultNet?.attachCookies ?: true,
                lockUserAgent = manifest.defaultNet?.lockUserAgent ?: false
            )
        )
        val updatedJson = ConnectorJson.encodeToString(ConnectorManifest.serializer(), updated)
        writeMeta(entity.scriptPath, updatedJson)
        dao.upsert(entity.copy(manifestJson = updatedJson, updatedAt = System.currentTimeMillis()))
    }

    private fun resolveManifestForJs(jsUrl: String, jsText: String): ConnectorManifest {
        val sibling = siblingUrl(jsUrl, "connector.json")
        return try {
            val manifestText = downloadText(sibling)
            val manifest = ConnectorJson.decodeFromString(ConnectorManifest.serializer(), manifestText)
            if (manifest.connectorId.isBlank()) synthesizeManifest(jsText, jsUrl, null, null) else manifest
        } catch (e: Exception) {
            synthesizeManifest(jsText, jsUrl, null, null)
        }
    }

    private fun synthesizeManifest(jsText: String, sourceUrl: String, forcedId: String?, forcedName: String?): ConnectorManifest {
        val idFromScript = Regex("""connectorId\s*[:=]\s*["']([^"']+)["']""").find(jsText)?.groupValues?.get(1)
        val idFromUrl = sourceUrl.substringAfterLast("/").substringBefore(".").takeIf { it.isNotBlank() }
        val connectorId = forcedId ?: idFromScript ?: idFromUrl ?: "connector_${System.currentTimeMillis()}"
        val features = com.tobi.app.core.protocol.ConnectorFeatures(
            feed = jsText.contains("fetchFeed"),
            catalogSearch = jsText.contains("queryCatalog"),
            dynamicFilters = jsText.contains("facets"),
            deferredSources = jsText.contains("resolveDeferredSource"),
            downloads = jsText.contains("acquisitions"),
            needsWebViewBridge = jsText.contains("webview.sniff")
        )
        return ConnectorManifest(
            connectorId = connectorId,
            displayName = forcedName ?: connectorId,
            version = "1.0.0",
            features = features
        )
    }

    private suspend fun persist(manifest: ConnectorManifest, jsText: String, sourceUrl: String?): InstalledConnector {
        val connectorId = manifest.connectorId.ifBlank { "connector_${System.currentTimeMillis()}" }
        val dir = File(context.filesDir, "connectors/$connectorId")
        dir.mkdirs()
        val scriptFile = File(dir, "connector.js")
        scriptFile.writeText(jsText)
        val manifestJson = ConnectorJson.encodeToString(ConnectorManifest.serializer(), manifest)
        writeMeta(scriptFile.absolutePath, manifestJson)
        val connectorPreferences = vault.connectorPrefs(connectorId)
        val preferenceEditor = connectorPreferences.edit()
        var hasNewDefaults = false
        manifest.userConfigSchema.forEach { field ->
            if (!connectorPreferences.contains(field.key) && field.defaultValue != null) {
                preferenceEditor.putString(field.key, field.defaultValue)
                hasNewDefaults = true
            }
        }
        if (hasNewDefaults) preferenceEditor.apply()
        val now = System.currentTimeMillis()
        val existing = dao.getById(connectorId)
        val entity = InstalledConnectorEntity(
            connectorId = connectorId,
            displayName = manifest.displayName.ifBlank { connectorId },
            version = manifest.version,
            scriptPath = scriptFile.absolutePath,
            manifestJson = manifestJson,
            enabled = existing?.enabled ?: true,
            sourceUrl = sourceUrl,
            installedAt = existing?.installedAt ?: now,
            updatedAt = now
        )
        dao.upsert(entity)
        return entity.toInstalled()
    }

    private fun writeMeta(scriptPath: String, manifestJson: String) {
        val dir = File(scriptPath).parentFile ?: return
        File(dir, "meta.json").writeText(manifestJson)
    }

    private fun downloadText(url: String): String {
        val headers = vault.buildHeaders(null, null, url)
        val result = network.executeSimple("GET", url, headers, null, 30000, true)
        if (result.status !in 200..299) {
            throw IllegalStateException("Download failed with HTTP ${result.status}")
        }
        return result.bodyText
    }

    private fun downloadCompanionScript(manifestUrl: String): String {
        val candidates = listOf("connector.js", "index.js")
        val failures = mutableListOf<String>()
        candidates.forEach { fileName ->
            val scriptUrl = siblingUrl(manifestUrl, fileName)
            try {
                return downloadText(scriptUrl)
            } catch (error: Exception) {
                failures += "$fileName: ${error.message}"
            }
        }
        throw IllegalStateException(
            "No connector script found beside the manifest. Expected connector.js or index.js. ${failures.joinToString("; ")}"
        )
    }

    private fun siblingUrl(url: String, fileName: String): String {
        val cleanUrl = url.substringBefore('?')
        val query = url.substringAfter('?', "").takeIf { it.isNotBlank() }
        val base = cleanUrl.substringBeforeLast("/")
        val sibling = if (base.isBlank()) fileName else "$base/$fileName"
        return if (query == null) sibling else "$sibling?$query"
    }

    private fun InstalledConnectorEntity.toInstalled(): InstalledConnector {
        return InstalledConnector(
            connectorId = connectorId,
            displayName = displayName,
            version = version,
            scriptPath = scriptPath,
            manifestJson = manifestJson,
            enabled = enabled,
            sourceUrl = sourceUrl,
            installedAt = installedAt,
            updatedAt = updatedAt
        )
    }
}
