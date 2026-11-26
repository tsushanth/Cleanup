package com.kreativekoala.cleanup.domain.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kreativekoala.cleanup.billing.FeatureAvailability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MediaStore.Downloads` exists from Android 10 only, and scanning on Android 8 or 9 closed the app. The scan must
 * not touch it there, and the home screen must not offer it.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadCleanupServiceTest {

    private fun scan() = runBlocking {
        DownloadCleanupService(ApplicationProvider.getApplicationContext<Context>()).scan()
    }

    @Test @Config(sdk = [26, 28], application = Application::class)
    fun scanDoesNotCrashBeforeAndroid10() {
        assertTrue(scan().allFiles.isEmpty())
        assertFalse(FeatureAvailability.DOWNLOADS_CLEANUP)
    }

    @Test @Config(sdk = [29], application = Application::class)
    fun offeredAndScannableFromAndroid10() {
        assertTrue(scan().allFiles.isEmpty())
        assertTrue(FeatureAvailability.DOWNLOADS_CLEANUP)
    }
}
