import SwiftUI
import Photos

@MainActor
class PhotosCleanupViewModel: ObservableObject {
    @Published var duplicateGroups: [DuplicateGroup] = []
    @Published var similarGroups: [SimilarPhotoGroup] = []
    @Published var screenshots: [PhotoAsset] = []
    @Published var selectedAssets: Set<String> = []
    @Published var isScanning = false
    @Published var scanProgress: Double = 0
    @Published var showDeleteConfirmation = false

    private let photoService = PhotoAnalysisService.shared
    private var hasScanned = false

    var selectedCount: Int {
        selectedAssets.count
    }

    var selectedAssetIds: Set<String> {
        selectedAssets
    }

    var selectedTotalBytes: Double {
        Double(totalSavings)
    }

    var totalSavings: Int64 {
        var total: Int64 = 0

        for group in duplicateGroups {
            for asset in group.assets where selectedAssets.contains(asset.id) {
                total += asset.fileSize
            }
        }

        for group in similarGroups {
            for asset in group.assets where selectedAssets.contains(asset.id) {
                total += asset.fileSize
            }
        }

        for asset in screenshots where selectedAssets.contains(asset.id) {
            total += asset.fileSize
        }

        return total
    }

    // MARK: - Scanning
    func scan() {
        guard !isScanning && !hasScanned else { return }

        Task {
            isScanning = true
            scanProgress = 0

            // Scan duplicates
            scanProgress = 0.1
            let duplicateResult = await photoService.findDuplicates()
            duplicateGroups = duplicateResult.groups
            scanProgress = 0.4

            // Scan similar
            let similarResult = await photoService.findSimilarPhotos()
            similarGroups = similarResult.groups
            scanProgress = 0.7

            // Scan screenshots
            let screenshotResult = await photoService.findScreenshots()
            screenshots = screenshotResult.items
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

    // MARK: - Selection
    func isSelected(_ asset: PhotoAsset) -> Bool {
        selectedAssets.contains(asset.id)
    }

    func toggleSelection(_ asset: PhotoAsset) {
        if selectedAssets.contains(asset.id) {
            selectedAssets.remove(asset.id)
        } else {
            selectedAssets.insert(asset.id)
        }
    }

    func selectAll(in group: DuplicateGroup) {
        for asset in group.assets {
            selectedAssets.insert(asset.id)
        }
    }

    func selectAllExceptBest(in group: DuplicateGroup) {
        for asset in group.assets where asset.id != group.bestAssetId {
            selectedAssets.insert(asset.id)
        }
        if let bestId = group.bestAssetId {
            selectedAssets.remove(bestId)
        }
    }

    func selectSuggestedToDelete(in group: SimilarPhotoGroup) {
        for asset in group.suggestedToDelete {
            selectedAssets.insert(asset.id)
        }
        if let bestId = group.bestAssetId {
            selectedAssets.remove(bestId)
        }
    }

    func selectAllScreenshots() {
        for asset in screenshots {
            selectedAssets.insert(asset.id)
        }
    }

    func deselectAllScreenshots() {
        for asset in screenshots {
            selectedAssets.remove(asset.id)
        }
    }

    func limitSelectionTo(_ count: Int) {
        let limited = Array(selectedAssets.prefix(count))
        selectedAssets = Set(limited)
    }

    // MARK: - Get Selected Assets (for archiving)
    func getSelectedPhotoAssets() -> [PhotoAsset] {
        var result: [PhotoAsset] = []
        for group in duplicateGroups {
            for asset in group.assets where selectedAssets.contains(asset.id) {
                result.append(asset)
            }
        }
        for group in similarGroups {
            for asset in group.assets where selectedAssets.contains(asset.id) {
                result.append(asset)
            }
        }
        for asset in screenshots where selectedAssets.contains(asset.id) {
            result.append(asset)
        }
        return result
    }

    // MARK: - Deletion
    func deleteSelected() {
        Task {
            var assetsToDelete: [PhotoAsset] = []

            for group in duplicateGroups {
                for asset in group.assets where selectedAssets.contains(asset.id) {
                    assetsToDelete.append(asset)
                }
            }

            for group in similarGroups {
                for asset in group.assets where selectedAssets.contains(asset.id) {
                    assetsToDelete.append(asset)
                }
            }

            for asset in screenshots where selectedAssets.contains(asset.id) {
                assetsToDelete.append(asset)
            }

            do {
                try await photoService.deleteAssets(assetsToDelete)
                let deletedIds = selectedAssets
                selectedAssets.removeAll()

                // Remove deleted items locally instead of rescanning
                duplicateGroups = duplicateGroups.compactMap { group in
                    let remaining = group.assets.filter { !deletedIds.contains($0.id) }
                    guard remaining.count > 1 else { return nil }
                    return DuplicateGroup(assets: remaining, bestAssetId: group.bestAssetId)
                }

                similarGroups = similarGroups.compactMap { group in
                    var updated = group
                    updated.assets = group.assets.filter { !deletedIds.contains($0.id) }
                    guard updated.assets.count > 1 else { return nil }
                    return updated
                }

                screenshots = screenshots.filter { !deletedIds.contains($0.id) }

                AppReviewManager.requestReviewIfNeeded()
            } catch {
                // Delete failed silently
            }
        }
    }
}
