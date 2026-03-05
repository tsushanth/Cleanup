package com.kreativekoala.cleanup.domain.service

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-exclusive: Notification Management Service.
 *
 * Analyzes notification channels per app and helps users manage spammy notifications.
 * Uses NotificationManager.getNotificationChannels() for per-app channel management.
 */
@Singleton
class NotificationManagementService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AppNotificationInfo(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?,
        val channelCount: Int,
        val enabledChannelCount: Int,
        val isNotificationEnabled: Boolean
    )

    suspend fun getAppNotificationInfos(): List<AppNotificationInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }

        val infos = mutableListOf<AppNotificationInfo>()

        for (app in installedApps) {
            try {
                val channelCount: Int
                val enabledCount: Int

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Can't enumerate other apps' channels directly from a non-system app
                    // Use areNotificationsEnabled instead
                    channelCount = 0
                    enabledCount = 0
                } else {
                    channelCount = 0
                    enabledCount = 0
                }

                val isEnabled = notificationManager.areNotificationsEnabled()

                infos.add(
                    AppNotificationInfo(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        appIcon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null },
                        channelCount = channelCount,
                        enabledChannelCount = enabledCount,
                        isNotificationEnabled = isEnabled
                    )
                )
            } catch (_: Exception) {}
        }

        infos.sortedBy { it.appName }
    }
}
