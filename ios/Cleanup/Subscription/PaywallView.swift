import SwiftUI
import RevenueCat

struct PaywallView: View {
    @ObservedObject var entitlementManager = EntitlementManager.shared
    @ObservedObject var coordinator = PaywallCoordinator.shared
    @Environment(\.dismiss) var dismiss
    @State private var selectedProduct: StoreProduct?
    @State private var showError = false
    @State private var showCloseButton = false
    @State private var showOfferCodeRedemption = false

    var body: some View {
        NavigationStack {
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [Color(.systemBackground), Color.blue.opacity(0.05)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        // Header with context-aware copy
                        PaywallHeaderView(context: coordinator.paywallContext)

                        // Features list
                        PaywallFeaturesView()

                        // Plan picker
                        if entitlementManager.isLoadingProducts {
                            PlanPickerSkeleton()
                        } else {
                            PlanPickerView(
                                products: entitlementManager.sortedProductsForDisplay,
                                selectedProduct: $selectedProduct
                            )
                        }

                        // CTA Button
                        PaywallCTAButton(
                            selectedProduct: selectedProduct,
                            isLoading: entitlementManager.purchaseInProgress
                        ) {
                            await purchaseSelected()
                        }

                        // Restore & Redeem links
                        HStack(spacing: 16) {
                            Button("Restore Purchases") {
                                Task {
                                    let restored = await entitlementManager.restorePurchases()
                                    AnalyticsService.logRestorePurchases(success: restored)
                                    if restored {
                                        handleSuccessfulPurchase()
                                    }
                                }
                            }
                            .font(.subheadline)
                            .foregroundColor(.blue)

                            Text("|")
                                .foregroundColor(.secondary)

                            Button("Redeem Code") {
                                showOfferCodeRedemption = true
                            }
                            .font(.subheadline)
                            .foregroundColor(.blue)
                        }

                        // Terms footer
                        PaywallTermsView()
                    }
                    .padding()
                    .padding(.bottom, 20)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(content: {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .foregroundColor(.secondary)
                    }
                }
            })
            .onAppear {
                AnalyticsService.logPaywallView(context: coordinator.paywallContext.analyticsName)

                // Reload products if not loaded
                if entitlementManager.products.isEmpty && !entitlementManager.isLoadingProducts {
                    Task {
                        await entitlementManager.loadProducts()
                    }
                }

                // Default to yearly (best value)
                if selectedProduct == nil {
                    selectedProduct = entitlementManager.yearlyProduct
                }
            }
            .onChange(of: entitlementManager.products) { products in
                // When products load, auto-select yearly if nothing selected
                if selectedProduct == nil && !products.isEmpty {
                    selectedProduct = entitlementManager.yearlyProduct ?? products.first
                }
            }
            .alert("Error", isPresented: $showError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(entitlementManager.lastError ?? "An error occurred")
            }
            .onChange(of: entitlementManager.lastError) { newValue in
                if newValue != nil {
                    showError = true
                }
            }
            .offerCodeRedemption(isPresented: $showOfferCodeRedemption) { result in
                switch result {
                case .success:
                    Task {
                        await entitlementManager.refreshEntitlements()
                        if entitlementManager.isPro {
                            handleSuccessfulPurchase()
                        }
                    }
                case .failure:
                    break
                }
            }
        }
    }

    private func purchaseSelected() async {
        guard let product = selectedProduct else { return }

        AnalyticsService.logPurchaseAttempt(productId: product.productIdentifier)
        let success = await entitlementManager.purchase(product)

        // Ensure UI update happens on main thread
        await MainActor.run {
            if success {
                AnalyticsService.logPurchaseSuccess(
                    productId: product.productIdentifier,
                    price: product.price,
                    currency: product.currencyCode
                )
                handleSuccessfulPurchase()
            } else if let error = entitlementManager.lastError {
                AnalyticsService.logPurchaseFailure(productId: product.productIdentifier, error: error)
            }
        }
    }

    private func handleSuccessfulPurchase() {
        dismiss()
        // Pending action will be handled by the calling view
    }
}

// MARK: - Header View
struct PaywallHeaderView: View {
    let context: PaywallContext

    var body: some View {
        VStack(spacing: 16) {
            // Icon
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.blue.opacity(0.2), .purple.opacity(0.2)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 80, height: 80)

                Image(systemName: "sparkles")
                    .font(.system(size: 36))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            }

            // Title - context aware
            Text(context.title)
                .font(.title2)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)

            // Subtitle - context aware
            Text(context.subtitle)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 20)
    }
}

// MARK: - Features View
struct PaywallFeaturesView: View {
    var body: some View {
        VStack(spacing: 16) {
            ForEach(PaywallFeature.allFeatures) { feature in
                HStack(spacing: 14) {
                    Image(systemName: feature.icon)
                        .font(.title3)
                        .foregroundColor(.blue)
                        .frame(width: 32)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(feature.title)
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Text(feature.description)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                }
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(16)
    }
}

// MARK: - Plan Picker View
struct PlanPickerView: View {
    let products: [StoreProduct]
    @Binding var selectedProduct: StoreProduct?
    @ObservedObject var entitlementManager = EntitlementManager.shared

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Choose Your Plan")
                    .font(.headline)
                if entitlementManager.isPro {
                    Spacer()
                    Text("PRO ACTIVE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.green)
                        .cornerRadius(4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if products.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.title)
                        .foregroundColor(.orange)
                    Text("Unable to load plans")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text("Please check your connection and try again")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Button {
                        Task {
                            await EntitlementManager.shared.loadProducts()
                        }
                    } label: {
                        Label("Retry", systemImage: "arrow.clockwise")
                            .font(.subheadline)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                    .padding(.top, 8)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
            } else {
                ForEach(products, id: \.productIdentifier) { product in
                    PlanCardView(
                        product: product,
                        isSelected: selectedProduct?.productIdentifier == product.productIdentifier,
                        isPurchased: entitlementManager.isPro
                    ) {
                        if !entitlementManager.isPro {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                selectedProduct = product
                            }
                        }
                    }
                }
            }
        }
    }
}

struct PlanCardView: View {
    let product: StoreProduct
    let isSelected: Bool
    let isPurchased: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                // Selection indicator
                if isPurchased {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(isSelected ? .green : .gray.opacity(0.3))
                        .font(.title3)
                } else {
                    ZStack {
                        Circle()
                            .stroke(isSelected ? Color.blue : Color.gray.opacity(0.3), lineWidth: 2)
                            .frame(width: 24, height: 24)

                        if isSelected {
                            Circle()
                                .fill(Color.blue)
                                .frame(width: 14, height: 14)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(planTitle)
                            .font(.headline)
                            .foregroundColor(.primary)

                        if isPurchased && isSelected {
                            Text("CURRENT PLAN")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.green)
                                .cornerRadius(4)
                        } else if product.isBestValue && !isPurchased {
                            Text("BEST VALUE")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.orange)
                                .cornerRadius(4)
                        }

                        if product.hasFreeTrial && !isPurchased {
                            Text("3-DAY FREE TRIAL")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.green)
                                .cornerRadius(4)
                        }
                    }

                    if isPurchased && isSelected {
                        Text("Subscribed")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else if product.hasFreeTrial {
                        Text("Try free for 3 days, then \(product.localizedPriceString)/year")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text(product.periodUnitText)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(product.localizedPriceString)
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)

                    if product.isBestValue && !isPurchased, let weeklyEquivalent = calculateWeeklyEquivalent() {
                        Text(weeklyEquivalent)
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }
            }
            .padding()
            .background(isPurchased && isSelected ? Color.green.opacity(0.08) : isSelected ? Color.blue.opacity(0.08) : Color(.systemBackground))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isPurchased && isSelected ? Color.green : isSelected ? Color.blue : Color.gray.opacity(0.2), lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(isPurchased)
    }

    private var planTitle: String {
        switch product.productIdentifier {
        case "cleanup_pro_yearly_2999":
            return "Yearly"
        case "cleanup_pro_monthly_999":
            return "Monthly"
        case "cleanup_pro_weekly_799":
            return "Weekly"
        default:
            return product.localizedTitle
        }
    }

    private func calculateWeeklyEquivalent() -> String? {
        guard product.productIdentifier == "cleanup_pro_yearly_2999" else { return nil }
        let weeklyPrice = product.price / 52
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = product.priceFormatter?.locale ?? Locale.current
        if let formatted = formatter.string(from: weeklyPrice as NSNumber) {
            return "\(formatted)/week"
        }
        return nil
    }
}

// MARK: - Plan Picker Skeleton
struct PlanPickerSkeleton: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Choose Your Plan")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)

            ForEach(0..<2) { _ in
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.gray.opacity(0.1))
                    .frame(height: 80)
                    .overlay(
                        ProgressView()
                    )
            }
        }
    }
}

// MARK: - CTA Button
struct PaywallCTAButton: View {
    let selectedProduct: StoreProduct?
    let isLoading: Bool
    let onPurchase: () async -> Void

    var body: some View {
        Button {
            Task {
                await onPurchase()
            }
        } label: {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: "sparkles")
                    Text(buttonText)
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                LinearGradient(
                    colors: selectedProduct != nil ? [.blue, .purple] : [.gray, .gray],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundColor(.white)
            .cornerRadius(14)
        }
        .disabled(selectedProduct == nil || isLoading)
    }

    private var buttonText: String {
        if let product = selectedProduct, product.hasFreeTrial {
            return "Start Free Trial"
        }
        return "Start Cleaning"
    }
}

// MARK: - Terms View
struct PaywallTermsView: View {
    var body: some View {
        VStack(spacing: 12) {
            // Links
            HStack(spacing: 16) {
                Link("Terms of Service", destination: URL(string: "https://kreativekoala.llc/terms")!)
                    .font(.caption)
                    .foregroundColor(.blue)

                Text("•")
                    .foregroundColor(.secondary)

                Link("Privacy Policy", destination: URL(string: "https://kreativekoala.llc/privacy")!)
                    .font(.caption)
                    .foregroundColor(.blue)
            }

            // Legal text
            Text("Cancel anytime in Settings > Apple ID > Subscriptions. Subscriptions auto-renew unless cancelled at least 24 hours before the end of the current period.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 8)
    }
}

#Preview {
    PaywallView()
}
