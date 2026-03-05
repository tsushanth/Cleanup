package com.kreativekoala.cleanup.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kreativekoala.cleanup.data.model.ArchiveQuota
import com.kreativekoala.cleanup.data.model.ArchivedItem
import com.kreativekoala.cleanup.util.ByteFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allItems by viewModel.items.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()

    val filteredItems = viewModel.filteredItems(allItems)

    // Show sign in if not authenticated
    if (!isAuthenticated) {
        ArchiveSignInScreen(
            onNavigateBack = onNavigateBack,
            onSignIn = { token -> viewModel.signIn(token) },
            isLoading = uiState.isLoading,
            error = uiState.errorMessage
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Archive") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncWithServer() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quota bar
            uiState.quota?.let { quota ->
                ArchiveQuotaBar(
                    quota = quota,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Filter chips
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ArchiveViewModel.ArchiveFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ArchiveViewModel.ArchiveFilter.entries.size
                        )
                    ) {
                        Text(filter.displayName)
                    }
                }
            }

            if (filteredItems.isEmpty() && !uiState.isLoading) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "No archived items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Archive photos and videos from the cleanup screens to free up device space",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Items grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ArchiveItemThumbnail(
                            item = item,
                            onClick = { viewModel.selectItem(item) }
                        )
                    }
                }
            }

            // Loading
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }

    // Item detail sheet
    uiState.selectedItem?.let { item ->
        ArchiveItemDetailSheet(
            item = item,
            onDismiss = { viewModel.clearSelectedItem() },
            onRetrieve = { viewModel.initiateRetrieval(item) },
            onDelete = { viewModel.showDeleteConfirmation() }
        )
    }

    // Delete confirmation
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Archived Item") },
            text = { Text("This will permanently remove this item from your archive. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelectedItem() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success dialogs
    if (uiState.showArchiveSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Archived Successfully") },
            text = { Text("${uiState.archiveSuccessCount} item(s) have been archived to the cloud.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccess() }) { Text("OK") }
            }
        )
    }

    if (uiState.showRestoreSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Restored Successfully") },
            text = { Text("Item has been restored to your device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccess() }) { Text("OK") }
            }
        )
    }

    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}

// MARK: - Quota Bar

@Composable
private fun ArchiveQuotaBar(quota: ArchiveQuota, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Storage Used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${ByteFormatter.format(quota.usedBytes)} / ${ByteFormatter.format(quota.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { quota.usedPercentage.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    quota.usedPercentage > 0.9 -> MaterialTheme.colorScheme.error
                    quota.usedPercentage > 0.7 -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "${quota.itemCount} items archived",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Item Thumbnail

@Composable
private fun ArchiveItemThumbnail(item: ArchivedItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        if (item.thumbnailPath != null) {
            AsyncImage(
                model = File(item.thumbnailPath),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (item.fileType) {
                        "VIDEO" -> Icons.Default.Videocam
                        "CONTACT" -> Icons.Default.Person
                        else -> Icons.Default.Photo
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Status badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            val badgeColor = when (item.transferStatus) {
                "archived" -> MaterialTheme.colorScheme.primary
                "retrieving" -> Color(0xFFFF9800)
                "available" -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(shape = CircleShape, color = badgeColor) {
                Icon(
                    when (item.transferStatus) {
                        "archived" -> Icons.Default.Cloud
                        "retrieving" -> Icons.Default.HourglassBottom
                        "available" -> Icons.Default.CloudDownload
                        else -> Icons.Default.CloudUpload
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(2.dp)
                )
            }
        }

        if (item.fileType == "VIDEO") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Item Detail Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveItemDetailSheet(
    item: ArchivedItem,
    onDismiss: () -> Unit,
    onRetrieve: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnailPath != null) {
                    AsyncImage(
                        model = File(item.thumbnailPath),
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        when (item.fileType) {
                            "VIDEO" -> Icons.Default.Videocam
                            "CONTACT" -> Icons.Default.Person
                            else -> Icons.Default.Photo
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                item.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Archived: ${dateFormat.format(Date(item.archivedDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Size: ${ByteFormatter.format(item.fileSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Tier: ${item.storageTier}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onRetrieve,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retrieve")
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
