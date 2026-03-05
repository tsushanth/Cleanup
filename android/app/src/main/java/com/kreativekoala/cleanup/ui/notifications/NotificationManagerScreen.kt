package com.kreativekoala.cleanup.ui.notifications

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.domain.service.NotificationManagementService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: NotificationManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.scan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isScanning -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analyzing notifications...")
                    }
                }
            }
            uiState.apps.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No apps found", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Notifications,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "${uiState.apps.size} apps with notifications",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Tap to manage notification settings for each app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    items(uiState.apps, key = { it.packageName }) { app ->
                        NotificationAppItem(
                            app = app,
                            onManage = {
                                context.startActivity(
                                    viewModel.getAppNotificationSettingsIntent(app.packageName)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationAppItem(
    app: NotificationManagementService.AppNotificationInfo,
    onManage: () -> Unit
) {
    ListItem(
        headlineContent = { Text(app.appName, maxLines = 1) },
        supportingContent = {
            Text(
                if (app.isNotificationEnabled) "Notifications enabled" else "Notifications disabled",
                style = MaterialTheme.typography.bodySmall,
                color = if (app.isNotificationEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        },
        leadingContent = {
            NotificationAppIcon(app.appIcon, Modifier.size(40.dp))
        },
        trailingContent = {
            FilledTonalButton(onClick = onManage) {
                Text("Manage")
            }
        }
    )
}

@Composable
private fun NotificationAppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        Image(
            bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(Icons.Default.Notifications, contentDescription = null, modifier = modifier)
    }
}
