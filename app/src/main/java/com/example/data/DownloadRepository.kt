package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadRepository(private val dao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = dao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadItem>> = dao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadItem>> = dao.getActiveDownloads()

    suspend fun getById(id: Long): DownloadItem? = dao.getById(id)

    suspend fun getByTaskId(taskId: String): DownloadItem? = dao.getByTaskId(taskId)

    suspend fun insert(item: DownloadItem): Long = dao.insert(item)

    suspend fun update(item: DownloadItem) = dao.update(item)

    suspend fun delete(item: DownloadItem) {
        if (item.filePath.isNotEmpty()) {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        dao.delete(item)
    }

    suspend fun deleteById(id: Long) {
        val item = dao.getById(id)
        if (item != null) {
            delete(item)
        }
    }
}
