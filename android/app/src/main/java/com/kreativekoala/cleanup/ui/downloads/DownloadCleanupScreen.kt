package com.kreativekoala.cleanup.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.domain.service.DownloadCleanupService
import com.kreativekoala.cleanup.util.ByteFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCleanupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DownloadCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.scan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.scanResult != null) {
                        TextButton(onClick = {
                            if (uiState.selectedFiles.isEmpty()) viewModel.selectAll()
                            else viewModel.deselectAll()
                        }) {
                            Text(if (uiState.selectedFiles.isEmpty()) "Select All" else "Deselect")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.selectedFiles.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { viewModel.deleteSelected() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        enabled = !uiState.isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (uiState.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete ${uiState.selectedFiles.size} files")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            uiState.isScanning -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning downloads folder...")
                    }
                }
            }
            uiState.scanResult != null && uiState.scanResult!!.allFiles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Downloads folder is clean!", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            uiState.scanResult != null -> {
                val result = uiState.scanResult!!

                Column(Modifier.fillMaxSize().padding(padding)) {
                    // Tab row
                    TabRow(
                        selectedTabIndex = DownloadCleanupViewModel.Tab.entries.indexOf(uiState.activeTab)
                    ) {
                        DownloadCleanupViewModel.Tab.entries.forEach { tab ->
                            val count = when (tab) {
                                DownloadCleanupViewModel.Tab.ALL -> result.allFiles.size
                                DownloadCleanupViewModel.Tab.LARGE -> result.largeFiles.size
                                DownloadCleanupViewModel.Tab.OLD -> result.oldFiles.size
                            }
                            Tab(
                                selected = uiState.activeTab == tab,
                                onClick = { viewModel.setTab(tab) },
                                text = { Text("${tab.label} ($count)") }
                            )
                        }
                    }

                    // Summary
                    val displayedFiles = viewModel.displayedFiles
                    val totalSize = displayedFiles.sumOf { it.size }
                    Text(
                        "${displayedFiles.size} files (${ByteFormatter.format(totalSize)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayedFiles, key = { it.id }) { file ->
                            DownloadFileItem(
                                file = file,
                                isSelected = file.id in uiState.selectedFiles,
                                onToggle = { viewModel.toggleFile(file.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFileItem(
    file: DownloadCleanupService.DownloadFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    ListItem(
        headlineContent = { Text(file.name, maxLines = 1) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ByteFormatter.format(file.size), style = MaterialTheme.typography.bodySmall)
                Text(
                    dateFormat.format(Date(file.dateAdded)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        },
        trailingContent = {
            Icon(
                when (file.category) {
                    DownloadCleanupService.DownloadCategory.LARGE -> Icons.Default.Storage
                    DownloadCleanupService.DownloadCategory.OLD -> Icons.Default.History
                    DownloadCleanupService.DownloadCategory.DOCUMENTS -> Icons.Default.Description
                    DownloadCleanupService.DownloadCategory.IMAGES -> Icons.Default.Image
                    DownloadCleanupService.DownloadCategory.VIDEOS -> Icons.Default.VideoFile
                    DownloadCleanupService.DownloadCategory.ARCHIVES -> Icons.Default.FolderZip
                    DownloadCleanupService.DownloadCategory.OTHER -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
