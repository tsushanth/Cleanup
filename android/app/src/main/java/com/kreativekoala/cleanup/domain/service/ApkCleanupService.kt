package com.kreativekoala.cleanup.domain.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-exclusive: APK File Cleanup Service.
 *
 * Scans Downloads and common APK locations for old/unused APK files.
 * Categorizes as: Already Installed, Not Installed, Outdated Version.
 */
@Singleton
class ApkCleanupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ApkFile(
        val path: String,
        val name: String,
        val size: Long,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val status: ApkStatus
    )

    enum class ApkStatus(val displayName: String) {
        ALREADY_INSTALLED("Already Installed"),
        OUTDATED("Outdated Version"),
        NOT_INSTALLED("Not Installed"),
        UNKNOWN("Unknown")
    }

    suspend fun scan(): List<ApkFile> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apkFiles = mutableListOf<ApkFile>()

        // Scan common APK locations
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "APK"),
            File(Environment.getExternalStorageDirectory(), "apk")
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown().maxDepth(2).forEach { file ->
                if (file.isFile && file.extension.equals("apk", ignoreCase = true)) {
                    val apkInfo = analyzeApk(pm, file)
                    apkFiles.add(apkInfo)
                }
            }
        }

        apkFiles.sortedByDescending { it.size }
    }

    private fun analyzeApk(pm: PackageManager, file: File): ApkFile {
        val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)

        return if (archiveInfo != null) {
            val packageName = archiveInfo.packageName
            val apkVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }

            val status = try {
                val installedInfo = pm.getPackageInfo(packageName, 0)
                val installedVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    installedInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    installedInfo.versionCode.toLong()
                }
                when {
                    installedVersionCode >= apkVersionCode -> ApkStatus.ALREADY_INSTALLED
                    installedVersionCode < apkVersionCode -> ApkStatus.OUTDATED
                    else -> ApkStatus.ALREADY_INSTALLED
                }
            } catch (_: PackageManager.NameNotFoundException) {
                ApkStatus.NOT_INSTALLED
            }

            ApkFile(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                packageName = packageName,
                versionName = archiveInfo.versionName,
                versionCode = apkVersionCode,
                status = status
            )
        } else {
            ApkFile(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                packageName = null,
                versionName = null,
                versionCode = null,
                status = ApkStatus.UNKNOWN
            )
        }
    }

    suspend fun deleteApks(files: List<ApkFile>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (apk in files) {
            if (File(apk.path).delete()) deleted++
        }
        deleted
    }
}
