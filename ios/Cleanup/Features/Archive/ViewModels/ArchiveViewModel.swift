import SwiftUI
import Photos

@MainActor
class ArchiveViewModel: ObservableObject {
    @Published var archivedItems: [ArchivedItem] = []
    @Published var quota: ArchiveQuota?
    @Published var isLoading = false
    @Published var showSignIn = false
    @Published var showArchiveUpgradePaywall = false
    @Published var showArchiveSuccess = false
    @Published var archiveSuccessCount = 0
    @Published var showRestoreSuccess = false
    @Published var errorMessage: String?
    @Published var itemsToArchive: [ArchivableItem] = []

    private let archiveService = ArchiveService.shared
    private let authService = ArchiveAuthService.shared
    private let entitlementManager = EntitlementManager.shared

    // MARK: - Archivable Item Wrapper

    enum ArchivableItem {
        case photo(PhotoAsset)
        case video(VideoAsset)
        case contact(ContactItem)
        case vault(VaultItem)

        var fileSize: Int64 {
            switch self {
            case .photo(let p): return p.fileSize
            case .video(let v): return v.fileSize
            case .contact: return 1024
            case .vault(let v): return v.fileSize
            }
        }
    }

    var totalArchiveSize: Int64 {
        itemsToArchive.reduce(0) { $0 + $1.fileSize }
    }

    var totalArchiveSizeFormatted: String {
        ByteCountFormatter.string(fromByteCount: totalArchiveSize, countStyle: .file)
    }

    var archiveItemCount: Int {
        itemsToArchive.count
    }

    // MARK: - Filter

    enum ArchiveFilter: String, CaseIterable {
        case all = "All"
        case photos = "Photos"
        case videos = "Videos"
        case contacts = "Contacts"
    }

    @Published var selectedFilter: ArchiveFilter = .all

    var filteredItems: [ArchivedItem] {
        switch selectedFilter {
        case .all: return archivedItems
        case .photos: return archivedItems.filter { $0.fileType == .photo }
        case .videos: return archivedItems.filter { $0.fileType == .video }
        case .contacts: return archivedItems.filter { $0.fileType == .contact }
        }
    }

    // MARK: - Load

    func loadArchivedItems() {
        Task {
            isLoading = true
            archivedItems = await archiveService.getArchivedItems()
            do {
                quota = try await archiveService.getQuota()
            } catch {
                quota = archiveService.getCachedQuota()
            }
            isLoading = false
        }
    }

    func syncWithServer() {
        Task {
            do {
                try await archiveService.syncItems()
                archivedItems = await archiveService.getArchivedItems()
                quota = try await archiveService.getQuota()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Archive Actions

    func archiveSelectedPhotos(_ assets: [PhotoAsset]) {
        itemsToArchive = assets.map { .photo($0) }
        checkAuthAndArchive()
    }

    func archiveSelectedVideos(_ assets: [VideoAsset]) {
        itemsToArchive = assets.map { .video($0) }
        checkAuthAndArchive()
    }

    func archiveSelectedContacts(_ contacts: [ContactItem]) {
        itemsToArchive = contacts.map { .contact($0) }
        checkAuthAndArchive()
    }

    func archiveVaultItems(_ items: [VaultItem]) {
        itemsToArchive = items.map { .vault($0) }
        checkAuthAndArchive()
    }

    private func checkAuthAndArchive() {
        Task {
            let isReady = await authService.isFullySetUp
            if isReady {
                confirmArchive()
            } else {
                showSignIn = true
            }
        }
    }

    func onSignInComplete() {
        showSignIn = false
        confirmArchive()
    }

    func confirmArchive() {
        Task {
            isLoading = true
            errorMessage = nil

            // Ensure backend registration is complete and subscription is synced
            do {
                try await authService.ensureRegistered()
                try await authService.syncSubscription()
            } catch {
                errorMessage = "Failed to connect to archive service: \(error.localizedDescription)"
                isLoading = false
                return
            }

            // Pre-flight quota check using local subscription tier
            let quotaLimit: Int64
            if let tier = entitlementManager.currentArchiveTier {
                quotaLimit = tier.storageLimitBytes
            } else {
                quotaLimit = 0
            }

            var usedBytes: Int64 = 0
            do {
                let freshQuota = try await archiveService.getQuota()
                usedBytes = freshQuota.usedBytes
            } catch {
                if let cached = archiveService.getCachedQuota() {
                    usedBytes = cached.usedBytes
                }
            }

            let remainingBytes = max(0, quotaLimit - usedBytes)
            if quotaLimit > 0 && remainingBytes < totalArchiveSize {
                showArchiveUpgradePaywall = true
                isLoading = false
                return
            }

            // Archive all items WITHOUT deleting originals
            var archivedAssets: [PHAsset] = []
            var archiveSucceeded = false
            for item in itemsToArchive {
                do {
                    switch item {
                    case .photo(let asset):
                        _ = try await archiveService.archiveAsset(
                            asset.asset, tier: .instant, deleteAfterArchive: false
                        )
                        archivedAssets.append(asset.asset)
                    case .video(let asset):
                        _ = try await archiveService.archiveAsset(
                            asset.asset, tier: .instant, deleteAfterArchive: false
                        )
                        archivedAssets.append(asset.asset)
                    case .contact(let contact):
                        _ = try await archiveService.archiveContacts(
                            [contact], tier: .instant
                        )
                    case .vault(let vaultItem):
                        _ = try await archiveService.archiveVaultItem(
                            vaultItem, tier: .instant
                        )
                    }
                    archiveSucceeded = true
                } catch {
                    errorMessage = "Archive failed: \(error.localizedDescription)"
                    break
                }
            }

            // Batch delete originals with a single permission prompt AFTER all archives succeed
            if archiveSucceeded && !archivedAssets.isEmpty {
                do {
                    try await PHPhotoLibrary.shared().performChanges {
                        PHAssetChangeRequest.deleteAssets(archivedAssets as NSFastEnumeration)
                    }
                } catch {
                    // Deletion denied or failed — archive still succeeded, just don't delete originals
                }
            }

            isLoading = false

            if archiveSucceeded && errorMessage == nil {
                let count = itemsToArchive.count
                itemsToArchive.removeAll()
                archiveSuccessCount = count
                showArchiveSuccess = true
                loadArchivedItems()
            }
        }
    }

    // MARK: - Retrieval

    func initiateRetrieval(for item: ArchivedItem) {
        Task {
            do {
                let response = try await archiveService.initiateRetrieval(itemId: item.id)
                if response.status == "available", let urlString = response.downloadURL,
                   let url = URL(string: urlString) {
                    try await archiveService.downloadAndRestore(itemId: item.id, downloadURL: url)
                    // Remove from archive after successful restore
                    try? await archiveService.deleteArchivedItem(itemId: item.id)
                    archivedItems.removeAll { $0.id == item.id }
                    showRestoreSuccess = true
                }
                refreshQuota()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func checkRetrievalStatus(for item: ArchivedItem) {
        Task {
            do {
                let response = try await archiveService.checkRetrievalStatus(itemId: item.id)
                if response.status == "available", let urlString = response.downloadURL,
                   let url = URL(string: urlString) {
                    try await archiveService.downloadAndRestore(itemId: item.id, downloadURL: url)
                    try? await archiveService.deleteArchivedItem(itemId: item.id)
                    archivedItems.removeAll { $0.id == item.id }
                    showRestoreSuccess = true
                }
                refreshQuota()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Delete

    func deleteArchivedItem(_ item: ArchivedItem) {
        Task {
            do {
                try await archiveService.deleteArchivedItem(itemId: item.id)
                archivedItems.removeAll { $0.id == item.id }
                refreshQuota()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func refreshQuota() {
        Task {
            do {
                quota = try await archiveService.getQuota()
            } catch {
                quota = archiveService.getCachedQuota()
            }
        }
    }
}
