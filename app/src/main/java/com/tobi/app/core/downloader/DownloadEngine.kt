package com.tobi.app.core.downloader

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tobi.app.core.database.AppDatabase
import com.tobi.app.core.database.DownloadEntity
import com.tobi.app.core.network.NetworkModule
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.network.UserPreferences
import com.tobi.app.core.protocol.Acquisition
import com.tobi.app.core.protocol.ConnectorJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val PAUSED = "PAUSED"
}

class DownloadEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val vault: SessionVault,
    private val network: NetworkModule,
    private val preferences: UserPreferences
) {
    suspend fun enqueue(connectorId: String, title: String, acquisition: Acquisition): Long = withContext(Dispatchers.IO) {
        val uri = acquisition.uri ?: throw IllegalArgumentException("Download URI is missing")
        val safeName = (acquisition.fileName ?: "$title.${acquisition.format ?: "mp4"}")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val headers = vault.buildHeaders(acquisition.net, null, uri)
        val id = database.downloadDao().insert(
            DownloadEntity(
                connectorId = connectorId,
                title = title,
                fileName = safeName,
                remoteUri = uri,
                status = DownloadStatus.QUEUED,
                totalBytes = acquisition.sizeBytes ?: 0,
                headersJson = ConnectorJson.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()), headers
                ),
                netJson = acquisition.net?.let { ConnectorJson.encodeToString(com.tobi.app.core.protocol.NetPolicy.serializer(), it) },
                isHls = acquisition.delivery.contains("hls", ignoreCase = true) || uri.substringBefore('?').endsWith(".m3u8", true),
                createdAt = System.currentTimeMillis()
            )
        )
        schedule(id)
        id
    }

    fun schedule(id: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putLong(DownloadWorker.DOWNLOAD_ID, id).build())
            .addTag("download-$id")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(id: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("download-$id")
    }

    fun outputFile(fileName: String): File {
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
        directory.mkdirs()
        return File(directory, fileName)
    }
}

class DownloadRepository(
    private val database: AppDatabase,
    private val engine: DownloadEngine
) {
    fun observeAll(): Flow<List<DownloadEntity>> = database.downloadDao().observeAll()

    suspend fun enqueue(connectorId: String, title: String, acquisition: Acquisition): Long =
        engine.enqueue(connectorId, title, acquisition)

    suspend fun retry(id: Long) {
        val item = database.downloadDao().getById(id) ?: return
        database.downloadDao().update(item.copy(status = DownloadStatus.QUEUED, errorMessage = null))
        engine.schedule(id)
    }

    suspend fun delete(id: Long) {
        val item = database.downloadDao().getById(id) ?: return
        engine.cancel(id)
        item.localPath?.let { File(it).delete() }
        database.downloadDao().deleteById(id)
    }
}
