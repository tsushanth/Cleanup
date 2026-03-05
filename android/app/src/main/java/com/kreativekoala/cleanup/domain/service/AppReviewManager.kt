package com.kreativekoala.cleanup.domain.service

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS AppReviewManager.
 *
 * Handles Google Play In-App Review prompts.
 * Shows after a successful cleanup action to maximize positive reviews.
 */
@Singleton
class AppReviewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "app_review_prefs"
        private const val KEY_CLEANUPS_COUNT = "cleanups_count"
        private const val KEY_LAST_REVIEW_PROMPT = "last_review_prompt"
        private const val KEY_HAS_REVIEWED = "has_reviewed"
        private const val CLEANUPS_BEFORE_REVIEW = 3
        private const val MIN_DAYS_BETWEEN_PROMPTS = 30L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordCleanup() {
        val count = prefs.getInt(KEY_CLEANUPS_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CLEANUPS_COUNT, count).apply()
    }

    fun shouldRequestReview(): Boolean {
        if (prefs.getBoolean(KEY_HAS_REVIEWED, false)) return false

        val cleanups = prefs.getInt(KEY_CLEANUPS_COUNT, 0)
        if (cleanups < CLEANUPS_BEFORE_REVIEW) return false

        val lastPrompt = prefs.getLong(KEY_LAST_REVIEW_PROMPT, 0)
        val daysSinceLastPrompt = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
        return lastPrompt == 0L || daysSinceLastPrompt >= MIN_DAYS_BETWEEN_PROMPTS
    }

    fun requestReview(activity: Activity) {
        if (!shouldRequestReview()) return

        val reviewManager = ReviewManagerFactory.create(context)
        val reviewFlow = reviewManager.requestReviewFlow()
        reviewFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                    prefs.edit()
                        .putLong(KEY_LAST_REVIEW_PROMPT, System.currentTimeMillis())
                        .putBoolean(KEY_HAS_REVIEWED, true)
                        .apply()
                }
            }
        }
    }
}
