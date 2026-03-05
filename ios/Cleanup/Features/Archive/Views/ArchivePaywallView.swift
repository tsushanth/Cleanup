import SwiftUI
import RevenueCat

struct ArchivePaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    @State private var selectedProduct: StoreProduct?
    @State private var isLoading = false
    @State private var showError = false
    @State private var errorMessage = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    archiveHeader

                    // Features
                    archiveFeatures

                    // Storage tier picker
                    if entitlementManager.archiveProducts.isEmpty {
                        ProgressView("Loading plans...")
                            .padding()
                    } else {
                        storageTierPicker
                    }

                    // CTA Button
                    purchaseButton

                    // Terms
                    archiveTerms
                }
                .padding()
            }
            .navigationTitle("Cloud Archive")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                AnalyticsService.logArchivePaywallView()

                if entitlementManager.archiveProducts.isEmpty {
                    Task { await entitlementManager.loadProducts() }
                }
                if selectedProduct == nil {
                    selectedProduct = entitlementManager.sortedArchiveProducts.first
                }
            }
            .alert("Error", isPresented: $showError) {
                Button("OK") {}
            } message: {
                Text(errorMessage)
            }
        }
    }

    // MARK: - Header

    private var archiveHeader: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.cyan.opacity(0.3), .blue.opacity(0.3)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 80, height: 80)

                Image(systemName: "shield.checkered")
                    .font(.system(size: 36))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.cyan, .blue],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            }

            Text("Delete with Confidence")
                .font(.title2)
                .fontWeight(.bold)

            Text("Your safety net — archive photos, videos, and contacts before deleting. Retrieve them anytime you need them back.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Features

    private var archiveFeatures: some View {
        VStack(alignment: .leading, spacing: 12) {
            ArchiveFeatureRow(icon: "shield.checkered", title: "Safety Net for Deletions", description: "Never lose a file you might need later")
            ArchiveFeatureRow(icon: "arrow.down.to.line", title: "Retrieve Anytime", description: "Download archived files back to your device")
            ArchiveFeatureRow(icon: "leaf.fill", title: "Minimize Your Footprint", description: "We help you store less, not more — unlike big tech")
            ArchiveFeatureRow(icon: "lock.shield", title: "Encrypted & Private", description: "Only you can access your archived files")
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    // MARK: - Storage Tier Picker

    private var storageTierPicker: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Choose Your Plan")
                    .font(.headline)
                if entitlementManager.hasArchiveSubscription {
                    Spacer()
                    Text("ACTIVE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.green)
                        .cornerRadius(4)
                }
            }

            ForEach(entitlementManager.sortedArchiveProducts, id: \.productIdentifier) { product in
                let isCurrentPlan = entitlementManager.currentArchiveTier?.rawValue == product.productIdentifier
                StoragePlanCard(
                    product: product,
                    isSelected: isCurrentPlan || selectedProduct?.productIdentifier == product.productIdentifier,
                    isCurrentPlan: isCurrentPlan,
                    onTap: {
                        if !isCurrentPlan {
                            selectedProduct = product
                        }
                    }
                )
            }
        }
    }

    // MARK: - Purchase Button

    private var isCurrentPlanSelected: Bool {
        guard let selected = selectedProduct else { return false }
        return entitlementManager.currentArchiveTier?.rawValue == selected.productIdentifier
    }

    private var purchaseButton: some View {
        Button {
            Task { await purchaseSelected() }
        } label: {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "icloud.and.arrow.up")
                    if entitlementManager.hasArchiveSubscription {
                        Text("Upgrade Storage")
                    } else {
                        Text("Start Archiving")
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                LinearGradient(
                    colors: selectedProduct != nil && !isCurrentPlanSelected ? [.cyan, .blue] : [.gray, .gray],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundColor(.white)
            .cornerRadius(14)
        }
        .disabled(selectedProduct == nil || isLoading || isCurrentPlanSelected)
    }

    // MARK: - Terms

    private var archiveTerms: some View {
        VStack(spacing: 8) {
            Text("Requires an active Cleanup Pro subscription.")
                .font(.caption)
                .foregroundColor(.secondary)

            Text("Storage subscriptions renew monthly. Cancel anytime in Settings. Archived files are retained for 30 days after cancellation.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Purchase

    private func purchaseSelected() async {
        guard let product = selectedProduct else { return }
        isLoading = true
        defer { isLoading = false }

        AnalyticsService.logArchivePurchaseAttempt(tier: product.productIdentifier)
        let success = await entitlementManager.purchase(product)
        if success {
            AnalyticsService.logArchivePurchaseSuccess(
                tier: product.productIdentifier,
                price: product.price,
                currency: product.currencyCode
            )
            dismiss()
        } else if let error = entitlementManager.lastError {
            errorMessage = error
            showError = true
        }
    }
}

// MARK: - Feature Row

struct ArchiveFeatureRow: View {
    let icon: String
    let title: String
    let description: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.cyan)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
}

// MARK: - Storage Plan Card

struct StoragePlanCard: View {
    let product: StoreProduct
    let isSelected: Bool
    var isCurrentPlan: Bool = false
    let onTap: () -> Void

    private var tier: ArchiveSubscriptionTier? {
        ArchiveSubscriptionTier(rawValue: product.productIdentifier)
    }

    var body: some View {
        Button(action: onTap) {
            HStack {
                if isCurrentPlan {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.title3)
                } else {
                    ZStack {
                        Circle()
                            .stroke(isSelected ? Color.cyan : Color.gray.opacity(0.3), lineWidth: 2)
                            .frame(width: 24, height: 24)
                        if isSelected {
                            Circle()
                                .fill(Color.cyan)
                                .frame(width: 14, height: 14)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(tier?.displayName ?? product.localizedTitle)
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.primary)

                        if isCurrentPlan {
                            Text("CURRENT PLAN")
                                .font(.system(size: 9, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(4)
                        } else if tier?.isBestValue == true {
                            Text("BEST VALUE")
                                .font(.system(size: 9, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange)
                                .foregroundColor(.white)
                                .cornerRadius(4)
                        }
                    }

                    if isCurrentPlan {
                        Text("Subscribed")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else if let perGB = tier?.perGBPrice {
                        Text(perGB)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                VStack(alignment: .trailing) {
                    Text(product.localizedPriceString)
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                    Text("per month")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding()
            .background(isCurrentPlan ? Color.green.opacity(0.08) : isSelected ? Color.cyan.opacity(0.08) : Color(.systemBackground))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isCurrentPlan ? Color.green : isSelected ? Color.cyan : Color(.separator), lineWidth: isCurrentPlan || isSelected ? 1.5 : 0.5)
            )
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(isCurrentPlan)
    }
}
