package com.kreativekoala.cleanup.billing

import com.kreativekoala.cleanup.data.model.ProductIds

/** Entry points for features that cannot work in the current build are hidden with these. */
object FeatureAvailability {
    /**
     * Cloud archive. Off: the app has no working sign-in for it (the button passes no Google token and the backend does not
     * accept Google logins). Turn on only after a signed-in upload and retrieval have been verified end to end.
     */
    const val CLOUD_ARCHIVE = false
}

/** The rules for the free cleanup limit, kept free of Android classes so they can be unit tested. */
object AccessPolicy {
    /** True when Google Play returned at least one Pro subscription that can actually be bought. */
    fun canSellPro(purchasableProductIds: Collection<String>): Boolean =
        purchasableProductIds.any { it in ProductIds.proProductIds }

    /**
     * The free limit only applies while Pro can be bought. With no product (none configured in Play, Play unreachable, or
     * still loading) a paywall could not be passed, so cleanup stays available.
     */
    fun decide(isPro: Boolean, remainingFreeActions: Int, proOnSale: Boolean): PaywallCoordinator.AccessResult = when {
        isPro -> PaywallCoordinator.AccessResult.PRO
        !proOnSale || remainingFreeActions > 0 -> PaywallCoordinator.AccessResult.FREE_TIER
        else -> PaywallCoordinator.AccessResult.PAYWALL
    }
}
