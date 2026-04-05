import SwiftUI
import RevenueCat
import PaywallKit

/// PaywallKit-powered paywall with RevenueCat purchases.
/// Templates & A/B testing controlled by PaywallKit-API; purchases still go through RevenueCat.
struct RemotePaywallView: View {
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var coordinator = PaywallCoordinator.shared
    @Environment(\.dismiss) private var dismiss
    @State private var didPurchaseOrRestore = false

    var body: some View {
        PaywallKit.PaywallView(
            appId: "smartspace",
            appName: "SmartSpace Pro",
            features: [
                PaywallFeature(icon: "🧹", title: "Unlimited Cleanups", description: "Clean your device as often as you want"),
                PaywallFeature(icon: "📸", title: "Duplicate & Similar Photos", description: "Find and remove duplicates instantly"),
                PaywallFeature(icon: "🎥", title: "Large Video Management", description: "Compress or delete space-hogging videos"),
                PaywallFeature(icon: "👥", title: "Contact Dedup", description: "Merge duplicate contacts in one tap"),
                PaywallFeature(icon: "🔒", title: "Private & Secure", description: "All scanning happens on your device"),
            ],
            products: entitlementManager.paywallProducts,
            theme: PaywallTheme(
                accent: Color(red: 0.2, green: 0.5, blue: 1.0),
                accent2: Color(red: 0.4, green: 0.3, blue: 0.9)
            ),
            showWinback: true,
            onPurchase: { productId in
                guard let product = entitlementManager.products.first(where: { $0.productIdentifier == productId }) else { return false }
                do {
                    let (_, customerInfo, _) = try await Purchases.shared.purchase(product: product)
                    if customerInfo.entitlements["pro"]?.isActive == true {
                        didPurchaseOrRestore = true
                        await entitlementManager.refreshEntitlements()
                        await MainActor.run { dismiss() }
                        return true
                    }
                    return false
                } catch {
                    print("[Paywall] Purchase failed: \(error)")
                    return false
                }
            },
            onRestore: {
                do {
                    let customerInfo = try await Purchases.shared.restorePurchases()
                    if customerInfo.entitlements["pro"]?.isActive == true {
                        didPurchaseOrRestore = true
                        await entitlementManager.refreshEntitlements()
                        await MainActor.run { dismiss() }
                    }
                } catch {
                    print("[Paywall] Restore failed: \(error)")
                }
            },
            onDismiss: {
                if !didPurchaseOrRestore {
                    coordinator.trackDismiss()
                }
                dismiss()
            }
        )
        .onAppear {
            AnalyticsService.logPaywallView(context: coordinator.paywallContext.analyticsName)
        }
        .task {
            if entitlementManager.products.isEmpty {
                await entitlementManager.loadProducts()
            }
        }
    }
}
