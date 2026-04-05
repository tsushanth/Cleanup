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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kreativekoala.cleanup.R

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
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsRow(stringResource(R.string.settings_sign_in), Icons.Default.AccountCircle, onClick = onNavigateToLogin)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.settings_subscription), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsRow(stringResource(R.string.settings_upgrade_pro), Icons.Default.Star, onClick = onNavigateToPaywall)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.settings_features), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item { SettingsRow(stringResource(R.string.settings_secret_space), Icons.Default.Lock, onClick = onNavigateToVault) }
            item { SettingsRow(stringResource(R.string.settings_email_cleanup), Icons.Default.Email, onClick = onNavigateToEmailCleanup) }
            item { SettingsRow(stringResource(R.string.settings_widgets), Icons.Default.Widgets, onClick = onNavigateToWidgets) }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item { SettingsRow(stringResource(R.string.settings_privacy_policy), Icons.Default.PrivacyTip) { } }
            item { SettingsRow(stringResource(R.string.settings_terms), Icons.Default.Description) { } }
            item { SettingsRow(stringResource(R.string.settings_rate_us), Icons.Default.ThumbUp) { } }
            item {
                Text(
                    stringResource(R.string.settings_version),
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
