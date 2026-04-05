package com.kreativekoala.cleanup.ui.apk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.R
import com.kreativekoala.cleanup.domain.service.ApkCleanupService
import com.kreativekoala.cleanup.util.ByteFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkCleanupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ApkCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.scan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apk_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    if (uiState.apkFiles.isNotEmpty()) {
                        TextButton(onClick = {
                            if (uiState.selectedFiles.isEmpty()) viewModel.selectAll()
                            else viewModel.deselectAll()
                        }) {
                            Text(if (uiState.selectedFiles.isEmpty()) stringResource(R.string.photos_select_all) else stringResource(R.string.apk_deselect))
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
                            Text(stringResource(R.string.apk_delete_n, uiState.selectedFiles.size))
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
                        Text(stringResource(R.string.apk_scanning))
                    }
                }
            }
            uiState.apkFiles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.apk_none_found), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // Filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.filterStatus == null,
                                onClick = { viewModel.setFilter(null) },
                                label = { Text(stringResource(R.string.apk_all_filter, uiState.apkFiles.size)) }
                            )
                        }
                        for (status in ApkCleanupService.ApkStatus.entries) {
                            val count = uiState.apkFiles.count { it.status == status }
                            if (count > 0) {
                                item {
                                    FilterChip(
                                        selected = uiState.filterStatus == status,
                                        onClick = { viewModel.setFilter(status) },
                                        label = { Text("${status.displayName} ($count)") }
                                    )
                                }
                            }
                        }
                    }

                    // Total size
                    val totalSize = viewModel.filteredFiles.sumOf { it.size }
                    Text(
                        stringResource(R.string.downloads_summary, viewModel.filteredFiles.size, ByteFormatter.format(totalSize)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(viewModel.filteredFiles, key = { it.path }) { apk ->
                            ApkFileItem(
                                apk = apk,
                                isSelected = apk.path in uiState.selectedFiles,
                                onToggle = { viewModel.toggleFile(apk.path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkFileItem(
    apk: ApkCleanupService.ApkFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(apk.name, maxLines = 1) },
        supportingContent = {
            Column {
                Text(
                    ByteFormatter.format(apk.size),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    apk.status.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (apk.status) {
                        ApkCleanupService.ApkStatus.ALREADY_INSTALLED -> Color(0xFF4CAF50)
                        ApkCleanupService.ApkStatus.OUTDATED -> Color(0xFFFF9800)
                        ApkCleanupService.ApkStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant
                        ApkCleanupService.ApkStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
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
                when (apk.status) {
                    ApkCleanupService.ApkStatus.ALREADY_INSTALLED -> Icons.Default.CheckCircle
                    ApkCleanupService.ApkStatus.OUTDATED -> Icons.Default.Update
                    ApkCleanupService.ApkStatus.NOT_INSTALLED -> Icons.Default.Android
                    ApkCleanupService.ApkStatus.UNKNOWN -> Icons.Default.Help
                },
                contentDescription = null,
                tint = when (apk.status) {
                    ApkCleanupService.ApkStatus.ALREADY_INSTALLED -> Color(0xFF4CAF50)
                    ApkCleanupService.ApkStatus.OUTDATED -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    )
}
