import SwiftUI

struct HomeView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = HomeViewModel()
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 20) {
                        // Winback nudge banner for discouraged users
                        if paywallCoordinator.shouldShowNudgeBanner {
                            WinbackNudgeBanner(viewModel: viewModel)
                        }

                        // Storage Overview Card
                        StorageOverviewCard(storageInfo: appState.storageInfo)

                        // Quick Actions
                        QuickActionsSection()

                        // Cleanup Categories
                        CleanupCategoriesSection(viewModel: viewModel)
                            .environmentObject(appState)

                        // Archive Card
                        if viewModel.totalCleanableItems > 0 && !viewModel.isScanning {
                            ArchivePromoCard(viewModel: viewModel)
                                .environmentObject(appState)
                        }
                    }
                    .padding()
                }

                // Floating One Tap Cleanup Button
                OneTapCleanupButton(viewModel: viewModel)
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .background(Color(.systemGroupedBackground).opacity(0.95))
            }
            .navigationTitle("Cleanup: One Tap")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: VaultView()) {
                        Image(systemName: "lock.shield.fill")
                            .foregroundColor(.blue)
                    }
                }
            }
            .refreshable {
                await viewModel.refresh()
                appState.loadStorageInfo()
            }
        }
    }
}

// MARK: - Storage Overview Card
struct StorageOverviewCard: View {
    let storageInfo: StorageInfo?

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Storage")
                    .font(.headline)
                Spacer()
                if let info = storageInfo {
                    Text("\(info.usedFormatted) / \(info.totalFormatted)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            if let info = storageInfo {
                // Progress Bar
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.gray.opacity(0.2))

                        RoundedRectangle(cornerRadius: 8)
                            .fill(storageGradient(percentage: info.usedPercentage))
                            .frame(width: geometry.size.width * CGFloat(info.usedPercentage))
                    }
                }
                .frame(height: 12)

                // Storage Breakdown
                HStack(spacing: 20) {
                    StorageItemView(title: "Photos", size: info.photosSize, color: .blue)
                    StorageItemView(title: "Videos", size: info.videosSize, color: .purple)
                    StorageItemView(title: "Other", size: info.otherSize, color: .gray)
                }
            } else {
                ProgressView()
                    .frame(height: 50)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 10, x: 0, y: 5)
    }

    private func storageGradient(percentage: Double) -> LinearGradient {
        let color: Color = percentage > 0.9 ? .red : percentage > 0.7 ? .orange : .blue
        return LinearGradient(
            colors: [color, color.opacity(0.7)],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

struct StorageItemView: View {
    let title: String
    let size: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
            Text(size)
                .font(.caption)
                .fontWeight(.medium)
        }
    }
}

// MARK: - Quick Actions Section
struct QuickActionsSection: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quick Actions")
                .font(.headline)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    Button {
                        appState.photosSelectedSegment = 0
                        appState.selectedTab = .photos
                    } label: {
                        QuickActionButton(
                            title: "Duplicates",
                            icon: "doc.on.doc.fill",
                            color: .blue
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button {
                        appState.photosSelectedSegment = 2
                        appState.selectedTab = .photos
                    } label: {
                        QuickActionButton(
                            title: "Screenshots",
                            icon: "camera.viewfinder",
                            color: .green
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button {
                        appState.videosSelectedSegment = 0
                        appState.selectedTab = .videos
                    } label: {
                        QuickActionButton(
                            title: "Large Videos",
                            icon: "film.fill",
                            color: .purple
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button {
                        appState.videosSelectedSegment = 1
                        appState.selectedTab = .videos
                    } label: {
                        QuickActionButton(
                            title: "Compress",
                            icon: "arrow.down.right.and.arrow.up.left",
                            color: .orange
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    if entitlementManager.hasArchiveSubscription {
                        NavigationLink(destination: ArchiveView()) {
                            QuickActionButton(
                                title: "Archive",
                                icon: "icloud.fill",
                                color: .cyan
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }
}

struct QuickActionButton: View {
    let title: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.white)
                .frame(width: 50, height: 50)
                .background(color)
                .cornerRadius(12)

            Text(title)
                .font(.caption)
                .foregroundColor(.primary)
        }
        .frame(width: 80)
    }
}

// MARK: - Cleanup Categories Section
struct CleanupCategoriesSection: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Cleanup Categories")
                    .font(.headline)
                if viewModel.isScanning {
                    ProgressView()
                        .scaleEffect(0.7)
                }
            }

            VStack(spacing: 12) {
                Button {
                    appState.photosSelectedSegment = 0
                    appState.selectedTab = .photos
                } label: {
                    CleanupCategoryRow(
                        title: "Duplicate Photos",
                        subtitle: viewModel.isScanning ? nil : "\(viewModel.duplicatePhotosCount) items found",
                        icon: "photo.on.rectangle",
                        color: .blue,
                        potentialSavings: viewModel.isScanning ? nil : viewModel.duplicatePhotosSavings,
                        isLoading: viewModel.isScanning
                    )
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    appState.photosSelectedSegment = 1
                    appState.selectedTab = .photos
                } label: {
                    CleanupCategoryRow(
                        title: "Similar Photos",
                        subtitle: viewModel.isScanning ? nil : "\(viewModel.similarPhotosCount) groups found",
                        icon: "square.stack.3d.up",
                        color: .indigo,
                        potentialSavings: viewModel.isScanning ? nil : viewModel.similarPhotosSavings,
                        isLoading: viewModel.isScanning
                    )
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    appState.photosSelectedSegment = 2
                    appState.selectedTab = .photos
                } label: {
                    CleanupCategoryRow(
                        title: "Screenshots",
                        subtitle: viewModel.isScanning ? nil : "\(viewModel.screenshotsCount) screenshots",
                        icon: "camera.viewfinder",
                        color: .green,
                        potentialSavings: viewModel.isScanning ? nil : viewModel.screenshotsSavings,
                        isLoading: viewModel.isScanning
                    )
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    appState.videosSelectedSegment = 0
                    appState.selectedTab = .videos
                } label: {
                    CleanupCategoryRow(
                        title: "Large Videos",
                        subtitle: viewModel.isScanning ? nil : "\(viewModel.largeVideosCount) videos",
                        icon: "video.fill",
                        color: .purple,
                        potentialSavings: viewModel.isScanning ? nil : viewModel.largeVideosSavings,
                        isLoading: viewModel.isScanning
                    )
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    appState.contactsSelectedSegment = 0
                    appState.selectedTab = .contacts
                } label: {
                    CleanupCategoryRow(
                        title: "Duplicate Contacts",
                        subtitle: viewModel.isScanning ? nil : "\(viewModel.duplicateContactsCount) duplicates",
                        icon: "person.2.fill",
                        color: .orange,
                        potentialSavings: nil,
                        isLoading: viewModel.isScanning
                    )
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
    }
}

struct CleanupCategoryRow: View {
    let title: String
    let subtitle: String?
    let icon: String
    let color: Color
    let potentialSavings: String?
    var isLoading: Bool = false

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.white)
                .frame(width: 44, height: 44)
                .background(color)
                .cornerRadius(10)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                if isLoading {
                    HStack(spacing: 6) {
                        ProgressView()
                            .scaleEffect(0.6)
                        Text("Scanning...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                } else if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            if !isLoading, let savings = potentialSavings, !savings.isEmpty {
                Text(savings)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.green)
            }

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.03), radius: 5, x: 0, y: 2)
    }
}

// MARK: - One Tap Cleanup Button
struct OneTapCleanupButton: View {
    @ObservedObject var viewModel: HomeViewModel
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var showConfirmation = false

    var body: some View {
        Button(action: {
            attemptCleanup()
        }) {
            HStack {
                Image(systemName: "sparkles")
                Text("One Tap Cleanup")
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(
                LinearGradient(
                    colors: [.blue, .purple],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundColor(.white)
            .cornerRadius(16)
        }
        .sheet(isPresented: $showConfirmation) {
            CleanupConfirmationView(viewModel: viewModel)
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .oneTapCleanup:
                    showConfirmation = true
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptCleanup() {
        if entitlementManager.isPro {
            showConfirmation = true
        } else {
            // Calculate total cleanup potential
            let totalItems = viewModel.duplicatePhotosCount +
                            viewModel.similarPhotosCount +
                            viewModel.screenshotsCount +
                            viewModel.largeVideosCount +
                            viewModel.duplicateContactsCount

            // Estimate total GB (rough estimate based on typical item sizes)
            let estimatedGB = Double(viewModel.duplicatePhotosCount + viewModel.similarPhotosCount + viewModel.screenshotsCount) * 0.003 +
                             Double(viewModel.largeVideosCount) * 0.1

            paywallCoordinator.showPaywall(
                context: .attemptCleanup(totalGB: estimatedGB, itemCount: totalItems),
                pendingAction: .oneTapCleanup
            )
        }
    }
}

// MARK: - Winback Nudge Banner
struct WinbackNudgeBanner: View {
    @ObservedObject var viewModel: HomeViewModel
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    private var totalClutterItems: Int {
        viewModel.duplicatePhotosCount +
        viewModel.similarPhotosCount +
        viewModel.screenshotsCount +
        viewModel.largeVideosCount
    }

    var body: some View {
        Button {
            paywallCoordinator.showPaywall(context: .generic)
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [.orange.opacity(0.2), .red.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 44, height: 44)

                    Image(systemName: "sparkles")
                        .font(.title3)
                        .foregroundColor(.orange)
                }

                VStack(alignment: .leading, spacing: 3) {
                    if totalClutterItems > 0 {
                        Text("\(totalClutterItems) items waiting to be cleaned")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                    } else {
                        Text("Unlock unlimited cleanups")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                    }

                    Text("Try Pro free for 3 days")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Text("Try Free")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        LinearGradient(
                            colors: [.orange, .red],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(20)
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(.systemBackground))
                    .shadow(color: .orange.opacity(0.15), radius: 8, x: 0, y: 4)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.orange.opacity(0.2), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Archive Promo Card
struct ArchivePromoCard: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject var viewModel: HomeViewModel
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    private var hasArchive: Bool {
        entitlementManager.hasArchiveSubscription
    }

    var body: some View {
        Button {
            if hasArchive {
                // Navigate to photos cleanup where archive toolbar button exists
                appState.photosSelectedSegment = 0
                appState.selectedTab = .photos
            } else if entitlementManager.isPro {
                paywallCoordinator.showArchivePaywall = true
            } else {
                paywallCoordinator.showPaywall(context: .generic)
            }
        } label: {
            VStack(spacing: 14) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [.cyan.opacity(0.2), .blue.opacity(0.2)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 44, height: 44)

                        Image(systemName: hasArchive ? "icloud.and.arrow.up" : "shield.checkered")
                            .font(.title3)
                            .foregroundColor(.cyan)
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        Text(hasArchive ? "Archive instead of deleting" : "Delete with confidence")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)

                        Text(hasArchive
                             ? "Select items in Photos or Videos, then tap the archive button to save them to the cloud."
                             : "Archive \(viewModel.totalCleanableBytesFormatted) as a safety net before deleting. Retrieve anytime.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    }

                    Spacer()
                }

                HStack {
                    if hasArchive {
                        Text("Archived items can be retrieved anytime from Settings → Cloud Archive.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .italic()
                    } else {
                        Text("Unlike big tech, we help minimize your storage — not maximize it.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .italic()
                    }

                    Spacer()

                    Text(hasArchive ? "Go to Photos" : "From $1.99/mo")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            LinearGradient(
                                colors: [.cyan, .blue],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(.systemBackground))
                    .shadow(color: .cyan.opacity(0.15), radius: 8, x: 0, y: 4)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.cyan.opacity(0.2), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
        .sheet(isPresented: $paywallCoordinator.showArchivePaywall) {
            ArchivePaywallView()
                .presentationDetents([.large])
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(AppState())
}
