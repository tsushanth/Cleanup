package com.kreativekoala.cleanup.domain.service

import android.Manifest
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val contactPermissions: Array<String>
        get() = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

    val mediaPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasContactPermission(): Boolean =
        contactPermissions.all { context.hasPermission(it) }

    fun hasMediaPermission(): Boolean =
        mediaPermissions.all { context.hasPermission(it) }

    private fun Context.hasPermission(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
