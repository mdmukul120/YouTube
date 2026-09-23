package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val title: String,
    val youtubeUrl: String,
    val format: String,
    val resolutionLabel: String,
    val thumbnailUrl: String = "",
    val filePath: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedText: String = "",
    val status: String = STATUS_PREPARING, // PREPARING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }

    val isFinished: Boolean
        get() = status == STATUS_COMPLETED || status == STATUS_FAILED || status == STATUS_CANCELLED
}
