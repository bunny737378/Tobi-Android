package com.tobi.app.di

import android.content.Context
import com.tobi.app.core.data.CollectionRepository
import com.tobi.app.core.data.ConnectorRepository
import com.tobi.app.core.data.HistoryRepository
import com.tobi.app.core.data.SearchHistoryRepository
import com.tobi.app.core.data.WatchlistRepository
import com.tobi.app.core.database.AppDatabase
import com.tobi.app.core.downloader.DownloadEngine
import com.tobi.app.core.downloader.DownloadRepository
import com.tobi.app.core.network.NetworkModule
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.network.UserPreferences
import com.tobi.app.core.runtime.ChallengeCoordinator
import com.tobi.app.core.runtime.ConnectorManager
import com.tobi.app.core.runtime.ConnectorRuntime
import com.tobi.app.core.runtime.InstalledConnector
import com.tobi.app.core.runtime.NativeBridge
import com.tobi.app.core.runtime.WebViewSniffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val vault = SessionVault(appContext)
    val userPreferences = UserPreferences(appContext)
    val network = NetworkModule(vault, userPreferences)
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val historyRepository = HistoryRepository(database)
    val watchlistRepository = WatchlistRepository(database)
    val searchHistoryRepository = SearchHistoryRepository(database)
    val collectionRepository = CollectionRepository(database)
    val challengeCoordinator = ChallengeCoordinator()
    val sniffer = WebViewSniffer(appContext, vault)
    val connectorManager = ConnectorManager(appContext, vault, network, database.installedConnectorDao())
    val connectorRepository = ConnectorRepository(connectorManager, userPreferences)
    val downloadEngine = DownloadEngine(appContext, database, vault, network, userPreferences)
    val downloadRepository = DownloadRepository(database, downloadEngine)
    val viewModelFactory = AppViewModelFactory(this)
    private val runtimes = ConcurrentHashMap<String, ConnectorRuntime>()

    init {
        scope.launch {
            challengeCoordinator.pending.filterNotNull().collectLatest { fault ->
                val target = fault.targetUri
                if (target.isNullOrBlank()) {
                    challengeCoordinator.complete(false)
                } else {
                    val result = runCatching { sniffer.sniff(target, 15_000, emptyList()) }.getOrNull()
                    challengeCoordinator.complete(result != null && result.finalUrl.isNotBlank())
                }
            }
        }
    }

    fun runtimeFor(connector: InstalledConnector): ConnectorRuntime {
        return runtimes.computeIfAbsent(connector.connectorId) {
            val script = File(connector.scriptPath).readText()
            val manifest = connector.manifest()
            val bridge = NativeBridge(appContext, vault, network, connector.connectorId, manifest.defaultNet, sniffer)
            ConnectorRuntime(connector.connectorId, script, manifest, bridge, challengeCoordinator)
        }
    }

    suspend fun runtimeForId(connectorId: String): ConnectorRuntime? {
        val connector = connectorManager.getInstalled(connectorId) ?: return null
        return runtimeFor(connector)
    }

    fun invalidateRuntime(connectorId: String) {
        runtimes.remove(connectorId)
    }
}
