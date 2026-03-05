import SwiftUI
import RevenueCat

struct WinbackOfferView: View {
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @Environment(\.dismiss) var dismiss
    @State private var showError = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                // Special offer badge
                Text("SPECIAL OFFER")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(
                        LinearGradient(
                            colors: [.orange, .red],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(20)

                VStack(spacing: 12) {
                    Text("We miss you!")
                        .font(.title)
                        .fontWeight(.bold)

                    Text("Try Cleanup Pro free for 3 days. Cancel anytime — no commitment.")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                // Value props with numbers
                VStack(spacing: 14) {
                    WinbackValueRow(
                        icon: "photo.on.rectangle.angled",
                        text: "Unlimited duplicate & similar photo cleanup"
                    )
                    WinbackValueRow(
                        icon: "video.fill",
                        text: "Compress videos & save gigabytes"
                    )
                    WinbackValueRow(
                        icon: "person.2.fill",
                        text: "Merge all duplicate contacts"
                    )
                    WinbackValueRow(
                        icon: "lock.shield.fill",
                        text: "Private vault for sensitive photos"
                    )
                }
                .padding(.horizontal, 32)

                Spacer()

                // CTA: Start free trial
                if let yearlyProduct = entitlementManager.yearlyProduct {
                    VStack(spacing: 8) {
                        Button {
                            Task {
                                let success = await entitlementManager.purchase(yearlyProduct)
                                if success {
                                    AnalyticsService.logWinbackPurchase()
                                    dismiss()
                                }
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if entitlementManager.purchaseInProgress {
                                    ProgressView()
                                        .tint(.white)
                                } else {
                                    Image(systemName: "sparkles")
                                    Text("Start 3-Day Free Trial")
                                        .fontWeight(.semibold)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                LinearGradient(
                                    colors: [.orange, .red],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .foregroundColor(.white)
                            .cornerRadius(14)
                        }
                        .disabled(entitlementManager.purchaseInProgress)

                        Text("Then \(yearlyProduct.localizedPriceString)/year. Cancel anytime.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Button("No thanks") {
                    dismiss()
                }
                .font(.subheadline)
                .foregroundColor(.secondary)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
            .onAppear {
                AnalyticsService.logWinbackView()
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .foregroundColor(.secondary)
                    }
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
        }
    }
}

struct WinbackValueRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.orange)
                .frame(width: 32)

            Text(text)
                .font(.subheadline)

            Spacer()
        }
    }
}
