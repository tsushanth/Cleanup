package com.kreativekoala.cleanup.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.kreativekoala.cleanup.R

sealed class Screen(val route: String) {
    // Bottom nav destinations
    data object Home : Screen("home")
    data object Photos : Screen("photos")
    data object Videos : Screen("videos")
    data object Contacts : Screen("contacts")
    data object Settings : Screen("settings")

    // Secondary destinations
    data object Vault : Screen("vault")
    data object Archive : Screen("archive")
    data object ArchiveSignIn : Screen("archive_sign_in")
    data object EmailCleanup : Screen("email_cleanup")
    data object Widgets : Screen("widgets")
    data object Onboarding : Screen("onboarding")
    data object Paywall : Screen("paywall")
    data object Login : Screen("login")

    // Android-exclusive destinations
    data object ApkCleanup : Screen("apk_cleanup")
    data object DownloadCleanup : Screen("download_cleanup")
    data object NotificationManager : Screen("notification_manager")
}

enum class BottomNavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME(Screen.Home, R.string.nav_home, Icons.Default.Home),
    PHOTOS(Screen.Photos, R.string.nav_photos, Icons.Default.Photo),
    VIDEOS(Screen.Videos, R.string.nav_videos, Icons.Default.VideoLibrary),
    CONTACTS(Screen.Contacts, R.string.nav_contacts, Icons.Default.Contacts),
    SETTINGS(Screen.Settings, R.string.nav_settings, Icons.Default.Settings)
}
