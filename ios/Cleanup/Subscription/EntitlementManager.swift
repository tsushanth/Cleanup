import SwiftUI
import RevenueCat

@MainActor
class EntitlementManager: NSObject, ObservableObject {
    static let shared = EntitlementManager()

    // MARK: - Published State
    @Published var isPro: Bool = false
    @Published var products: [StoreProduct] = []
    @Published var isLoadingProducts: Bool = false
    @Published var purchaseInProgress: Bool = false
    @Published var lastError: String? = nil

    // Archive subscription state
    @Published var hasArchiveSubscription: Bool = false
    @Published var archiveProducts: [StoreProduct] = []
    @Published var currentArchiveTier: ArchiveSubscriptionTier? = nil

    // RevenueCat offerings for experimentation
    @Published var currentOffering: Offering?
    @Published var archiveOffering: Offering?

    // MARK: - Product IDs
    private let proProductIDs: Set<String> = [
        "cleanup_pro_weekly_799",
        "cleanup_pro_monthly_999",
        "cleanup_pro_yearly_2999"
    ]

    private let archiveProductIDs: Set<String> = [
        "cleanup_archive_5gb__199",
        "cleanup_archive_25gb_499",
        "cleanup_archive_100gb_1499"
    ]

    // MARK: - Entitlement IDs (must match RevenueCat dashboard)
    private let proEntitlementID = "pro"
    private let archiveEntitlementIDs: Set<String> = [
        "archive_5gb",
        "archive_25gb",
        "archive_100gb"
    ]

    // Map archive entitlement IDs to product IDs for tier lookup
    private let archiveEntitlementToProduct: [String: String] = [
        "archive_5gb": "cleanup_archive_5gb__199",
        "archive_25gb": "cleanup_archive_25gb_499",
        "archive_100gb": "cleanup_archive_100gb_1499"
    ]

    // MARK: - Computed Properties
    var monthlyProduct: StoreProduct? {
        products.first { $0.productIdentifier == "cleanup_pro_monthly_999" }
    }

    var yearlyProduct: StoreProduct? {
        products.first { $0.productIdentifier == "cleanup_pro_yearly_2999" }
    }

    var weeklyProduct: StoreProduct? {
        products.first { $0.productIdentifier == "cleanup_pro_weekly_799" }
    }

    /// Products sorted for display: yearly first (best value), then monthly, then weekly
    var sortedProductsForDisplay: [StoreProduct] {
        let order = ["cleanup_pro_yearly_2999", "cleanup_pro_monthly_999", "cleanup_pro_weekly_799"]
        return products.sorted { p1, p2 in
            let i1 = order.firstIndex(of: p1.productIdentifier) ?? Int.max
            let i2 = order.firstIndex(of: p2.productIdentifier) ?? Int.max
            return i1 < i2
        }
    }

    var subscriptionStatusText: String {
        isPro ? "Pro Member" : "Free"
    }

    /// Archive products sorted by storage size for display
    var sortedArchiveProducts: [StoreProduct] {
        let order = [
            "cleanup_archive_5gb__199",
            "cleanup_archive_25gb_499",
            "cleanup_archive_100gb_1499"
        ]
        return archiveProducts.sorted { p1, p2 in
            let i1 = order.firstIndex(of: p1.productIdentifier) ?? Int.max
            let i2 = order.firstIndex(of: p2.productIdentifier) ?? Int.max
            return i1 < i2
        }
    }

    var subscriptionTypeDescription: String {
        isPro ? "Cleanup Pro Subscription" : "Upgrade to unlock all features"
    }

    // MARK: - Initialization
    private override init() {
        super.init()
        // Clean up legacy promo code flag from previous versions
        UserDefaults.standard.removeObject(forKey: "promo_code_activated")
    }

    // MARK: - Configure RevenueCat (call from AppDelegate)
    func configure() {
        Purchases.logLevel = .warn
        Purchases.configure(withAPIKey: "appl_ENPkaZyUjVLReTrOAMEUakqvEjt")
        Purchases.shared.delegate = self

        Task {
            await loadProducts()
            await refreshEntitlements()
        }
    }

    // MARK: - Load Products via Offerings
    func loadProducts() async {
        isLoadingProducts = true
        defer { isLoadingProducts = false }

        do {
            let offerings = try await Purchases.shared.offerings()

            // Default offering for Pro subscriptions
            if let defaultOffering = offerings.current {
                currentOffering = defaultOffering

                products = defaultOffering.availablePackages
                    .map { $0.storeProduct }
                    .filter { proProductIDs.contains($0.productIdentifier) }
            }

            // Archive offering (look for "archive" offering identifier)
            if let archive = offerings.offering(identifier: "archive") {
                archiveOffering = archive
                archiveProducts = archive.availablePackages
                    .map { $0.storeProduct }
                    .filter { archiveProductIDs.contains($0.productIdentifier) }
            }

            // Fallback: if no separate archive offering, load archive products from default
            if archiveProducts.isEmpty, let defaultOffering = offerings.current {
                archiveProducts = defaultOffering.availablePackages
                    .map { $0.storeProduct }
                    .filter { archiveProductIDs.contains($0.productIdentifier) }
            }
        } catch {
            lastError = "Failed to load products: \(error.localizedDescription)"
            print("[Cleanup] Error loading offerings: \(error)")
        }
    }

    // MARK: - Refresh Entitlements
    func refreshEntitlements() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateEntitlements(from: customerInfo)
        } catch {
            print("[EntitlementManager] Failed to refresh entitlements: \(error)")
        }
    }

    private func updateEntitlements(from customerInfo: CustomerInfo) {
        // Pro entitlement
        isPro = customerInfo.entitlements[proEntitlementID]?.isActive == true

        // Archive entitlements
        var archiveActive = false
        var activeTier: ArchiveSubscriptionTier? = nil

        for entitlementID in archiveEntitlementIDs {
            if customerInfo.entitlements[entitlementID]?.isActive == true {
                archiveActive = true
                if let productID = archiveEntitlementToProduct[entitlementID] {
                    activeTier = ArchiveSubscriptionTier(rawValue: productID)
                }
                break
            }
        }

        hasArchiveSubscription = archiveActive
        currentArchiveTier = activeTier

        // Update Firebase user property for Google Ads audience targeting
        AnalyticsService.setSubscriptionStatus(isPro: isPro)
    }

    // MARK: - Purchase
    @discardableResult
    func purchase(_ package: Package) async -> Bool {
        purchaseInProgress = true
        lastError = nil
        defer {
            Task { @MainActor in
                purchaseInProgress = false
            }
        }

        do {
            let (_, customerInfo, userCancelled) = try await Purchases.shared.purchase(package: package)

            if userCancelled {
                return false
            }

            await MainActor.run {
                updateEntitlements(from: customerInfo)
            }
            return true
        } catch {
            await MainActor.run {
                lastError = "Purchase failed: \(error.localizedDescription)"
            }
            return false
        }
    }

    /// Purchase by StoreProduct (convenience for views that don't have a Package)
    @discardableResult
    func purchase(_ storeProduct: StoreProduct) async -> Bool {
        purchaseInProgress = true
        lastError = nil
        defer {
            Task { @MainActor in
                purchaseInProgress = false
            }
        }

        do {
            let (_, customerInfo, userCancelled) = try await Purchases.shared.purchase(product: storeProduct)

            if userCancelled {
                return false
            }

            await MainActor.run {
                updateEntitlements(from: customerInfo)
            }
            return true
        } catch {
            await MainActor.run {
                lastError = "Purchase failed: \(error.localizedDescription)"
            }
            return false
        }
    }

    // MARK: - Restore Purchases
    func restorePurchases() async -> Bool {
        purchaseInProgress = true
        lastError = nil
        defer { purchaseInProgress = false }

        do {
            let customerInfo = try await Purchases.shared.restorePurchases()
            updateEntitlements(from: customerInfo)
            return isPro
        } catch {
            lastError = "Failed to restore purchases: \(error.localizedDescription)"
            return false
        }
    }
}

// MARK: - RevenueCat Delegate
extension EntitlementManager: PurchasesDelegate {
    nonisolated func purchases(_ purchases: Purchases, receivedUpdated customerInfo: CustomerInfo) {
        Task { @MainActor in
            updateEntitlements(from: customerInfo)
        }
    }
}

// MARK: - StoreProduct Extensions
extension StoreProduct {
    var periodUnitText: String {
        guard let period = subscriptionPeriod else { return "" }

        switch period.unit {
        case .day:
            return period.value == 7 ? "per week" : "per day"
        case .week:
            return "per week"
        case .month:
            return "per month"
        case .year:
            return "per year"
        @unknown default:
            return ""
        }
    }

    var isBestValue: Bool {
        productIdentifier == "cleanup_pro_yearly_2999"
    }

    var hasFreeTrial: Bool {
        introductoryDiscount?.paymentMode == .freeTrial
    }

    var freeTrialDays: Int? {
        guard let offer = introductoryDiscount,
              offer.paymentMode == .freeTrial else { return nil }
        let period = offer.subscriptionPeriod
        switch period.unit {
        case .day: return period.value
        case .week: return period.value * 7
        default: return nil
        }
    }
}
