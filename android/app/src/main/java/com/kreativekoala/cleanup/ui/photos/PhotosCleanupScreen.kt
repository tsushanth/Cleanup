package com.kreativekoala.cleanup.ui.photos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kreativekoala.cleanup.R
import com.kreativekoala.cleanup.data.model.DuplicateGroup
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.SimilarPhotoGroup
import com.kreativekoala.cleanup.util.ByteFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosCleanupScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: PhotosCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        stringResource(R.string.photos_tab_duplicates),
        stringResource(R.string.photos_tab_similar),
        stringResource(R.string.photos_tab_screenshots)
    )

    // SAF folder picker launcher ("Select All")
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    // Photo Picker launcher ("Choose Photos")
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onPhotosSelected(uris)
    }

    // Handle delete request PendingIntent (API 30+ system consent dialog)
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteCompleted()
        }
        viewModel.clearDeletePendingIntent()
    }

    // Launch delete consent dialog when PendingIntent is available
    LaunchedEffect(uiState.deleteRequestPendingIntent) {
        uiState.deleteRequestPendingIntent?.let { pendingIntent ->
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            deleteLauncher.launch(request)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photos_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    // Re-pick photos button
                    if (uiState.hasMediaAccess) {
                        IconButton(onClick = {
                            folderPickerLauncher.launch(null)
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.photos_change_folder_cd))
                        }
                    }
                    AnimatedVisibility(visible = viewModel.selectedCount > 0) {
                        Row {
                            TextButton(
                                onClick = { viewModel.requestDelete(selectedTab) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.photos_delete_count, viewModel.selectedCount))
                            }
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
                        Icons.Default.PhotoLibrary,
                        null,
                        Modifier.size(64.dp),
                        MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.photos_select_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.photos_select_subtitle),
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
                        Text(stringResource(R.string.photos_select_all_folder))
                    }

                    Text(
                        stringResource(R.string.photos_select_all_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Choose specific photos
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.photos_choose))
                    }

                    Text(
                        stringResource(R.string.photos_choose_hint),
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
            if (viewModel.selectedCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.photos_selected_count, viewModel.selectedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.photos_save_amount, viewModel.totalSavingsFormatted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DuplicatesTab(
                        groups = uiState.duplicateGroups,
                        isScanning = uiState.isScanning,
                        selectedIds = uiState.selectedAssets,
                        viewModel = viewModel
                    )
                    1 -> SimilarPhotosTab(
                        groups = uiState.similarGroups,
                        isScanning = uiState.isScanning,
                        selectedIds = uiState.selectedAssets,
                        viewModel = viewModel
                    )
                    2 -> ScreenshotsTab(
                        screenshots = uiState.screenshots,
                        isScanning = uiState.isScanning,
                        selectedIds = uiState.selectedAssets,
                        viewModel = viewModel
                    )
                }

                // Scanning overlay
                if (uiState.isScanning) {
                    ScanningOverlay(progress = uiState.scanProgress)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text(stringResource(R.string.photos_delete_title, viewModel.selectedCount)) },
            text = {
                Text(stringResource(R.string.photos_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSelected() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.photos_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Paywall dialog
    if (uiState.showPaywall) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPaywall() },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text(stringResource(R.string.upgrade_title), textAlign = TextAlign.Center) },
            text = {
                Text(
                    stringResource(R.string.upgrade_message),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPaywall()
                    onNavigateToPaywall()
                }) {
                    Text(stringResource(R.string.upgrade_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPaywall() }) {
                    Text(stringResource(R.string.upgrade_not_now))
                }
            }
        )
    }
}

// MARK: - Duplicates Tab

@Composable
private fun DuplicatesTab(
    groups: List<DuplicateGroup>,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    viewModel: PhotosCleanupViewModel
) {
    if (groups.isEmpty() && !isScanning) {
        EmptyState(
            icon = Icons.Default.CheckCircle,
            title = stringResource(R.string.photos_no_duplicates_title),
            subtitle = stringResource(R.string.photos_no_duplicates_subtitle)
        )
    } else {
        var selectAll by remember { mutableStateOf(false) }

        Column {
            // Select All header for duplicates
            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.photos_duplicates_summary, groups.sumOf { it.duplicateCount }, groups.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        selectAll = !selectAll
                        if (selectAll) viewModel.selectAllDuplicates()
                        else viewModel.deselectAllDuplicates()
                    }) {
                        Text(if (selectAll) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(groups, key = { it.hash }) { group ->
                    DuplicateGroupCard(group = group, selectedIds = selectedIds, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedIds: Set<Long>,
    viewModel: PhotosCleanupViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.photos_n_duplicates, group.photos.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ByteFormatter.format(group.potentialSavings),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo thumbnails (horizontal scroll)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.photos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        isSelected = photo.id in selectedIds,
                        isBest = photo.id == group.bestPhoto.id,
                        onClick = { viewModel.toggleSelection(photo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { viewModel.selectAllExceptBest(group) }) {
                    Text(stringResource(R.string.photos_keep_best))
                }
                OutlinedButton(onClick = { viewModel.selectAll(group) }) {
                    Text(stringResource(R.string.photos_select_all))
                }
            }
        }
    }
}

// MARK: - Similar Photos Tab

@Composable
private fun SimilarPhotosTab(
    groups: List<SimilarPhotoGroup>,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    viewModel: PhotosCleanupViewModel
) {
    if (groups.isEmpty() && !isScanning) {
        EmptyState(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.photos_no_similar_title),
            subtitle = stringResource(R.string.photos_no_similar_subtitle)
        )
    } else {
        var selectAll by remember { mutableStateOf(false) }

        Column {
            // Select All header for similar photos
            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.photos_similar_summary, groups.sumOf { it.similarCount }, groups.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        selectAll = !selectAll
                        if (selectAll) viewModel.selectAllSimilar()
                        else viewModel.deselectAllSimilar()
                    }) {
                        Text(if (selectAll) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(groups, key = { it.bestPhoto.id }) { group ->
                    SimilarGroupCard(group = group, selectedIds = selectedIds, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun SimilarGroupCard(
    group: SimilarPhotoGroup,
    selectedIds: Set<Long>,
    viewModel: PhotosCleanupViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.photos_n_similar, group.photos.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.photos_percent_similar, (group.similarityScore * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo thumbnails
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.photos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        isSelected = photo.id in selectedIds,
                        isBest = photo.id == group.bestPhoto.id,
                        onClick = { viewModel.toggleSelection(photo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button
            OutlinedButton(
                onClick = { viewModel.selectSuggestedToDelete(group) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.photos_keep_best_select_others))
            }
        }
    }
}

// MARK: - Screenshots Tab

@Composable
private fun ScreenshotsTab(
    screenshots: List<PhotoAsset>,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    viewModel: PhotosCleanupViewModel
) {
    if (screenshots.isEmpty() && !isScanning) {
        EmptyState(
            icon = Icons.Default.Screenshot,
            title = stringResource(R.string.photos_no_screenshots_title),
            subtitle = stringResource(R.string.photos_no_screenshots_subtitle)
        )
    } else {
        var selectAll by remember { mutableStateOf(false) }

        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.photos_n_screenshots, screenshots.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    selectAll = !selectAll
                    if (selectAll) viewModel.selectAllScreenshots()
                    else viewModel.deselectAllScreenshots()
                }) {
                    Text(if (selectAll) stringResource(R.string.photos_deselect_all) else stringResource(R.string.photos_select_all))
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(screenshots, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        isSelected = photo.id in selectedIds,
                        isBest = false,
                        size = 0, // Fill available space
                        onClick = { viewModel.toggleSelection(photo) }
                    )
                }
            }
        }
    }
}

// MARK: - Photo Thumbnail

@Composable
private fun PhotoThumbnail(
    photo: PhotoAsset,
    isSelected: Boolean,
    isBest: Boolean,
    size: Int = 100,
    onClick: () -> Unit
) {
    val modifier = if (size > 0) {
        Modifier.size(size.dp)
    } else {
        Modifier.aspectRatio(1f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .size(200)
                .crossfade(true)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.photos_selected_cd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // "BEST" badge
        if (isBest) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.photos_best_badge),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// MARK: - Empty State

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Scanning Overlay

@Composable
private fun ScanningOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()

                Text(
                    stringResource(R.string.photos_scanning),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.width(200.dp),
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
