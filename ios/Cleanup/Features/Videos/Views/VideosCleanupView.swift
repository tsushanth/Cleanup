import SwiftUI
import Photos

struct VideosCleanupView: View {
    @StateObject private var viewModel = VideosCleanupViewModel()
    @EnvironmentObject var appState: AppState
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @ObservedObject private var freeUsage = FreeUsageManager.shared
    @StateObject private var archiveViewModel = ArchiveViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Free tier banner
                if !entitlementManager.isPro {
                    FreeUsageBanner(
                        remaining: freeUsage.remainingFreeActions(for: .videos),
                        category: .videos
                    )
                }

                // Tab Selector
                Picker("Category", selection: $appState.videosSelectedSegment) {
                    Text("Large Videos").tag(0)
                    Text("Compress").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()

                TabView(selection: $appState.videosSelectedSegment) {
                    LargeVideosListView(viewModel: viewModel)
                        .tag(0)

                    CompressVideosView(viewModel: viewModel, entitlementManager: entitlementManager, paywallCoordinator: paywallCoordinator)
                        .tag(1)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .navigationTitle("Videos Cleanup")
            .toolbar(content: {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 12) {
                        Button {
                            let assets = viewModel.getSelectedVideoAssets()
                            let totalBytes = assets.reduce(Int64(0)) { $0 + $1.fileSize }
                            let result = paywallCoordinator.checkArchiveAccess(
                                requiredBytes: totalBytes,
                                context: .attemptArchive(count: assets.count, totalGB: Double(totalBytes) / 1_073_741_824)
                            )
                            if result == .allowed {
                                archiveViewModel.archiveSelectedVideos(assets)
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
                        .opacity(viewModel.selectedCount > 0 && appState.videosSelectedSegment == 0 ? 1 : 0)
                        .disabled(viewModel.selectedCount == 0 || appState.videosSelectedSegment != 0)

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
                        .opacity(viewModel.selectedCount > 0 && appState.videosSelectedSegment == 0 ? 1 : 0)
                        .disabled(viewModel.selectedCount == 0 || appState.videosSelectedSegment != 0)
                    }
                }
            })
            .onAppear {
                viewModel.scan()
            }
            .confirmationDialog(
                "What would you like to do with \(viewModel.selectedCount) videos?",
                isPresented: $viewModel.showDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button("Delete Permanently", role: .destructive) {
                    viewModel.deleteSelected()
                    if !entitlementManager.isPro {
                        freeUsage.recordUsage(count: viewModel.selectedCount, category: .videos)
                    }
                }

                Button("Archive to Cloud") {
                    let assets = viewModel.getSelectedVideoAssets()
                    let totalBytes = assets.reduce(Int64(0)) { $0 + $1.fileSize }
                    let result = paywallCoordinator.checkArchiveAccess(
                        requiredBytes: totalBytes,
                        context: .attemptArchive(count: assets.count, totalGB: Double(totalBytes) / 1_073_741_824)
                    )
                    if result == .allowed {
                        archiveViewModel.archiveSelectedVideos(assets)
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
                ArchivePaywallView()
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $archiveViewModel.showArchiveUpgradePaywall) {
                ArchivePaywallView()
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
            .overlay {
                if viewModel.isScanning {
                    ScanningOverlayView(progress: viewModel.scanProgress, message: "Scanning videos...")
                }
            }
            .onChange(of: entitlementManager.isPro) { isPro in
                if isPro, let pending = paywallCoordinator.pendingAction {
                    switch pending {
                    case .deleteVideos:
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

        let remaining = freeUsage.remainingFreeActions(for: .videos)

        if remaining <= 0 {
            let selectedIds = viewModel.selectedVideoIds
            let savedGB = viewModel.selectedTotalBytes / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptDeleteVideos(count: viewModel.selectedCount, savedGB: savedGB),
                pendingAction: .deleteVideos(ids: selectedIds)
            )
        } else if viewModel.selectedCount <= remaining {
            viewModel.showDeleteConfirmation = true
        } else {
            // Partial free deletion - show paywall with context
            let savedGB = viewModel.selectedTotalBytes / 1_000_000_000
            paywallCoordinator.showPaywall(
                context: .attemptDeleteVideos(count: viewModel.selectedCount, savedGB: savedGB),
                pendingAction: .deleteVideos(ids: viewModel.selectedVideoIds)
            )
        }
    }
}

// MARK: - Large Videos List
struct LargeVideosListView: View {
    @ObservedObject var viewModel: VideosCleanupViewModel

    var body: some View {
        Group {
            if viewModel.largeVideos.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "checkmark.circle.fill",
                    title: "No Large Videos",
                    subtitle: "No videos over 50MB found"
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        // Summary Header
                        HStack {
                            Text("\(viewModel.largeVideos.count) large videos")
                                .font(.headline)
                            Spacer()
                            Text(viewModel.totalSizeFormatted)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal)

                        ForEach(viewModel.largeVideos) { video in
                            VideoRowView(
                                video: video,
                                isSelected: viewModel.isSelected(video)
                            ) {
                                viewModel.toggleSelection(video)
                            }
                        }
                    }
                    .padding()
                }
            }
        }
    }
}

struct VideoRowView: View {
    let video: VideoAsset
    let isSelected: Bool
    let onTap: () -> Void

    @State private var thumbnail: UIImage?

    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail
            ZStack {
                if let thumbnail = thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 80, height: 60)
                        .clipped()
                } else {
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 80, height: 60)
                }

                // Duration overlay
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Text(video.durationFormatted)
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.7))
                            .cornerRadius(4)
                    }
                }
                .padding(4)
            }
            .cornerRadius(8)

            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(video.creationDate?.formatted(date: .abbreviated, time: .omitted) ?? "Unknown date")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(video.fileSizeFormatted)
                    .font(.caption)
                    .foregroundColor(.orange)
            }

            Spacer()

            // Selection
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title2)
                .foregroundColor(isSelected ? .blue : .gray)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.03), radius: 5, x: 0, y: 2)
        .onTapGesture {
            onTap()
        }
        .onAppear {
            loadThumbnail()
        }
    }

    private func loadThumbnail() {
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic

        PHImageManager.default().requestImage(
            for: video.asset,
            targetSize: CGSize(width: 160, height: 120),
            contentMode: .aspectFill,
            options: options
        ) { result, _ in
            if let result = result {
                self.thumbnail = result
            }
        }
    }
}

// MARK: - Compress Videos View
struct CompressVideosView: View {
    @ObservedObject var viewModel: VideosCleanupViewModel
    @ObservedObject var entitlementManager: EntitlementManager
    @ObservedObject var paywallCoordinator: PaywallCoordinator
    @State private var selectedQuality: VideoCompressionService.CompressionQuality = .medium

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Quality Selector
                VStack(alignment: .leading, spacing: 12) {
                    Text("Compression Quality")
                        .font(.headline)

                    ForEach(VideoCompressionService.CompressionQuality.allCases, id: \.self) { quality in
                        QualityOptionView(
                            quality: quality,
                            isSelected: selectedQuality == quality
                        ) {
                            selectedQuality = quality
                        }
                    }
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(12)

                // Videos to Compress
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Select Videos")
                            .font(.headline)
                        Spacer()
                        Text("\(viewModel.videosToCompress.filter { $0.isSelected }.count) selected")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    ForEach(Array(viewModel.videosToCompress.enumerated()), id: \.element.id) { index, video in
                        CompressVideoRowView(
                            video: video,
                            estimatedSavings: viewModel.estimatedSavings(for: video, quality: selectedQuality)
                        ) {
                            viewModel.toggleCompressionSelection(at: index)
                        }
                    }
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(12)

                // Compress Button
                if viewModel.videosToCompress.contains(where: { $0.isSelected }) {
                    Button(action: {
                        attemptCompress()
                    }) {
                        HStack {
                            Image(systemName: "arrow.down.right.and.arrow.up.left")
                            Text("Compress Selected")
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                }
            }
            .padding()
        }
        .overlay {
            if viewModel.isCompressing {
                CompressionProgressView(
                    progress: viewModel.compressionProgress,
                    currentVideo: viewModel.currentCompressionVideo
                )
            }
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .compressVideo:
                    viewModel.compressSelected(quality: selectedQuality)
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptCompress() {
        if entitlementManager.isPro {
            viewModel.compressSelected(quality: selectedQuality)
        } else {
            // Calculate estimated savings
            let selectedVideos = viewModel.videosToCompress.filter { $0.isSelected }
            let totalBytes = selectedVideos.reduce(Int64(0)) { $0 + $1.fileSize }
            let estimatedSavedGB = Double(totalBytes) * 0.5 / 1_000_000_000 // Rough 50% estimate

            paywallCoordinator.showPaywall(
                context: .attemptCompressVideo(savedGB: estimatedSavedGB),
                pendingAction: .compressVideo(id: selectedVideos.first?.id ?? "")
            )
        }
    }
}

struct QualityOptionView: View {
    let quality: VideoCompressionService.CompressionQuality
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(quality.rawValue)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(quality.description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundColor(isSelected ? .blue : .gray)
        }
        .padding()
        .background(isSelected ? Color.blue.opacity(0.1) : Color.clear)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.blue : Color.gray.opacity(0.3), lineWidth: 1)
        )
        .onTapGesture {
            onTap()
        }
    }
}

struct CompressVideoRowView: View {
    let video: VideoAsset
    let estimatedSavings: String
    let onTap: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(video.creationDate?.formatted(date: .abbreviated, time: .omitted) ?? "Video")
                    .font(.subheadline)
                HStack {
                    Text(video.fileSizeFormatted)
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Image(systemName: "arrow.right")
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    Text(estimatedSavings)
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }

            Spacer()

            Image(systemName: video.isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundColor(video.isSelected ? .blue : .gray)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(8)
        .onTapGesture {
            onTap()
        }
    }
}

struct CompressionProgressView: View {
    let progress: Double
    let currentVideo: String

    var body: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Text("Compressing Video")
                    .font(.headline)

                Text(currentVideo)
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                ProgressView(value: progress)
                    .frame(width: 200)

                Text("\(Int(progress * 100))%")
                    .font(.caption)
            }
            .padding(30)
            .background(Color(.systemBackground))
            .cornerRadius(20)
        }
    }
}

#Preview {
    VideosCleanupView()
        .environmentObject(AppState())
}
