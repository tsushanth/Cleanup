package com.kreativekoala.cleanup.billing

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// MARK: - Paywall Context

sealed class PaywallContext(val title: String, val subtitle: String) {
    data object Generic : PaywallContext(
        "Unlock Cleanup Pro",
        "Unlimited cleanups & duplicate removal"
    )

    data class AttemptDeleteDuplicates(val count: Int, val savedGB: Double) : PaywallContext(
        "Remove $count duplicates in one tap",
        "Unlimited photo cleanups included"
    )

    data class AttemptDeleteSimilar(val count: Int, val savedGB: Double) : PaywallContext(
        "Clean up $count similar photos",
        "Unlimited photo cleanups included"
    )

    data class AttemptDeleteScreenshots(val count: Int, val savedGB: Double) : PaywallContext(
        "Delete $count screenshots instantly",
        "Bulk delete screenshots anytime"
    )

    data class AttemptDeleteVideos(val count: Int, val savedGB: Double) : PaywallContext(
        "Free up ${"%.1f".format(savedGB)} GB from $count videos",
        "Compress & delete large videos"
    )

    data class AttemptCompressVideo(val savedGB: Double) : PaywallContext(
        "Save ${"%.1f".format(savedGB)} GB with compression",
        "Compress & delete large videos"
    )

    data class AttemptMergeContacts(val count: Int) : PaywallContext(
        "Merge $count duplicate contacts",
        "Keep your contacts organized"
    )

    data class AttemptArchive(val count: Int, val totalGB: Double) : PaywallContext(
        "Archive $count items (${"%.1f".format(totalGB)} GB)",
        "Keep files safe in the cloud instead of deleting"
    )
}

// MARK: - Pending Cleanup Action

sealed class PendingCleanupAction {
    data class DeleteDuplicates(val ids: Set<Long>) : PendingCleanupAction()
    data class DeleteSimilar(val ids: Set<Long>) : PendingCleanupAction()
    data class DeleteScreenshots(val ids: Set<Long>) : PendingCleanupAction()
    data class DeleteVideos(val ids: Set<Long>) : PendingCleanupAction()
    data class CompressVideo(val id: Long) : PendingCleanupAction()
    data class MergeContacts(val groupId: String) : PendingCleanupAction()
    data object OneTapCleanup : PendingCleanupAction()
}

// MARK: - Paywall Coordinator

/**
 * Port of iOS PaywallCoordinator.
 *
 * Manages paywall display, context-aware messaging, pending actions,
 * winback offers, and access control checks.
 */
@Singleton
class PaywallCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val entitlementManager: EntitlementManager,
    private val freeUsageManager: FreeUsageManager
) {
    companion object {
        private const val PREFS_NAME = "paywall_prefs"
        private const val DISMISS_COUNT_KEY = "paywall_dismiss_count"
        private const val LAST_DISMISS_DATE_KEY = "paywall_last_dismiss_date"
        private const val WINBACK_SHOWN_DATE_KEY = "winback_last_shown_date"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    private val _paywallContext = MutableStateFlow<PaywallContext>(PaywallContext.Generic)
    val paywallContext: StateFlow<PaywallContext> = _paywallContext.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingCleanupAction?>(null)
    val pendingAction: StateFlow<PendingCleanupAction?> = _pendingAction.asStateFlow()

    private val _showArchivePaywall = MutableStateFlow(false)
    val showArchivePaywall: StateFlow<Boolean> = _showArchivePaywall.asStateFlow()

    private val _showWinbackOffer = MutableStateFlow(false)
    val showWinbackOffer: StateFlow<Boolean> = _showWinbackOffer.asStateFlow()

    private var paywallDismissCount: Int = prefs.getInt(DISMISS_COUNT_KEY, 0)

    // MARK: - Show Paywall

    fun showPaywall(
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = null
    ) {
        _paywallContext.value = context
        _pendingAction.value = pendingAction
        _showPaywall.value = true
    }

    fun dismissPaywall() {
        _showPaywall.value = false
    }

    fun dismissArchivePaywall() {
        _showArchivePaywall.value = false
    }

    fun trackDismiss() {
        paywallDismissCount++
        prefs.edit()
            .putInt(DISMISS_COUNT_KEY, paywallDismissCount)
            .putLong(LAST_DISMISS_DATE_KEY, System.currentTimeMillis())
            .apply()
    }

    fun clearPendingAction() {
        _pendingAction.value = null
    }

    // MARK: - Access Checks

    enum class AccessResult {
        PRO,
        FREE_TIER,
        PAYWALL
    }

    fun checkAccess(
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = null
    ): AccessResult {
        if (entitlementManager.isPro.value) return AccessResult.PRO

        val remaining = freeUsageManager.remainingFreeActions()
        if (remaining > 0) return AccessResult.FREE_TIER

        showPaywall(context, pendingAction)
        return AccessResult.PAYWALL
    }

    fun recordUsage(count: Int) {
        freeUsageManager.recordUsage(count)
    }

    fun checkAccessOrShowPaywall(
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = null
    ): Boolean {
        if (entitlementManager.isPro.value) return true
        showPaywall(context, pendingAction)
        return false
    }

    // MARK: - Archive Access

    enum class ArchiveAccessResult {
        ALLOWED,
        NEEDS_PRO,
        NEEDS_ARCHIVE_SUBSCRIPTION
    }

    fun checkArchiveAccess(
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = null
    ): ArchiveAccessResult {
        if (!entitlementManager.isPro.value) {
            showPaywall(PaywallContext.Generic, pendingAction)
            return ArchiveAccessResult.NEEDS_PRO
        }

        if (!entitlementManager.hasArchiveSubscription.value) {
            _showArchivePaywall.value = true
            _pendingAction.value = pendingAction
            return ArchiveAccessResult.NEEDS_ARCHIVE_SUBSCRIPTION
        }

        return ArchiveAccessResult.ALLOWED
    }

    // MARK: - Winback Logic

    fun checkWinbackEligibility() {
        if (entitlementManager.isPro.value) {
            _showWinbackOffer.value = false
            return
        }
        if (paywallDismissCount < 3) {
            _showWinbackOffer.value = false
            return
        }

        val lastDismiss = prefs.getLong(LAST_DISMISS_DATE_KEY, 0)
        if (lastDismiss == 0L) {
            _showWinbackOffer.value = false
            return
        }

        val daysSinceLastDismiss = (System.currentTimeMillis() - lastDismiss) / 86_400_000.0
        if (daysSinceLastDismiss < 1) {
            _showWinbackOffer.value = false
            return
        }

        val lastWinback = prefs.getLong(WINBACK_SHOWN_DATE_KEY, 0)
        if (lastWinback > 0) {
            val daysSinceWinback = (System.currentTimeMillis() - lastWinback) / 86_400_000.0
            if (daysSinceWinback < 1) {
                _showWinbackOffer.value = false
                return
            }
        }

        _showWinbackOffer.value = true
    }

    fun markWinbackShown() {
        prefs.edit().putLong(WINBACK_SHOWN_DATE_KEY, System.currentTimeMillis()).apply()
        _showWinbackOffer.value = false
    }

    val shouldShowNudgeBanner: Boolean
        get() = !entitlementManager.isPro.value && paywallDismissCount >= 2
}
