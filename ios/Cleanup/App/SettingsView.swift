import SwiftUI
import StoreKit

struct SettingsView: View {
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared

    var body: some View {
        NavigationStack {
            List {
                // Subscription Section
                Section {
                    if entitlementManager.isPro {
                        HStack {
                            Image(systemName: "crown.fill")
                                .foregroundColor(.yellow)
                            VStack(alignment: .leading) {
                                Text("Premium Active")
                                    .font(.headline)
                                Text(entitlementManager.subscriptionTypeDescription)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    } else {
                        Button(action: { paywallCoordinator.showPaywall(context: .generic) }) {
                            HStack {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.orange)
                                VStack(alignment: .leading) {
                                    Text("Upgrade to Premium")
                                        .font(.headline)
                                    Text("Unlock all features")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(.secondary)
                            }
                        }
                        .foregroundColor(.primary)
                    }

                    // Archive Storage Subscriptions
                    if entitlementManager.hasArchiveSubscription {
                        NavigationLink(destination: ArchivePaywallView()) {
                            HStack {
                                Image(systemName: "icloud.fill")
                                    .foregroundColor(.cyan)
                                VStack(alignment: .leading) {
                                    Text("Cloud Archive Active")
                                        .font(.headline)
                                    if let tier = entitlementManager.currentArchiveTier {
                                        Text(tier.displayName + " — Tap to manage")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                        .foregroundColor(.primary)
                    } else if entitlementManager.isPro {
                        NavigationLink(destination: ArchivePaywallView()) {
                            HStack {
                                Image(systemName: "icloud.fill")
                                    .foregroundColor(.cyan)
                                VStack(alignment: .leading) {
                                    Text("Add Cloud Archive")
                                        .font(.headline)
                                    Text("Archive files instead of deleting")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .foregroundColor(.primary)
                    }
                } header: {
                    Text("Subscription")
                }

                // Features Section
                Section {
                    NavigationLink(destination: ChargingAnimationsView()) {
                        SettingsRow(
                            icon: "bolt.fill",
                            iconColor: .yellow,
                            title: "Charging Animations"
                        )
                    }

                    NavigationLink(destination: VaultView()) {
                        SettingsRow(
                            icon: "lock.shield.fill",
                            iconColor: .blue,
                            title: "Secret Space"
                        )
                    }

                    NavigationLink(destination: WidgetsView()) {
                        SettingsRow(
                            icon: "apps.iphone",
                            iconColor: .purple,
                            title: "Widgets"
                        )
                    }

                    NavigationLink(destination: EmailCleanupView()) {
                        SettingsRow(
                            icon: "envelope.fill",
                            iconColor: .green,
                            title: "Email Cleanup"
                        )
                    }

                    NavigationLink(destination: ArchiveView()) {
                        SettingsRow(
                            icon: "icloud.fill",
                            iconColor: .cyan,
                            title: "Cloud Archive"
                        )
                    }
                } header: {
                    Text("Features")
                }

                // Cloud Archive Section
                if entitlementManager.hasArchiveSubscription {
                    Section {
                        NavigationLink(destination: ArchiveView()) {
                            HStack {
                                SettingsRow(
                                    icon: "icloud.fill",
                                    iconColor: .cyan,
                                    title: "Cloud Archive"
                                )
                                Spacer()
                                if let tier = entitlementManager.currentArchiveTier {
                                    Text(tier.displayName)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    } header: {
                        Text("Cloud Archive")
                    }
                }

                // Privacy Section
                Section {
                    HStack {
                        SettingsRow(
                            icon: "wifi.slash",
                            iconColor: .gray,
                            title: "Works Offline"
                        )
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                    }

                    HStack {
                        SettingsRow(
                            icon: "hand.raised.fill",
                            iconColor: .red,
                            title: "No Data Collection"
                        )
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                    }

                    HStack {
                        SettingsRow(
                            icon: entitlementManager.hasArchiveSubscription ? "icloud.and.arrow.up" : "lock.fill",
                            iconColor: .blue,
                            title: entitlementManager.hasArchiveSubscription
                                ? "Encrypted Cloud Archive"
                                : "Files Stay on Device"
                        )
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                    }
                } header: {
                    Text("Privacy")
                } footer: {
                    if entitlementManager.hasArchiveSubscription {
                        Text("Analysis happens on your device. Archived items are encrypted and stored in your private cloud storage. Only you can access them.")
                    } else {
                        Text("Your data never leaves your device. We cannot access your photos, contacts, or any personal information.")
                    }
                }

                // Support Section
                Section {
                    Button(action: rateApp) {
                        SettingsRow(
                            icon: "star.fill",
                            iconColor: .orange,
                            title: "Rate App"
                        )
                    }
                    .foregroundColor(.primary)

                    Button(action: shareApp) {
                        SettingsRow(
                            icon: "square.and.arrow.up",
                            iconColor: .blue,
                            title: "Share App"
                        )
                    }
                    .foregroundColor(.primary)

                    Link(destination: URL(string: "mailto:support@kreativekoala.llc")!) {
                        SettingsRow(
                            icon: "envelope.fill",
                            iconColor: .teal,
                            title: "Contact Support"
                        )
                    }
                    .foregroundColor(.primary)
                } header: {
                    Text("Support")
                }

                // Legal Section
                Section {
                    Link(destination: URL(string: "https://kreativekoala.llc/privacy")!) {
                        SettingsRow(
                            icon: "doc.text.fill",
                            iconColor: .gray,
                            title: "Privacy Policy"
                        )
                    }
                    .foregroundColor(.primary)

                    Link(destination: URL(string: "https://kreativekoala.llc/terms")!) {
                        SettingsRow(
                            icon: "doc.text.fill",
                            iconColor: .gray,
                            title: "Terms of Service"
                        )
                    }
                    .foregroundColor(.primary)

                    Button(action: restorePurchases) {
                        SettingsRow(
                            icon: "arrow.clockwise",
                            iconColor: .blue,
                            title: "Restore Purchases"
                        )
                    }
                    .foregroundColor(.primary)
                } header: {
                    Text("Legal")
                }

                // App Info
                Section {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }

    private func rateApp() {
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
            SKStoreReviewController.requestReview(in: scene)
        }
    }

    private func shareApp() {
        let url = URL(string: "https://apps.apple.com/us/app/cleanup-one-tap/id6758323695")!
        let activityVC = UIActivityViewController(activityItems: [url], applicationActivities: nil)

        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first,
           let rootVC = window.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
    }

    private func restorePurchases() {
        Task {
            await entitlementManager.restorePurchases()
        }
    }
}

struct SettingsRow: View {
    let icon: String
    let iconColor: Color
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.body)
                .foregroundColor(.white)
                .frame(width: 28, height: 28)
                .background(iconColor)
                .cornerRadius(6)

            Text(title)
        }
    }
}

#Preview {
    SettingsView()
}
