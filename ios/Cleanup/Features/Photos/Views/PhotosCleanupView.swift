import SwiftUI
import Photos

struct PhotosCleanupView: View {
    @StateObject private var viewModel = PhotosCleanupViewModel()
    @EnvironmentObject var appState: AppState
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @ObservedObject private var freeUsage = FreeUsageManager.shared
    @StateObject private var archiveViewModel = ArchiveViewModel()
    @State private var showFreeLimitAlert = false
    @State private var freeLimitCount = 0

    private var currentCategory: FreeUsageManager.Category {
        switch appState.photosSelectedSegment {
        case 0: return .duplicatePhotos
        case 1: return .similarPhotos
        default: return .screenshots
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Free tier banner
                if !entitlementManager.isPro {
                    let remaining = freeUsage.remainingFreeActions(for: currentCategory)
                    FreeUsageBanner(remaining: remaining, category: currentCategory)
                }

                // Tab Selector
                Picker("Category", selection: $appState.photosSelectedSegment) {
                    Text("Duplicates").tag(0)
                    Text("Similar").tag(1)
                    Text("Screenshots").tag(2)
                }
                .pickerStyle(.segmented)
                .padding()

                // Content
                TabView(selection: $appState.photosSelectedSegment) {
                    DuplicatesListView(viewModel: viewModel)
                        .tag(0)

                    SimilarPhotosListView(viewModel: viewModel)
                        .tag(1)

                    ScreenshotsListView(viewModel: viewModel)
                        .tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .navigationTitle("Photos Cleanup")
            .toolbar(content: {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 12) {
                        Button {
                            let assets = viewModel.getSelectedPhotoAssets()
                            let totalBytes = assets.reduce(Int64(0)) { $0 + $1.fileSize }
                            let result = paywallCoordinator.checkArchiveAccess(
                                requiredBytes: totalBytes,
                                context: .attemptArchive(count: assets.count, totalGB: Double(totalBytes) / 1_073_741_824)
                            )
                            if result == .allowed {
                                archiveViewModel.archiveSelectedPhotos(assets)
                                viewModel.selectedAssets.removeAll()
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "icloud.and.arrow.up")
                                Text("Archive")
                                Text("\(viewModel.selectedCount)")
                            }
                            .font(.subheadline)
                        }
                        .tint(.cyan)
                        .opacity(viewModel.selectedCount > 0 ? 1 : 0)
                        .disabled(viewModel.selectedCount == 0)

                        Button {
                            attemptDelete()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "trash.fill")
                                Text("Delete")
                                Text("\(viewModel.selectedCount)")
                            }
                            .font(.subheadline)
                        }
                        .tint(.red)
                        .opacity(viewModel.selectedCount > 0 ? 1 : 0)
                        .disabled(viewModel.selectedCount == 0)
                    }
                }
            })
            .onAppear {
                if !appState.hasPhotoPermission {
                    Task {
                        await appState.requestPhotoPermission()
                    }
                }
                viewModel.scan()
            }
            .confirmationDialog(
                "What would you like to do with \(viewModel.selectedCount) photos?",
                isPresented: $viewModel.showDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button("Delete Permanently", role: .destructive) {
                    viewModel.deleteSelected()
                    if !entitlementManager.isPro {
                        freeUsage.recordUsage(count: viewModel.selectedCount, category: currentCategory)
                    }
                }

                Button("Archive to Cloud") {
                    let assets = viewModel.getSelectedPhotoAssets()
                    let totalBytes = assets.reduce(Int64(0)) { $0 + $1.fileSize }
                    let result = paywallCoordinator.checkArchiveAccess(
                        requiredBytes: totalBytes,
                        context: .attemptArchive(count: assets.count, totalGB: Double(totalBytes) / 1_073_741_824)
                    )
                    if result == .allowed {
                        archiveViewModel.archiveSelectedPhotos(assets)
                                viewModel.selectedAssets.removeAll()
                    }
                }

                Button("Cancel", role: .cancel) {}
            } message: {
                if entitlementManager.hasArchiveSubscription {
                    Text("Archived items are safely stored in the cloud and can be retrieved later.")
                } else {
                    Text("This action cannot be undone. Consider archiving to cloud storage instead.")
                }
            }
            .sheet(isPresented: $archiveViewModel.showSignIn) {
                ArchiveSignInView(onSignIn: { archiveViewModel.onSignInComplete() })
            }
            .sheet(isPresented: $paywallCoordinator.showArchivePaywall) {
                RemoteArchivePaywallView()
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $archiveViewModel.showArchiveUpgradePaywall) {
                RemoteArchivePaywallView()
                    .presentationDetents([.large])
            }
            .alert("Archive Error", isPresented: .init(
                get: { archiveViewModel.errorMessage != nil },
                set: { if !$0 { archiveViewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(archiveViewModel.errorMessage ?? "")
            }
            .alert("Archived Successfully", isPresented: $archiveViewModel.showArchiveSuccess) {
                Button("OK", role: .cancel) {
                    viewModel.rescan()
                }
            } message: {
                Text("\(archiveViewModel.archiveSuccessCount) items have been archived to the cloud. They are uploading in the background.")
            }
            .alert("Free Limit", isPresented: $showFreeLimitAlert) {
                Button("Delete \(freeLimitCount)", role: .destructive) {
                    viewModel.limitSelectionTo(freeLimitCount)
                    viewModel.showDeleteConfirmation = true
                }
                Button("Upgrade to Pro") {
                    let context: PaywallContext = .attemptDeleteDuplicates(count: viewModel.selectedCount, savedGB: viewModel.selectedTotalBytes / 1_000_000_000)
                    paywallCoordinator.showPaywall(context: context)
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You can delete \(freeLimitCount) more items for free. Upgrade to Pro for unlimited deletions.")
            }
            .overlay {
                if viewModel.isScanning {
                    ScanningOverlayView(progress: viewModel.scanProgress, message: "Scanning photos...")
                }
            }
            .onChange(of: entitlementManager.isPro) { isPro in
                if isPro, let pending = paywallCoordinator.pendingAction {
                    switch pending {
                    case .deleteDuplicates, .deleteSimilar, .deleteScreenshots:
                        viewModel.showDeleteConfirmation = true
                        paywallCoordinator.clearPendingAction()
                    default:
                        break
                    }
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            viewModel.showDeleteConfirmation = true
            return
        }

        let category = currentCategory
        let remaining = freeUsage.remainingFreeActions(for: category)

        if remaining <= 0 {
            // No free deletions left - show paywall
            let selectedIds = viewModel.selectedAssetIds
            let savedGB = viewModel.selectedTotalBytes / 1_000_000_000

            let context: PaywallContext
            let pendingAction: PendingCleanupAction

            switch appState.photosSelectedSegment {
            case 0:
                context = .attemptDeleteDuplicates(count: viewModel.selectedCount, savedGB: savedGB)
                pendingAction = .deleteDuplicates(ids: selectedIds)
            case 1:
                context = .attemptDeleteSimilar(count: viewModel.selectedCount, savedGB: savedGB)
                pendingAction = .deleteSimilar(ids: selectedIds)
            default:
                context = .attemptDeleteScreenshots(count: viewModel.selectedCount, savedGB: savedGB)
                pendingAction = .deleteScreenshots(ids: selectedIds)
            }

            paywallCoordinator.showPaywall(context: context, pendingAction: pendingAction)
        } else if viewModel.selectedCount <= remaining {
            // All selected items fit within free limit
            viewModel.showDeleteConfirmation = true
        } else {
            // Some items exceed free limit - offer partial deletion
            freeLimitCount = remaining
            showFreeLimitAlert = true
        }
    }
}

// MARK: - Duplicates List
struct DuplicatesListView: View {
    @ObservedObject var viewModel: PhotosCleanupViewModel

    var body: some View {
        Group {
            if viewModel.duplicateGroups.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "checkmark.circle.fill",
                    title: "No Duplicates Found",
                    subtitle: "Your photo library is clean!"
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(viewModel.duplicateGroups) { group in
                            DuplicateGroupCard(group: group, viewModel: viewModel)
                        }
                    }
                    .padding()
                }
            }
        }
    }
}

struct DuplicateGroupCard: View {
    let group: DuplicateGroup
    @ObservedObject var viewModel: PhotosCleanupViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(group.assets.count) duplicates")
                    .font(.headline)
                Spacer()
                Text(formatBytes(group.potentialSavings))
                    .font(.subheadline)
                    .foregroundColor(.green)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(group.assets) { asset in
                        PhotoThumbnailView(
                            asset: asset,
                            isSelected: viewModel.isSelected(asset),
                            isBest: asset.id == group.bestAssetId
                        ) {
                            viewModel.toggleSelection(asset)
                        }
                    }
                }
            }

            HStack {
                Button("Keep Best") {
                    viewModel.selectAllExceptBest(in: group)
                }
                .buttonStyle(.bordered)

                Spacer()

                Button("Select All") {
                    viewModel.selectAll(in: group)
                }
                .buttonStyle(.bordered)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
    }

    private func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}

// MARK: - Similar Photos List
struct SimilarPhotosListView: View {
    @ObservedObject var viewModel: PhotosCleanupViewModel

    var body: some View {
        Group {
            if viewModel.similarGroups.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "square.stack.3d.up.fill",
                    title: "No Similar Photos",
                    subtitle: "No groups of similar photos found"
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(viewModel.similarGroups) { group in
                            SimilarGroupCard(group: group, viewModel: viewModel)
                        }
                    }
                    .padding()
                }
            }
        }
    }
}

struct SimilarGroupCard: View {
    let group: SimilarPhotoGroup
    @ObservedObject var viewModel: PhotosCleanupViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(group.assets.count) similar photos")
                    .font(.headline)
                Spacer()
                if let date = group.assets.first?.creationDate {
                    Text(date, style: .date)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(group.assets) { asset in
                        PhotoThumbnailView(
                            asset: asset,
                            isSelected: viewModel.isSelected(asset),
                            isBest: asset.id == group.bestAssetId
                        ) {
                            viewModel.toggleSelection(asset)
                        }
                    }
                }
            }

            Button("Keep Best, Select Others") {
                viewModel.selectSuggestedToDelete(in: group)
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
    }
}

// MARK: - Screenshots List
struct ScreenshotsListView: View {
    @ObservedObject var viewModel: PhotosCleanupViewModel
    @State private var selectAll = false

    var body: some View {
        Group {
            if viewModel.screenshots.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "camera.viewfinder",
                    title: "No Screenshots",
                    subtitle: "No screenshots found in your library"
                )
            } else {
                VStack(spacing: 0) {
                    // Header
                    HStack {
                        Text("\(viewModel.screenshots.count) screenshots")
                            .font(.headline)
                        Spacer()
                        Button(selectAll ? "Deselect All" : "Select All") {
                            selectAll.toggle()
                            if selectAll {
                                viewModel.selectAllScreenshots()
                            } else {
                                viewModel.deselectAllScreenshots()
                            }
                        }
                    }
                    .padding()

                    // Grid
                    ScrollView {
                        LazyVGrid(columns: [
                            GridItem(.flexible()),
                            GridItem(.flexible()),
                            GridItem(.flexible())
                        ], spacing: 4) {
                            ForEach(viewModel.screenshots) { asset in
                                PhotoThumbnailView(
                                    asset: asset,
                                    isSelected: viewModel.isSelected(asset),
                                    isBest: false
                                ) {
                                    viewModel.toggleSelection(asset)
                                }
                                .aspectRatio(1, contentMode: .fill)
                            }
                        }
                        .padding(4)
                    }
                }
            }
        }
    }
}

// MARK: - Photo Thumbnail
struct PhotoThumbnailView: View {
    let asset: PhotoAsset
    let isSelected: Bool
    let isBest: Bool
    let onTap: () -> Void

    @State private var image: UIImage?

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if let image = image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 100, height: 100)
                    .clipped()
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: 100, height: 100)
                    .overlay {
                        ProgressView()
                    }
            }

            // Selection indicator
            if isSelected {
                Circle()
                    .fill(Color.blue)
                    .frame(width: 24, height: 24)
                    .overlay {
                        Image(systemName: "checkmark")
                            .font(.caption)
                            .foregroundColor(.white)
                    }
                    .padding(4)
            }

            // Best badge
            if isBest {
                VStack {
                    Spacer()
                    HStack {
                        Text("BEST")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.green)
                            .cornerRadius(4)
                        Spacer()
                    }
                    .padding(4)
                }
            }
        }
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.blue : Color.clear, lineWidth: 3)
        )
        .onTapGesture {
            onTap()
        }
        .onAppear {
            loadImage()
        }
    }

    private func loadImage() {
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast

        PHImageManager.default().requestImage(
            for: asset.asset,
            targetSize: CGSize(width: 200, height: 200),
            contentMode: .aspectFill,
            options: options
        ) { result, _ in
            if let result = result {
                self.image = result
            }
        }
    }
}

// MARK: - Empty State
struct EmptyStateView: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 60))
                .foregroundColor(.green)

            Text(title)
                .font(.title2)
                .fontWeight(.semibold)

            Text(subtitle)
                .font(.body)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Scanning Overlay
struct ScanningOverlayView: View {
    let progress: Double
    let message: String

    var body: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                ProgressView()
                    .scaleEffect(1.5)

                Text(message)
                    .font(.headline)
                    .foregroundColor(.white)

                if progress > 0 {
                    ProgressView(value: progress)
                        .frame(width: 200)
                        .tint(.white)

                    Text("\(Int(progress * 100))%")
                        .font(.caption)
                        .foregroundColor(.white)
                }
            }
            .padding(30)
            .background(Color(.systemGray5).opacity(0.9))
            .cornerRadius(20)
        }
    }
}

#Preview {
    PhotosCleanupView()
        .environmentObject(AppState())
}
