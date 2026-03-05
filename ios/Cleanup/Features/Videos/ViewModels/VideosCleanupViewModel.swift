import SwiftUI
import Photos

@MainActor
class VideosCleanupViewModel: ObservableObject {
    @Published var largeVideos: [VideoAsset] = []
    @Published var videosToCompress: [VideoAsset] = []
    @Published var selectedAssets: Set<String> = []
    @Published var isScanning = false
    @Published var scanProgress: Double = 0
    @Published var showDeleteConfirmation = false

    @Published var isCompressing = false
    @Published var compressionProgress: Double = 0
    @Published var currentCompressionVideo = ""

    private let photoService = PhotoAnalysisService.shared
    private let compressionService = VideoCompressionService.shared
    private var hasScanned = false

    var selectedCount: Int {
        selectedAssets.count
    }

    var selectedVideoIds: Set<String> {
        selectedAssets
    }

    var selectedTotalBytes: Double {
        let total = largeVideos.filter { selectedAssets.contains($0.id) }.reduce(Int64(0)) { $0 + $1.fileSize }
        return Double(total)
    }

    var totalSizeFormatted: String {
        let total = largeVideos.reduce(0) { $0 + $1.fileSize }
        return ByteCountFormatter.string(fromByteCount: total, countStyle: .file)
    }

    // MARK: - Scanning
    func scan() {
        guard !isScanning && !hasScanned else { return }

        Task {
            isScanning = true
            scanProgress = 0

            let result = await photoService.findLargeVideos(minimumSize: 50_000_000)

            var videos: [VideoAsset] = []
            for item in result.items {
                let video = VideoAsset(
                    id: item.id,
                    asset: item.asset,
                    fileSize: item.fileSize,
                    duration: item.asset.duration,
                    creationDate: item.creationDate
                )
                videos.append(video)
            }

            largeVideos = videos
            videosToCompress = videos

            scanProgress = 1.0
            hasScanned = true
            isScanning = false
        }
    }

    func rescan() {
        hasScanned = false
        Task { await photoService.invalidateCache() }
        scan()
    }

    // MARK: - Selection for Deletion
    func isSelected(_ video: VideoAsset) -> Bool {
        selectedAssets.contains(video.id)
    }

    func toggleSelection(_ video: VideoAsset) {
        if selectedAssets.contains(video.id) {
            selectedAssets.remove(video.id)
        } else {
            selectedAssets.insert(video.id)
        }
    }

    func getSelectedVideoAssets() -> [VideoAsset] {
        largeVideos.filter { selectedAssets.contains($0.id) }
    }

    func deleteSelected() {
        Task {
            let assetsToDelete = largeVideos.filter { selectedAssets.contains($0.id) }
            let photoAssets = assetsToDelete.map {
                PhotoAsset(
                    id: $0.id,
                    asset: $0.asset,
                    fileSize: $0.fileSize,
                    creationDate: $0.creationDate,
                    mediaType: .video
                )
            }

            do {
                try await photoService.deleteAssets(photoAssets)
                let deletedIds = selectedAssets
                selectedAssets.removeAll()

                // Remove deleted items locally instead of rescanning
                largeVideos = largeVideos.filter { !deletedIds.contains($0.id) }
                videosToCompress = videosToCompress.filter { !deletedIds.contains($0.id) }

                AppReviewManager.requestReviewIfNeeded()
            } catch {
                // Delete failed silently
            }
        }
    }

    // MARK: - Compression Selection
    func toggleCompressionSelection(at index: Int) {
        guard index < videosToCompress.count else { return }
        videosToCompress[index].isSelected.toggle()
    }

    func estimatedSavings(for video: VideoAsset, quality: VideoCompressionService.CompressionQuality) -> String {
        let estimated = Int64(Double(video.fileSize) * quality.estimatedReduction)
        return ByteCountFormatter.string(fromByteCount: estimated, countStyle: .file)
    }

    // MARK: - Compression
    func compressSelected(quality: VideoCompressionService.CompressionQuality) {
        let selected = videosToCompress.filter { $0.isSelected }
        guard !selected.isEmpty else { return }

        Task {
            isCompressing = true

            for (index, video) in selected.enumerated() {
                currentCompressionVideo = "Video \(index + 1) of \(selected.count)"
                compressionProgress = 0

                let result = await compressionService.compressVideo(
                    asset: video.asset,
                    quality: quality
                ) { progress in
                    Task { @MainActor in
                        self.compressionProgress = progress
                    }
                }

                if result.success, let outputURL = result.outputURL {
                    do {
                        try await compressionService.replaceWithCompressed(
                            originalAsset: video.asset,
                            compressedURL: outputURL
                        )
                    } catch {
                        // Replace failed silently
                    }
                }
            }

            isCompressing = false
            // Rescan after compression since file sizes changed
            rescan()
            AppReviewManager.requestReviewIfNeeded()
        }
    }
}
