package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.data.ResolutionOption
import com.example.network.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository
    private val downloadManager: DownloadManager

    init {
        val database = AppDatabase.getInstance(application)
        repository = DownloadRepository(database.downloadDao())
        downloadManager = DownloadManager(application, repository)
    }

    val allDownloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadItem>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> = repository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTab = MutableStateFlow(0) // 0 = Browse, 1 = Downloads
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _detectedVideoUrl = MutableStateFlow<String?>(null)
    val detectedVideoUrl: StateFlow<String?> = _detectedVideoUrl.asStateFlow()

    private val _detectedVideoTitle = MutableStateFlow<String?>("YouTube Video")
    val detectedVideoTitle: StateFlow<String?> = _detectedVideoTitle.asStateFlow()

    private val _detectedVideoThumbnail = MutableStateFlow<String?>("")
    val detectedVideoThumbnail: StateFlow<String?> = _detectedVideoThumbnail.asStateFlow()

    private val _browserUrl = MutableStateFlow("https://m.youtube.com")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _browserCanGoBack = MutableStateFlow(false)
    val browserCanGoBack: StateFlow<Boolean> = _browserCanGoBack.asStateFlow()

    private val _browserCanGoForward = MutableStateFlow(false)
    val browserCanGoForward: StateFlow<Boolean> = _browserCanGoForward.asStateFlow()

    private val _browserLoadingProgress = MutableStateFlow(0)
    val browserLoadingProgress: StateFlow<Int> = _browserLoadingProgress.asStateFlow()

    private val _showResolutionPicker = MutableStateFlow(false)
    val showResolutionPicker: StateFlow<Boolean> = _showResolutionPicker.asStateFlow()

    private val _selectedPlaybackItem = MutableStateFlow<DownloadItem?>(null)
    val selectedPlaybackItem: StateFlow<DownloadItem?> = _selectedPlaybackItem.asStateFlow()

    private val _itemToDelete = MutableStateFlow<DownloadItem?>(null)
    val itemToDelete: StateFlow<DownloadItem?> = _itemToDelete.asStateFlow()

    private val _snackBarMessage = MutableStateFlow<String?>(null)
    val snackBarMessage: StateFlow<String?> = _snackBarMessage.asStateFlow()

    fun switchTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun onBrowserUrlChanged(url: String, title: String? = null) {
        _browserUrl.value = url

        // Check if the URL is a YouTube watch / short / embed URL
        if (isYouTubeWatchUrl(url)) {
            val canonicalUrl = normalizeYouTubeUrl(url)
            _detectedVideoUrl.value = canonicalUrl
            val videoId = extractYouTubeVideoId(url)
            if (videoId != null) {
                _detectedVideoThumbnail.value = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            if (!title.isNullOrEmpty() && !title.contains("YouTube", ignoreCase = true)) {
                _detectedVideoTitle.value = title
            } else if (title?.contains("- YouTube") == true) {
                _detectedVideoTitle.value = title.replace("- YouTube", "").trim()
            }
        } else {
            // Keep previous detected video or hide if desired, but if navigating home, clear
            if (url.trimEnd('/') == "https://m.youtube.com" || url.trimEnd('/') == "https://www.youtube.com") {
                _detectedVideoUrl.value = null
            }
        }
    }

    fun onBrowserTitleChanged(title: String) {
        if (!title.isNullOrEmpty() && !title.equals("YouTube", ignoreCase = true)) {
            val cleaned = title.replace("- YouTube", "").replace(" - YouTube", "").trim()
            if (cleaned.isNotEmpty()) {
                _detectedVideoTitle.value = cleaned
            }
        }
    }

    fun onBrowserNavState(canGoBack: Boolean, canGoForward: Boolean) {
        _browserCanGoBack.value = canGoBack
        _browserCanGoForward.value = canGoForward
    }

    fun onBrowserProgress(progress: Int) {
        _browserLoadingProgress.value = progress
    }

    fun openResolutionPicker(customUrl: String? = null) {
        if (!customUrl.isNullOrEmpty()) {
            _detectedVideoUrl.value = normalizeYouTubeUrl(customUrl)
            val videoId = extractYouTubeVideoId(customUrl)
            if (videoId != null) {
                _detectedVideoThumbnail.value = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
        }
        if (_detectedVideoUrl.value != null) {
            _showResolutionPicker.value = true
        } else {
            _snackBarMessage.value = "Please open or paste a YouTube video first"
        }
    }

    fun closeResolutionPicker() {
        _showResolutionPicker.value = false
    }

    fun startDownload(option: ResolutionOption) {
        val targetUrl = _detectedVideoUrl.value ?: return
        val currentTitle = _detectedVideoTitle.value ?: "YouTube Video"
        val thumb = _detectedVideoThumbnail.value ?: ""

        closeResolutionPicker()

        downloadManager.startDownload(
            scope = viewModelScope,
            youtubeUrl = targetUrl,
            format = option.format,
            resolutionLabel = option.label,
            initialTitle = currentTitle,
            initialThumbnail = thumb
        )

        _snackBarMessage.value = "Download started: ${option.label}"
        // Switch to downloads tab so user immediately sees their download in progress
        _currentTab.value = 1
    }

    fun cancelDownload(taskId: String) {
        downloadManager.cancelDownload(taskId)
    }

    fun confirmDelete(item: DownloadItem) {
        _itemToDelete.value = item
    }

    fun dismissDeleteDialog() {
        _itemToDelete.value = null
    }

    fun executeDelete() {
        val item = _itemToDelete.value ?: return
        viewModelScope.launch {
            repository.delete(item)
            _itemToDelete.value = null
            _snackBarMessage.value = "Video deleted"
        }
    }

    fun playVideo(item: DownloadItem) {
        _selectedPlaybackItem.value = item
    }

    fun closePlayer() {
        _selectedPlaybackItem.value = null
    }

    fun clearSnackBar() {
        _snackBarMessage.value = null
    }

    fun loadCustomUrl(url: String) {
        val formatted = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains("youtube.com") || url.contains("youtu.be") -> "https://$url"
            else -> "https://m.youtube.com/results?search_query=${Uri.encode(url)}"
        }
        _browserUrl.value = formatted
    }

    private fun isYouTubeWatchUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
                url.contains("youtu.be/") ||
                url.contains("youtube.com/shorts/") ||
                url.contains("youtube.com/embed/")
    }

    private fun normalizeYouTubeUrl(url: String): String {
        val videoId = extractYouTubeVideoId(url)
        return if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            url
        }
    }

    private fun extractYouTubeVideoId(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            when {
                url.contains("youtu.be/") -> {
                    uri.lastPathSegment
                }
                url.contains("youtube.com/shorts/") -> {
                    uri.lastPathSegment
                }
                url.contains("youtube.com/embed/") -> {
                    uri.lastPathSegment
                }
                else -> {
                    uri.getQueryParameter("v")
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
