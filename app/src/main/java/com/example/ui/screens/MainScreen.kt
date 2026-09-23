package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ResolutionPickerSheet
import com.example.ui.components.VideoPlayerDialog
import com.example.viewmodel.DownloadViewModel

@Composable
fun MainScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val browserUrl by viewModel.browserUrl.collectAsStateWithLifecycle()
    val detectedVideoUrl by viewModel.detectedVideoUrl.collectAsStateWithLifecycle()
    val detectedVideoTitle by viewModel.detectedVideoTitle.collectAsStateWithLifecycle()
    val detectedThumbnail by viewModel.detectedVideoThumbnail.collectAsStateWithLifecycle()
    val canGoBack by viewModel.browserCanGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.browserCanGoForward.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.browserLoadingProgress.collectAsStateWithLifecycle()

    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()

    val showResolutionPicker by viewModel.showResolutionPicker.collectAsStateWithLifecycle()
    val selectedPlaybackItem by viewModel.selectedPlaybackItem.collectAsStateWithLifecycle()
    val itemToDelete by viewModel.itemToDelete.collectAsStateWithLifecycle()
    val snackBarMessage by viewModel.snackBarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackBarMessage) {
        snackBarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackBar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_bottom_nav")
            ) {
                // Browse Tab
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.switchTab(0) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (detectedVideoUrl != null && currentTab != 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == 0) Icons.Filled.Explore else Icons.Outlined.Explore,
                                contentDescription = "YouTube Browser"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "YouTube",
                            fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_tab_browse")
                )

                // Downloads Tab
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.switchTab(1) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeDownloads.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("${activeDownloads.size}", color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                contentDescription = "Offline Downloads"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "Downloads",
                            fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_tab_downloads")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    0 -> BrowserScreen(
                        currentUrl = browserUrl,
                        detectedVideoUrl = detectedVideoUrl,
                        detectedVideoTitle = detectedVideoTitle,
                        detectedThumbnailUrl = detectedThumbnail,
                        loadingProgress = loadingProgress,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        onUrlChanged = { url, title -> viewModel.onBrowserUrlChanged(url, title) },
                        onTitleChanged = { title -> viewModel.onBrowserTitleChanged(title) },
                        onNavStateChanged = { back, forward -> viewModel.onBrowserNavState(back, forward) },
                        onLoadingProgress = { p -> viewModel.onBrowserProgress(p) },
                        onDownloadClick = { viewModel.openResolutionPicker() },
                        onNavigateUrl = { url -> viewModel.loadCustomUrl(url) }
                    )

                    1 -> DownloadsScreen(
                        activeDownloads = activeDownloads,
                        completedDownloads = completedDownloads,
                        onCancelTask = { taskId -> viewModel.cancelDownload(taskId) },
                        onPlayVideo = { item -> viewModel.playVideo(item) },
                        onConfirmDelete = { item -> viewModel.confirmDelete(item) },
                        onGoToBrowse = { viewModel.switchTab(0) },
                        itemToDelete = itemToDelete,
                        onDismissDeleteDialog = { viewModel.dismissDeleteDialog() },
                        onExecuteDelete = { viewModel.executeDelete() }
                    )
                }
            }
        }
    }

    // Resolution Picker Bottom Sheet
    if (showResolutionPicker) {
        ResolutionPickerSheet(
            title = detectedVideoTitle ?: "YouTube Video",
            thumbnailUrl = detectedThumbnail ?: "",
            videoUrl = detectedVideoUrl ?: "",
            onSelectOption = { option -> viewModel.startDownload(option) },
            onDismiss = { viewModel.closeResolutionPicker() }
        )
    }

    // Fullscreen Offline Video Player
    selectedPlaybackItem?.let { playbackItem ->
        VideoPlayerDialog(
            item = playbackItem,
            onClose = { viewModel.closePlayer() }
        )
    }
}
