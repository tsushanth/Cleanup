package com.kreativekoala.cleanup.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToPhotos: (tab: Int) -> Unit = {},
    onNavigateToVideos: (tab: Int) -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToApkCleanup: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // SAF folder picker for Smart Scan
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToVault) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.home_vault_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.isScanning) return@ExtendedFloatingActionButton
                    if (uiState.hasMediaAccess) {
                        viewModel.performOneTapCleanup()
                    } else {
                        folderPickerLauncher.launch(null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.home_scanning))
                } else {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.hasMediaAccess) stringResource(R.string.home_one_tap_scan) else stringResource(R.string.home_grant_access_scan))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Overview Card
            item {
                StorageOverviewCard(
                    usedFormatted = uiState.storageUsedFormatted,
                    totalFormatted = uiState.storageTotalFormatted,
                    usedPercentage = uiState.storageUsedPercentage
                )
            }

            // Scanning progress
            if (uiState.isScanning) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.home_scanning_device),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.scanProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Text(stringResource(R.string.home_quick_actions), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionChip(stringResource(R.string.home_chip_duplicates), Icons.Default.FileCopy) { onNavigateToPhotos(0) }
                    QuickActionChip(stringResource(R.string.home_chip_screenshots), Icons.Default.Screenshot) { onNavigateToPhotos(2) }
                    QuickActionChip(stringResource(R.string.home_chip_large_videos), Icons.Default.VideoLibrary) { onNavigateToVideos(0) }
                    QuickActionChip(stringResource(R.string.home_chip_compress), Icons.Default.Compress) { onNavigateToVideos(1) }
                    QuickActionChip(stringResource(R.string.home_chip_archive), Icons.Default.Cloud, onNavigateToArchive)
                    QuickActionChip(stringResource(R.string.home_chip_downloads), Icons.Default.Download, onNavigateToDownloads)
                    QuickActionChip(stringResource(R.string.home_chip_apk_files), Icons.Default.Android, onNavigateToApkCleanup)
                }
            }

            // Cleanup Categories
            item {
                Text(stringResource(R.string.home_smart_scan_results), style = MaterialTheme.typography.titleMedium)
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_duplicate_photos),
                    count = uiState.duplicatePhotosCount,
                    savings = uiState.duplicatePhotosSavings,
                    icon = Icons.Default.FileCopy,
                    onClick = { onNavigateToPhotos(0) }
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_similar_photos),
                    count = uiState.similarPhotosCount,
                    savings = uiState.similarPhotosSavings,
                    icon = Icons.Default.PhotoLibrary,
                    onClick = { onNavigateToPhotos(1) }
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_screenshots),
                    count = uiState.screenshotsCount,
                    savings = uiState.screenshotsSavings,
                    icon = Icons.Default.Screenshot,
                    onClick = { onNavigateToPhotos(2) }
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_large_videos),
                    count = uiState.largeVideosCount,
                    savings = uiState.largeVideosSavings,
                    icon = Icons.Default.VideoLibrary,
                    onClick = { onNavigateToVideos(0) }
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_duplicate_contacts),
                    count = uiState.duplicateContactsCount,
                    savings = "",
                    icon = Icons.Default.Contacts,
                    onClick = onNavigateToContacts
                )
            }

            // File Management
            item {
                Text(stringResource(R.string.home_file_management), style = MaterialTheme.typography.titleMedium)
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_downloads),
                    count = 0,
                    savings = "",
                    icon = Icons.Default.Download,
                    onClick = onNavigateToDownloads
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_apk_files),
                    count = 0,
                    savings = "",
                    icon = Icons.Default.Android,
                    onClick = onNavigateToApkCleanup
                )
            }

            item {
                CleanupCategoryCard(
                    title = stringResource(R.string.home_category_notification_manager),
                    count = 0,
                    savings = "",
                    icon = Icons.Default.Notifications,
                    onClick = onNavigateToNotifications
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    usedFormatted: String,
    totalFormatted: String,
    usedPercentage: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.home_storage_used, usedFormatted), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_storage_total, totalFormatted), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))

            val progressColor = when {
                usedPercentage > 0.9 -> Color.Red
                usedPercentage > 0.75 -> Color(0xFFFF9800)
                else -> MaterialTheme.colorScheme.primary
            }
            LinearProgressIndicator(
                progress = { usedPercentage.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (usedPercentage > 0.7) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_storage_full, (usedPercentage * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun CleanupCategoryCard(
    title: String,
    count: Int,
    savings: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (count > 0) {
                    Text(
                        if (savings.isNotEmpty()) stringResource(R.string.home_items_count_savings, count, savings)
                        else stringResource(R.string.home_items_count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stringResource(R.string.home_tap_to_scan), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
