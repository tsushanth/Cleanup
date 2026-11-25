package com.kreativekoala.cleanup.billing

import com.kreativekoala.cleanup.billing.PaywallCoordinator.AccessResult
import com.kreativekoala.cleanup.data.model.ProductIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {
    @Test
    fun nothingOnSaleNeverGatesCleanup() {
        for (remaining in listOf(5, 1, 0)) {
            assertEquals(AccessResult.FREE_TIER, AccessPolicy.decide(isPro = false, remainingFreeActions = remaining, proOnSale = false))
        }
    }

    @Test
    fun freeLimitAppliesOnlyWhenProCanBeBought() {
        assertEquals(AccessResult.FREE_TIER, AccessPolicy.decide(isPro = false, remainingFreeActions = 1, proOnSale = true))
        assertEquals(AccessResult.PAYWALL, AccessPolicy.decide(isPro = false, remainingFreeActions = 0, proOnSale = true))
    }

    @Test
    fun proUsersAreNeverGated() {
        assertEquals(AccessResult.PRO, AccessPolicy.decide(isPro = true, remainingFreeActions = 0, proOnSale = true))
        assertEquals(AccessResult.PRO, AccessPolicy.decide(isPro = true, remainingFreeActions = 0, proOnSale = false))
    }

    @Test
    fun onlyAProProductMakesProBuyable() {
        assertFalse(AccessPolicy.canSellPro(emptyList()))
        assertFalse(AccessPolicy.canSellPro(ProductIds.archiveProductIds))
        assertTrue(AccessPolicy.canSellPro(listOf(ProductIds.PRO_MONTHLY)))
        assertTrue(AccessPolicy.canSellPro(listOf(ProductIds.PRO_YEARLY)))
    }

    @Test
    fun cloudArchiveEntryPointsAreOff() {
        assertFalse(FeatureAvailability.CLOUD_ARCHIVE)
    }
}
