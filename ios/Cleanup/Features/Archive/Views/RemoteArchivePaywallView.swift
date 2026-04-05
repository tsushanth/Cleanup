import SwiftUI
import RevenueCat
import RevenueCatUI

/// Remote paywall for the Archive subscription — design & copy controlled from RC dashboard.
/// Uses the "archive" offering so experiments and copy changes require no app update.
struct RemoteArchivePaywallView: View {
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let offering = entitlementManager.archiveOffering {
                RevenueCatUI.PaywallView(offering: offering)
                    .onPurchaseCompleted { _ in
                        Task { @MainActor in
                            await entitlementManager.refreshEntitlements()
                        }
                        dismiss()
                    }
                    .onRestoreCompleted { customerInfo in
                        let hasArchive = ["archive_5gb", "archive_25gb", "archive_100gb"]
                            .contains { customerInfo.entitlements[$0]?.isActive == true }
                        if hasArchive {
                            Task { @MainActor in
                                await entitlementManager.refreshEntitlements()
                            }
                            dismiss()
                        }
                    }
            } else {
                // Fallback while offering loads
                RevenueCatUI.PaywallView()
                    .onPurchaseCompleted { _ in
                        Task { @MainActor in
                            await entitlementManager.refreshEntitlements()
                        }
                        dismiss()
                    }
                    .onRestoreCompleted { customerInfo in
                        let hasArchive = ["archive_5gb", "archive_25gb", "archive_100gb"]
                            .contains { customerInfo.entitlements[$0]?.isActive == true }
                        if hasArchive {
                            Task { @MainActor in
                                await entitlementManager.refreshEntitlements()
                            }
                            dismiss()
                        }
                    }
            }
        }
        .onAppear {
            AnalyticsService.logArchivePaywallView()
            if entitlementManager.archiveOffering == nil {
                Task { await entitlementManager.loadProducts() }
            }
        }
    }
}
