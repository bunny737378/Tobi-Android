package com.tobi.app.core.downloader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tobi.app.TobiApplication
import com.tobi.app.core.protocol.ConnectorJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Request
import java.io.FileOutputStream

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getLong(DOWNLOAD_ID, -1)
        if (id < 0) return@withContext Result.failure()
        val container = (applicationContext as TobiApplication).container
        val dao = container.database.downloadDao()
        val item = dao.getById(id) ?: return@withContext Result.failure()
        val output = container.downloadEngine.outputFile(item.fileName)
        val partial = output.length()
        val headers = runCatching {
            ConnectorJson.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                item.headersJson
            )
        }.getOrDefault(emptyMap())
        val request = Request.Builder().url(item.remoteUri).apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (partial > 0) header("Range", "bytes=$partial-")
        }.build()
        try {
            dao.updateProgress(id, DownloadStatus.DOWNLOADING, partial, item.totalBytes, 0, 0, 0, output.absolutePath, null, null)
            container.network.okHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty response")
                val append = partial > 0 && response.code == 206
                if (!append && output.exists()) output.delete()
                val startBytes = if (append) partial else 0L
                val total = body.contentLength().takeIf { it >= 0 }?.plus(startBytes) ?: item.totalBytes
                body.byteStream().use { input ->
                    FileOutputStream(output, append).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = startBytes
                        var lastBytes = downloaded
                        var lastTime = System.currentTimeMillis()
                        while (true) {
                            if (isStopped) throw InterruptedException("Download cancelled")
                            val count = input.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 750) {
                                val speed = (downloaded - lastBytes) * 1000 / (now - lastTime).coerceAtLeast(1)
                                dao.updateProgress(id, DownloadStatus.DOWNLOADING, downloaded, total, speed, 0, 0, output.absolutePath, null, null)
                                setProgress(androidx.work.Data.Builder().putLong("downloaded", downloaded).putLong("total", total).build())
                                lastTime = now
                                lastBytes = downloaded
                            }
                        }
                        dao.updateProgress(id, DownloadStatus.COMPLETED, downloaded, total, 0, 0, 0, output.absolutePath, null, System.currentTimeMillis())
                    }
                }
            }
            Result.success()
        } catch (e: InterruptedException) {
            dao.updateProgress(id, DownloadStatus.PAUSED, output.length(), item.totalBytes, 0, 0, 0, output.absolutePath, null, null)
            Result.failure()
        } catch (e: Exception) {
            dao.updateProgress(id, DownloadStatus.FAILED, output.length(), item.totalBytes, 0, 0, 0, output.absolutePath, e.message, null)
            Result.retry()
        }
    }

    companion object {
        const val DOWNLOAD_ID = "download_id"
    }
}
