package com.example.network

import android.content.Context
import android.os.Environment
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startDownload(
        scope: CoroutineScope,
        youtubeUrl: String,
        format: String,
        resolutionLabel: String,
        initialTitle: String = "YouTube Video",
        initialThumbnail: String = ""
    ): String {
        val taskId = "task_" + System.currentTimeMillis() + "_" + (1000..9999).random()

        val job = scope.launch(Dispatchers.IO) {
            var downloadItem = DownloadItem(
                taskId = taskId,
                title = initialTitle,
                youtubeUrl = youtubeUrl,
                format = format,
                resolutionLabel = resolutionLabel,
                thumbnailUrl = initialThumbnail,
                status = DownloadItem.STATUS_PREPARING,
                progressPercent = 0,
                speedText = "Connecting..."
            )
            val dbId = repository.insert(downloadItem)
            downloadItem = downloadItem.copy(id = dbId)

            var targetFile: File? = null

            try {
                // Step 1: Request download start
                updateStatus(downloadItem, DownloadItem.STATUS_PREPARING, 5, "Connecting to service...")

                val encodedUrl = URLEncoder.encode(youtubeUrl, "UTF-8")
                val startApiUrl = "https://downclip.vercel.app/api/download/start?url=$encodedUrl&format=$format"

                val startRequest = Request.Builder()
                    .url(startApiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipTube/1.0")
                    .build()

                val startResponse = client.newCall(startRequest).execute()
                if (!startResponse.isSuccessful) {
                    throw IllegalStateException("API error: HTTP ${startResponse.code}")
                }

                val startBody = startResponse.body?.string() ?: throw IllegalStateException("Empty API response")
                val startJson = JSONObject(startBody)

                val isSuccess = startJson.optBoolean("success", false) ||
                        startJson.optInt("success", 0) == 1 ||
                        startJson.has("id")

                if (!isSuccess && !startJson.has("id")) {
                    val msg = startJson.optString("message", "Failed to start download")
                    throw IllegalStateException(msg)
                }

                val downloadId = startJson.optString("id")
                if (downloadId.isEmpty()) {
                    throw IllegalStateException("No download ID received from server")
                }

                val fetchedTitle = startJson.optString("title", initialTitle).ifEmpty { initialTitle }
                val fetchedImage = startJson.optString("image", initialThumbnail).ifEmpty { initialThumbnail }

                downloadItem = downloadItem.copy(
                    title = fetchedTitle,
                    thumbnailUrl = fetchedImage
                )
                repository.update(downloadItem)

                // Step 2: Poll progress endpoint
                updateStatus(downloadItem, DownloadItem.STATUS_PREPARING, 15, "Server converting media...")

                var downloadUrl = ""
                var pollCount = 0
                val maxPolls = 70 // approx 100 seconds max

                val progressBaseUrl = "https://p.savenow.to/api/progress?id=$downloadId"

                while (isActive && downloadUrl.isEmpty() && pollCount < maxPolls) {
                    delay(1500)
                    pollCount++

                    try {
                        val pollRequest = Request.Builder()
                            .url(progressBaseUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipTube/1.0")
                            .build()

                        val pollResponse = client.newCall(pollRequest).execute()
                        if (pollResponse.isSuccessful) {
                            val pollBody = pollResponse.body?.string() ?: ""
                            if (pollBody.isNotEmpty()) {
                                val pollJson = JSONObject(pollBody)
                                val candidateUrl = pollJson.optString("download_url", "")
                                if (candidateUrl.isNotEmpty() && candidateUrl.startsWith("http")) {
                                    downloadUrl = candidateUrl
                                    val serverTitle = pollJson.optString("title", "")
                                    if (serverTitle.isNotEmpty()) {
                                        downloadItem = downloadItem.copy(title = serverTitle)
                                    }
                                    break
                                } else {
                                    val textStatus = pollJson.optString("text", "Preparing streaming...")
                                    val serverProgress = pollJson.optInt("progress", 0)
                                    // Map server preparation progress to 10-25%
                                    val mapped = 15 + ((serverProgress.coerceIn(0, 1000) / 1000f) * 10).toInt()
                                    updateStatus(downloadItem, DownloadItem.STATUS_PREPARING, mapped, textStatus)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // continue polling
                    }
                }

                if (downloadUrl.isEmpty()) {
                    throw IllegalStateException("Timeout waiting for server download link")
                }

                // Step 3: Stream and download to local file
                updateStatus(downloadItem, DownloadItem.STATUS_DOWNLOADING, 25, "Starting download...")

                val downloadDir = getDownloadDirectory()
                val safeFileName = sanitizeFileName(downloadItem.title, format)
                val destinationFile = File(downloadDir, safeFileName)

                // If file with same name exists, add suffix
                var finalFile = destinationFile
                var counter = 1
                while (finalFile.exists()) {
                    val nameWithoutExt = safeFileName.substringBeforeLast(".")
                    val ext = safeFileName.substringAfterLast(".", "mp4")
                    finalFile = File(downloadDir, "${nameWithoutExt}_$counter.$ext")
                    counter++
                }
                targetFile = finalFile

                val streamRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipTube/1.0")
                    .build()

                val streamResponse = client.newCall(streamRequest).execute()
                if (!streamResponse.isSuccessful) {
                    throw IllegalStateException("Download stream failed: HTTP ${streamResponse.code}")
                }

                val responseBody = streamResponse.body ?: throw IllegalStateException("Empty download stream")
                val totalBytes = responseBody.contentLength().let { if (it <= 0) 10_000_000L else it }

                downloadItem = downloadItem.copy(
                    filePath = finalFile.absolutePath,
                    fileSizeBytes = totalBytes
                )
                repository.update(downloadItem)

                val inputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(finalFile)

                val buffer = ByteArray(32 * 1024)
                var downloadedBytes = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            if (!isActive) {
                                throw CancellationException("Download cancelled by user")
                            }
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            bytesSinceLastUpdate += read

                            val now = System.currentTimeMillis()
                            val timeDiff = now - lastUpdateTime

                            if (timeDiff >= 300) {
                                val speedBps = (bytesSinceLastUpdate * 1000) / timeDiff.coerceAtLeast(1)
                                val speedFormatted = formatSpeed(speedBps)
                                val percent = if (totalBytes > 0) {
                                    ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                                } else 50

                                downloadItem = downloadItem.copy(
                                    downloadedBytes = downloadedBytes,
                                    progressPercent = percent,
                                    speedText = speedFormatted,
                                    status = DownloadItem.STATUS_DOWNLOADING
                                )
                                repository.update(downloadItem)

                                lastUpdateTime = now
                                bytesSinceLastUpdate = 0L
                            }
                            read = input.read(buffer)
                        }
                        output.flush()
                    }
                }

                // Step 4: Finished successfully
                val finalSize = finalFile.length()
                downloadItem = downloadItem.copy(
                    fileSizeBytes = finalSize,
                    downloadedBytes = finalSize,
                    progressPercent = 100,
                    speedText = "Completed",
                    status = DownloadItem.STATUS_COMPLETED,
                    errorMessage = null
                )
                repository.update(downloadItem)

            } catch (e: Exception) {
                if (e is CancellationException) {
                    downloadItem = downloadItem.copy(
                        status = DownloadItem.STATUS_CANCELLED,
                        speedText = "Cancelled"
                    )
                    targetFile?.delete()
                } else {
                    downloadItem = downloadItem.copy(
                        status = DownloadItem.STATUS_FAILED,
                        errorMessage = e.localizedMessage ?: "Download failed",
                        speedText = "Error"
                    )
                    targetFile?.delete()
                }
                repository.update(downloadItem)
            } finally {
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
        return taskId
    }

    fun cancelDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
    }

    private suspend fun updateStatus(
        item: DownloadItem,
        status: String,
        percent: Int,
        speedText: String
    ) {
        val updated = item.copy(
            status = status,
            progressPercent = percent,
            speedText = speedText
        )
        repository.update(updated)
    }

    private fun getDownloadDirectory(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val cliptubeDir = File(dir, "ClipTube")
        if (!cliptubeDir.exists()) {
            cliptubeDir.mkdirs()
        }
        return cliptubeDir
    }

    private fun sanitizeFileName(title: String, format: String): String {
        val ext = if (format.lowercase() == "mp3") "mp3" else "mp4"
        val clean = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(60)
        val finalTitle = if (clean.isEmpty()) "video_${System.currentTimeMillis()}" else clean
        return "$finalTitle.$ext"
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format("%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }
}
