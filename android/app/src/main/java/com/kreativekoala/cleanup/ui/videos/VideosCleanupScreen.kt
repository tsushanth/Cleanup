package com.kreativekoala.cleanup.ui.videos

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kreativekoala.cleanup.data.model.CompressionQuality
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.VideoAsset
import com.kreativekoala.cleanup.util.ByteFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosCleanupScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: VideosCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Large Videos", "Compress")

    // SAF folder picker launcher ("Select All")
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    // Video Picker launcher ("Choose Videos")
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onVideosSelected(uris)
    }

    // Delete consent dialog handler
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteCompleted()
        }
        viewModel.clearDeletePendingIntent()
    }

    LaunchedEffect(uiState.deleteRequestPendingIntent) {
        uiState.deleteRequestPendingIntent?.let { pi ->
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Cleanup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.hasMediaAccess) {
                        IconButton(onClick = {
                            folderPickerLauncher.launch(null)
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Change folder")
                        }
                    }
                    AnimatedVisibility(visible = viewModel.selectedCount > 0 && selectedTab == 0) {
                        TextButton(
                            onClick = { viewModel.requestDelete() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete ${viewModel.selectedCount}")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!uiState.hasMediaAccess) {
            // Media selection screen (replaces permission request)
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        null,
                        Modifier.size(64.dp),
                        MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Select Videos to Analyze",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Choose how you'd like to provide videos for large file detection and compression.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Select All (folder access)
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select All (Folder Access)")
                    }

                    Text(
                        "Grant access to your DCIM or media folder to scan all videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Choose specific videos
                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Videos")
                    }

                    Text(
                        "Pick specific videos to analyze",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Savings bar
            if (viewModel.selectedCount > 0 && selectedTab == 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${viewModel.selectedCount} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Save ${viewModel.totalSavingsFormatted}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> LargeVideosTab(
                        videos = uiState.largeVideos,
                        isScanning = uiState.isScanning,
                        totalSize = viewModel.totalSizeFormatted,
                        selectedIds = uiState.selectedVideoIds,
                        viewModel = viewModel
                    )
                    1 -> CompressTab(
                        videos = uiState.videosToCompress,
                        isScanning = uiState.isScanning,
                        selectedIds = uiState.selectedCompressionIds,
                        viewModel = viewModel
                    )
                }

                if (uiState.isScanning) {
                    ScanningOverlay(message = "Scanning videos...")
                }
                if (uiState.isCompressing) {
                    CompressionOverlay(
                        progress = uiState.compressionProgress,
                        currentVideo = uiState.currentCompressionVideo
                    )
                }
            }
        }
    }

    // Delete confirmation
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete ${viewModel.selectedCount} Videos?") },
            text = { Text("This will permanently delete the selected videos. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSelected() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) { Text("Cancel") }
            }
        )
    }

    // Paywall dialog
    if (uiState.showPaywall) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPaywall() },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text("Upgrade to Pro", textAlign = TextAlign.Center) },
            text = {
                Text(
                    "You've used all your free cleanups for this category. Upgrade to Pro for unlimited access.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPaywall()
                    onNavigateToPaywall()
                }) {
                    Text("Upgrade")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPaywall() }) {
                    Text("Not Now")
                }
            }
        )
    }
}

// MARK: - Large Videos Tab

@Composable
private fun LargeVideosTab(
    videos: List<PhotoAsset>,
    isScanning: Boolean,
    totalSize: String,
    selectedIds: Set<Long>,
    viewModel: VideosCleanupViewModel
) {
    if (videos.isEmpty() && !isScanning) {
        EmptyState(title = "No Large Videos", subtitle = "No videos over 50MB found")
    } else {
        var selectAll by remember { mutableStateOf(false) }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary header with Select All
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${videos.size} large videos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        selectAll = !selectAll
                        if (selectAll) viewModel.selectAllVideos()
                        else viewModel.deselectAllVideos()
                    }) {
                        Text(if (selectAll) "Deselect All" else "Select All")
                    }
                }
            }

            items(videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    isSelected = video.id in selectedIds,
                    onClick = { viewModel.toggleSelection(video) }
                )
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: PhotoAsset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .size(160, 120)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Video icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    ByteFormatter.format(video.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium
                )
            }

            // Selection indicator
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// MARK: - Compress Tab

@Composable
private fun CompressTab(
    videos: List<VideoAsset>,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    viewModel: VideosCleanupViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCount = selectedIds.size

    if (videos.isEmpty() && !isScanning) {
        EmptyState(title = "No Videos", subtitle = "No compressible videos found")
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp,
                    bottom = if (selectedCount > 0) 80.dp else 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quality selector
                item {
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Compression Quality",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            CompressionQuality.entries.forEach { quality ->
                                val isSelected = uiState.selectedQuality == quality
                                QualityOption(
                                    quality = quality,
                                    isSelected = isSelected,
                                    onClick = { viewModel.setQuality(quality) }
                                )
                                if (quality != CompressionQuality.LOW) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // Videos list
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Select Videos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "$selectedCount selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(videos, key = { it.id }) { video ->
                    CompressVideoRow(
                        video = video,
                        isSelected = video.id in selectedIds,
                        estimatedSavings = viewModel.estimatedSavings(video),
                        estimatedSize = viewModel.estimatedCompressedSize(video),
                        onClick = { viewModel.toggleCompressionSelection(video) }
                    )
                }
            }

            // Floating compress button at bottom
            AnimatedVisibility(
                visible = selectedCount > 0,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Button(
                    onClick = { viewModel.compressSelected() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compress $selectedCount Videos")
                }
            }
        }
    }
}

@Composable
private fun QualityOption(
    quality: CompressionQuality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(quality.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${quality.resolution}p - ${quality.estimatedReduction}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompressVideoRow(
    video: VideoAsset,
    isSelected: Boolean,
    estimatedSavings: String,
    estimatedSize: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(video.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ByteFormatter.format(video.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(horizontal = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        estimatedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Overlays

@Composable
private fun ScanningOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CompressionOverlay(progress: Float, currentVideo: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Compressing Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(currentVideo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(200.dp)
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
