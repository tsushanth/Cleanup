package com.kreativekoala.cleanup.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.billing.PaywallContext
import com.kreativekoala.cleanup.ui.home.HomeScreen
import com.kreativekoala.cleanup.ui.photos.PhotosCleanupScreen
import com.kreativekoala.cleanup.ui.videos.VideosCleanupScreen
import com.kreativekoala.cleanup.ui.contacts.ContactsCleanupScreen
import com.kreativekoala.cleanup.ui.settings.SettingsScreen
import com.kreativekoala.cleanup.ui.onboarding.OnboardingScreen
import com.kreativekoala.cleanup.ui.vault.VaultScreen
import com.kreativekoala.cleanup.ui.archive.ArchiveScreen
import com.kreativekoala.cleanup.ui.apk.ApkCleanupScreen
import com.kreativekoala.cleanup.ui.downloads.DownloadCleanupScreen
import com.kreativekoala.cleanup.ui.notifications.NotificationManagerScreen
import com.kreativekoala.cleanup.ui.paywall.PaywallScreen
import com.kreativekoala.cleanup.ui.paywall.PaywallViewModel

@Composable
fun CleanupNavGraph(
    navController: NavHostController,
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = onOnboardingComplete)
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPhotos = { tab -> navController.navigate("photos?tab=$tab") },
                onNavigateToVideos = { tab -> navController.navigate("videos?tab=$tab") },
                onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                onNavigateToVault = { navController.navigate(Screen.Vault.route) },
                onNavigateToArchive = { navController.navigate(Screen.Archive.route) },
                onNavigateToApkCleanup = { navController.navigate(Screen.ApkCleanup.route) },
                onNavigateToDownloads = { navController.navigate(Screen.DownloadCleanup.route) },
                onNavigateToNotifications = { navController.navigate(Screen.NotificationManager.route) }
            )
        }

        composable(
            route = Screen.Photos.route + "?tab={tab}",
            arguments = listOf(navArgument("tab") { defaultValue = 0; type = NavType.IntType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            PhotosCleanupScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) },
                initialTab = tab
            )
        }

        composable(
            route = Screen.Videos.route + "?tab={tab}",
            arguments = listOf(navArgument("tab") { defaultValue = 0; type = NavType.IntType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            VideosCleanupScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) },
                initialTab = tab
            )
        }

        composable(Screen.Contacts.route) {
            ContactsCleanupScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToVault = { navController.navigate(Screen.Vault.route) },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) },
                onNavigateToEmailCleanup = { navController.navigate(Screen.EmailCleanup.route) },
                onNavigateToWidgets = { navController.navigate(Screen.Widgets.route) },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
            )
        }

        composable(Screen.Vault.route) {
            VaultScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ApkCleanup.route) {
            ApkCleanupScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.DownloadCleanup.route) {
            DownloadCleanupScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.NotificationManager.route) {
            NotificationManagerScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Paywall.route) {
            val viewModel: PaywallViewModel = hiltViewModel()
            PaywallScreen(
                context = PaywallContext.Generic,
                billingManager = viewModel.billingManager,
                onDismiss = { navController.popBackStack() },
                onPurchaseComplete = { navController.popBackStack() }
            )
        }

        composable(Screen.Login.route) {
            LoginPlaceholderScreen(onNavigateBack = { navController.popBackStack() })
        }

        // Placeholder screens for remaining features
        listOf(
            Screen.EmailCleanup to "Email Cleanup",
            Screen.Widgets to "Widgets"
        ).forEach { (screen, title) ->
            composable(screen.route) {
                PlaceholderScreen(title) { navController.popBackStack() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPlaceholderScreen(onNavigateBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Coming Soon", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Account sign-in will be available in a future update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Sign In")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("$title - Coming Soon")
        }
    }
}
