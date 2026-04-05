import SwiftUI
import Photos
import AppTrackingTransparency

struct OnboardingView: View {
    @EnvironmentObject var appState: AppState
    @Binding var hasCompletedOnboarding: Bool
    @State private var currentPage = 0
    @State private var storageUsed: String = ""
    @State private var storageTotal: String = ""
    @State private var storagePercentage: Double = 0
    @State private var isRequestingPermission = false
    @State private var scanComplete = false
    @State private var estimatedSavings: String = "..."
    @State private var showOnboardingPaywall = false

    var body: some View {
        ZStack {
            // Background
            LinearGradient(
                colors: [Color(.systemBackground), Color.blue.opacity(0.05)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            TabView(selection: $currentPage) {
                // Page 1: Welcome + Storage visual
                welcomePage.tag(0)
                // Page 2: Permission + Scan
                scanPage.tag(1)
                // Page 3: Results + CTA
                resultsPage.tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: currentPage)
        }
    }

    // MARK: - Page 1: Welcome
    private var welcomePage: some View {
        VStack(spacing: 32) {
            Spacer()

            // App icon area
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.blue.opacity(0.2), .purple.opacity(0.2)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 120, height: 120)

                Image(systemName: "sparkles")
                    .font(.system(size: 56))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            }

            VStack(spacing: 12) {
                Text("Your phone is full of clutter")
                    .font(.title)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("Duplicate photos, old screenshots, large videos — they're eating your storage. Let's fix that.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            // Storage bar preview
            if let info = appState.storageInfo {
                VStack(spacing: 8) {
                    HStack {
                        Text("\(info.usedFormatted) used")
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Spacer()
                        Text("\(info.totalFormatted) total")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.gray.opacity(0.2))
                            RoundedRectangle(cornerRadius: 8)
                                .fill(
                                    LinearGradient(
                                        colors: info.usedPercentage > 0.8 ? [.red, .orange] : [.blue, .purple],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .frame(width: geometry.size.width * CGFloat(info.usedPercentage))
                        }
                    }
                    .frame(height: 16)

                    if info.usedPercentage > 0.7 {
                        Text("Your storage is \(Int(info.usedPercentage * 100))% full")
                            .font(.caption)
                            .foregroundColor(.red)
                            .fontWeight(.medium)
                    }
                }
                .padding(.horizontal, 32)
            }

            Spacer()

            Button {
                withAnimation { currentPage = 1 }
            } label: {
                Text("Let's Clean Up")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundColor(.white)
                    .cornerRadius(14)
            }
            .padding(.horizontal, 24)

            pageIndicator(current: 0, total: 3)
                .padding(.bottom, 24)
        }
    }

    // MARK: - Page 2: Permission + Scan
    private var scanPage: some View {
        VStack(spacing: 32) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.15))
                    .frame(width: 120, height: 120)

                if isRequestingPermission {
                    ProgressView()
                        .scaleEffect(2)
                } else {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 50))
                        .foregroundColor(.blue)
                }
            }

            VStack(spacing: 12) {
                Text("Quick scan your library")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("We'll scan your photos and videos to find duplicates, similar shots, and space hogs. Everything stays on your device.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            // Permission features
            VStack(spacing: 16) {
                PermissionFeatureRow(icon: "lock.shield.fill", title: "100% Private", subtitle: "All scanning happens on-device")
                PermissionFeatureRow(icon: "bolt.fill", title: "Fast Scan", subtitle: "Takes less than 30 seconds")
                PermissionFeatureRow(icon: "trash.fill", title: "You Choose", subtitle: "Nothing deleted without your approval")
            }
            .padding(.horizontal, 32)

            Spacer()

            Button {
                Task {
                    isRequestingPermission = true
                    await appState.requestPhotoPermission()
                    isRequestingPermission = false
                    withAnimation { currentPage = 2 }
                }
            } label: {
                Text("Scan My Library")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundColor(.white)
                    .cornerRadius(14)
            }
            .padding(.horizontal, 24)

            Button("Skip for now") {
                withAnimation { currentPage = 2 }
            }
            .font(.subheadline)
            .foregroundColor(.secondary)

            pageIndicator(current: 1, total: 3)
                .padding(.bottom, 24)
        }
    }

    // MARK: - Page 3: Results + Start
    private var resultsPage: some View {
        VStack(spacing: 24) {
            Spacer()

            VStack(spacing: 8) {
                Text("Two ways to reclaim space")
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("Use one or both — they work independently.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            // Pro card
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "trash.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(LinearGradient(colors: [.blue, .purple], startPoint: .leading, endPoint: .trailing))
                        .cornerRadius(10)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("SmartSpace Pro")
                            .font(.subheadline)
                            .fontWeight(.bold)
                        Text("Delete duplicates, videos & contacts")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text("FREE TRIAL")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(Color.blue)
                        .cornerRadius(5)
                }

                VStack(alignment: .leading, spacing: 6) {
                    OnboardingFeatureRow(icon: "doc.on.doc.fill", text: "Remove duplicate & similar photos")
                    OnboardingFeatureRow(icon: "video.fill", text: "Clean up large videos")
                    OnboardingFeatureRow(icon: "person.2.fill", text: "Merge duplicate contacts")
                }
            }
            .padding(14)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(14)
            .padding(.horizontal, 24)

            // Archive card
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "shield.checkered")
                        .font(.title3)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(LinearGradient(colors: [.cyan, .blue], startPoint: .leading, endPoint: .trailing))
                        .cornerRadius(10)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cloud Archive")
                            .font(.subheadline)
                            .fontWeight(.bold)
                        Text("Save to cloud before you delete")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text("SEPARATE")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(Color.cyan)
                        .cornerRadius(5)
                }

                VStack(alignment: .leading, spacing: 6) {
                    OnboardingFeatureRow(icon: "icloud.and.arrow.up", text: "Archive photos, videos & contacts")
                    OnboardingFeatureRow(icon: "arrow.down.to.line", text: "Retrieve anything, anytime")
                    OnboardingFeatureRow(icon: "lock.shield", text: "Encrypted & private — only you can access")
                }
            }
            .padding(14)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(14)
            .padding(.horizontal, 24)

            Spacer()

            Button {
                showOnboardingPaywall = true
            } label: {
                Text("Get Started — It's Free")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundColor(.white)
                    .cornerRadius(14)
            }
            .padding(.horizontal, 24)
            .sheet(isPresented: $showOnboardingPaywall, onDismiss: completeOnboarding) {
                RemotePaywallView()
            }

            pageIndicator(current: 2, total: 3)
                .padding(.bottom, 24)
        }
    }

    private func completeOnboarding() {
        ATTrackingManager.requestTrackingAuthorization { status in
            if status == .authorized {
                AppDelegate.initializeTikTok()
            }
            DispatchQueue.main.async {
                hasCompletedOnboarding = true
            }
        }
    }

    // MARK: - Helpers
    private func pageIndicator(current: Int, total: Int) -> some View {
        HStack(spacing: 8) {
            ForEach(0..<total, id: \.self) { index in
                Circle()
                    .fill(index == current ? Color.blue : Color.gray.opacity(0.3))
                    .frame(width: 8, height: 8)
            }
        }
    }
}

// MARK: - Supporting Views
struct PermissionFeatureRow: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.blue)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()
        }
    }
}

struct OnboardingFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 16)
            Text(text)
                .font(.caption)
                .foregroundColor(.secondary)
            Spacer()
        }
    }
}
