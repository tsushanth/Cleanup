package com.kreativekoala.cleanup.billing

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks free usage counts globally. Free users get 5 total cleanup actions
 * across all categories before being prompted to upgrade to Pro.
 */
@Singleton
class FreeUsageManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "free_usage_prefs"
        private const val TOTAL_USAGE_KEY = "free_usage_total"
        private const val FREE_LIMIT = 5
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _totalUsed = MutableStateFlow(0)
    val totalUsed: StateFlow<Int> = _totalUsed.asStateFlow()

    init {
        _totalUsed.value = prefs.getInt(TOTAL_USAGE_KEY, 0)
    }

    fun remainingFreeActions(): Int {
        return maxOf(0, FREE_LIMIT - _totalUsed.value)
    }

    fun hasFreeDeletionsAvailable(): Boolean {
        return remainingFreeActions() > 0
    }

    fun recordUsage(count: Int) {
        val newTotal = _totalUsed.value + count
        _totalUsed.value = newTotal
        prefs.edit().putInt(TOTAL_USAGE_KEY, newTotal).apply()
    }

    fun totalUsedCount(): Int = _totalUsed.value
}
