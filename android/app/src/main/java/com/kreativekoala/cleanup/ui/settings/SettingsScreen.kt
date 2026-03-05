package com.kreativekoala.cleanup.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToVault: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    onNavigateToEmailCleanup: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text("Account", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsRow("Sign In", Icons.Default.AccountCircle, onClick = onNavigateToLogin)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Subscription", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsRow("Upgrade to Pro", Icons.Default.Star, onClick = onNavigateToPaywall)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Features", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item { SettingsRow("Secret Space", Icons.Default.Lock, onClick = onNavigateToVault) }
            item { SettingsRow("Email Cleanup", Icons.Default.Email, onClick = onNavigateToEmailCleanup) }
            item { SettingsRow("Widgets", Icons.Default.Widgets, onClick = onNavigateToWidgets) }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("About", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item { SettingsRow("Privacy Policy", Icons.Default.PrivacyTip) { } }
            item { SettingsRow("Terms of Service", Icons.Default.Description) { } }
            item { SettingsRow("Rate Us", Icons.Default.ThumbUp) { } }
            item {
                Text(
                    "Version 1.1 (2)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, icon: ImageVector, onClick: () -> Unit = {}) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
