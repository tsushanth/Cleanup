import FirebaseAnalytics
import TikTokBusinessSDK

enum AnalyticsService {

    static func logEvent(_ name: String, parameters: [String: Any]? = nil) {
        Analytics.logEvent(name, parameters: parameters)
        TikTokBusiness.trackEvent(name, withProperties: parameters ?? [:])
    }

    // MARK: - Paywall Events

    static func logPaywallView(context: String) {
        logEvent("paywall_view", parameters: ["context": context])
    }

    static func logPurchaseAttempt(productId: String) {
        logEvent("purchase_attempt", parameters: ["product_id": productId])
    }

    static func logPurchaseSuccess(productId: String, price: Decimal? = nil, currency: String? = nil) {
        // Custom event for internal tracking
        logEvent("purchase_success", parameters: ["product_id": productId])

        // Standard Firebase "purchase" event — recognized by Google Ads for conversion optimization
        var purchaseParams: [String: Any] = [
            AnalyticsParameterItemID: productId,
            AnalyticsParameterItemName: productId
        ]
        if let price = price {
            purchaseParams[AnalyticsParameterValue] = NSDecimalNumber(decimal: price).doubleValue
        }
        if let currency = currency {
            purchaseParams[AnalyticsParameterCurrency] = currency
        }
        Analytics.logEvent(AnalyticsEventPurchase, parameters: purchaseParams)
    }

    static func logPurchaseFailure(productId: String, error: String) {
        logEvent("purchase_failure", parameters: [
            "product_id": productId,
            "error": error
        ])
    }

    static func logRestorePurchases(success: Bool) {
        logEvent("restore_purchases", parameters: ["success": success])
    }

    // MARK: - Feature Gating Events

    static func logFeatureGated(feature: String, result: String) {
        logEvent("feature_gated", parameters: [
            "feature": feature,
            "result": result
        ])
    }

    // MARK: - Winback Events

    static func logWinbackView() {
        logEvent("winback_view")
    }

    static func logWinbackPurchase() {
        logEvent("winback_purchase")
    }

    // MARK: - Archive Events

    static func logArchivePaywallView() {
        logEvent("archive_paywall_view")
    }

    static func logArchivePurchaseAttempt(tier: String) {
        logEvent("archive_purchase_attempt", parameters: ["tier": tier])
    }

    static func logArchivePurchaseSuccess(tier: String, price: Decimal? = nil, currency: String? = nil) {
        logEvent("archive_purchase_success", parameters: ["tier": tier])

        // Standard Firebase "purchase" event for Google Ads
        var purchaseParams: [String: Any] = [
            AnalyticsParameterItemID: tier,
            AnalyticsParameterItemName: tier,
            AnalyticsParameterItemCategory: "archive"
        ]
        if let price = price {
            purchaseParams[AnalyticsParameterValue] = NSDecimalNumber(decimal: price).doubleValue
        }
        if let currency = currency {
            purchaseParams[AnalyticsParameterCurrency] = currency
        }
        Analytics.logEvent(AnalyticsEventPurchase, parameters: purchaseParams)
    }

    // MARK: - User Properties

    static func setSubscriptionStatus(isPro: Bool) {
        Analytics.setUserProperty(isPro ? "pro" : "free", forName: "subscription_status")
    }
}
