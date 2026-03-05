package com.kreativekoala.cleanup.billing

import com.kreativekoala.cleanup.data.model.ArchiveSubscriptionTier
import com.kreativekoala.cleanup.data.model.ProductIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS EntitlementManager.
 *
 * Tracks active subscription state: Pro and Archive tiers.
 * Updated by BillingManager when purchases are queried or completed.
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val billingManager: BillingManager
) {
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _hasArchiveSubscription = MutableStateFlow(false)
    val hasArchiveSubscription: StateFlow<Boolean> = _hasArchiveSubscription.asStateFlow()

    private val _currentArchiveTier = MutableStateFlow<ArchiveSubscriptionTier?>(null)
    val currentArchiveTier: StateFlow<ArchiveSubscriptionTier?> = _currentArchiveTier.asStateFlow()

    val subscriptionStatusText: String
        get() = if (_isPro.value) "Pro Member" else "Free"

    init {
        billingManager.setOnPurchasesProcessed { activeProductIds ->
            refreshEntitlements(activeProductIds)
        }
    }

    fun refreshEntitlements(activeProductIds: Set<String>) {
        _isPro.value = activeProductIds.any { it in ProductIds.proProductIds }
        _hasArchiveSubscription.value = activeProductIds.any { it in ProductIds.archiveProductIds }
        _currentArchiveTier.value = activeProductIds
            .firstOrNull { it in ProductIds.archiveProductIds }
            ?.let { ArchiveSubscriptionTier.fromProductId(it) }
    }

    fun refreshFromBilling() {
        billingManager.queryExistingPurchases()
    }
}
