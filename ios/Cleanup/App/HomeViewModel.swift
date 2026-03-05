import SwiftUI
import Photos
import Contacts

@MainActor
class HomeViewModel: ObservableObject {
    @Published var duplicatePhotosCount = 0
    @Published var duplicatePhotosSavings = ""
    @Published var similarPhotosCount = 0
    @Published var similarPhotosSavings = ""
    @Published var screenshotsCount = 0
    @Published var screenshotsSavings = ""
    @Published var largeVideosCount = 0
    @Published var largeVideosSavings = ""
    @Published var duplicateContactsCount = 0
    @Published var isScanning = false
    @Published var cleanupItems: [CleanupItem] = []
    @Published var hasPhotoPermission = false
    @Published var hasContactPermission = false

    var totalCleanableItems: Int {
        duplicatePhotosCount + similarPhotosCount + screenshotsCount + largeVideosCount + duplicateContactsCount
    }

    var totalCleanableBytes: Int64 {
        duplicatePhotosBytes + similarPhotosBytes + screenshotsBytes + largeVideosBytes
    }

    var totalCleanableBytesFormatted: String {
        ByteCountFormatter.string(fromByteCount: totalCleanableBytes, countStyle: .file)
    }

    // Stored scan results for actual cleanup
    private(set) var duplicatePhotoAssets: [PhotoAsset] = []
    private(set) var similarPhotoGroups: [SimilarPhotoGroup] = []
    private(set) var screenshotAssets: [PhotoAsset] = []
    private(set) var largeVideoAssets: [PhotoAsset] = []
    private(set) var duplicateContactGroups: [DuplicateContactGroup] = []

    private(set) var duplicatePhotosBytes: Int64 = 0
    private(set) var similarPhotosBytes: Int64 = 0
    private(set) var screenshotsBytes: Int64 = 0
    private(set) var largeVideosBytes: Int64 = 0

    private let photoService = PhotoAnalysisService.shared
    private let contactService = ContactService.shared

    init() {
        Task {
            await requestPermissionsAndScan()
        }
    }

    func refresh() async {
        await photoService.invalidateCache()
        await contactService.invalidateCache()
        await requestPermissionsAndScan()
    }

    private func requestPermissionsAndScan() async {
        // Request photo permission
        let photoStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if photoStatus == .notDetermined {
            let newStatus = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
            hasPhotoPermission = newStatus == .authorized || newStatus == .limited
        } else {
            hasPhotoPermission = photoStatus == .authorized || photoStatus == .limited
        }

        // Request contact permission
        let contactStatus = CNContactStore.authorizationStatus(for: .contacts)
        if contactStatus == .notDetermined {
            let store = CNContactStore()
            do {
                hasContactPermission = try await store.requestAccess(for: .contacts)
            } catch {
                hasContactPermission = false
            }
        } else {
            hasContactPermission = contactStatus == .authorized
        }

        // Now scan with permissions granted
        await scanAll()
    }

    func scanAll() async {
        isScanning = true

        await withTaskGroup(of: Void.self) { group in
            if hasPhotoPermission {
                group.addTask { await self.scanDuplicatePhotos() }
                group.addTask { await self.scanSimilarPhotos() }
                group.addTask { await self.scanScreenshots() }
                group.addTask { await self.scanLargeVideos() }
            }
            if hasContactPermission {
                group.addTask { await self.scanDuplicateContacts() }
            }
        }

        isScanning = false
    }

    private func scanDuplicatePhotos() async {
        let result = await photoService.findDuplicates()
        await MainActor.run {
            duplicatePhotosCount = result.duplicateCount
            duplicatePhotosBytes = result.totalBytes
            duplicatePhotosSavings = formatBytes(result.totalBytes)
            // Collect non-best assets from all groups for one-tap cleanup
            duplicatePhotoAssets = result.groups.flatMap { group in
                group.assets.filter { $0.id != group.bestAssetId }
            }
        }
    }

    private func scanSimilarPhotos() async {
        let result = await photoService.findSimilarPhotos()
        await MainActor.run {
            similarPhotosCount = result.groupCount
            similarPhotosBytes = result.potentialSavings
            similarPhotosSavings = formatBytes(result.potentialSavings)
            similarPhotoGroups = result.groups
        }
    }

    private func scanScreenshots() async {
        let result = await photoService.findScreenshots()
        await MainActor.run {
            screenshotsCount = result.count
            screenshotsBytes = result.totalBytes
            screenshotsSavings = formatBytes(result.totalBytes)
            screenshotAssets = result.items
        }
    }

    private func scanLargeVideos() async {
        let result = await photoService.findLargeVideos()
        await MainActor.run {
            largeVideosCount = result.count
            largeVideosBytes = result.totalBytes
            largeVideosSavings = formatBytes(result.totalBytes)
            largeVideoAssets = result.items
        }
    }

    private func scanDuplicateContacts() async {
        let result = await contactService.findDuplicates()
        await MainActor.run {
            duplicateContactsCount = result.count
            duplicateContactGroups = result.groups
        }
    }

    // MARK: - One Tap Cleanup

    func performCleanup(categories: Set<CleanupCategory>) async -> CleanupResult {
        var totalItems = 0
        var totalBytes: Int64 = 0

        // Delete duplicate photos (keep first/best, delete rest)
        if categories.contains(.duplicatePhotos) && !duplicatePhotoAssets.isEmpty {
            do {
                try await photoService.deleteAssets(duplicatePhotoAssets)
                totalItems += duplicatePhotoAssets.count
                totalBytes += duplicatePhotosBytes
            } catch {
                // Continue with other categories
            }
        }

        // Delete similar photos (keep best per group, delete suggested)
        if categories.contains(.similarPhotos) && !similarPhotoGroups.isEmpty {
            var toDelete: [PhotoAsset] = []
            for group in similarPhotoGroups {
                toDelete.append(contentsOf: group.suggestedToDelete)
            }
            if !toDelete.isEmpty {
                do {
                    try await photoService.deleteAssets(toDelete)
                    totalItems += toDelete.count
                    totalBytes += similarPhotosBytes
                } catch {
                    // Continue
                }
            }
        }

        // Delete screenshots
        if categories.contains(.screenshots) && !screenshotAssets.isEmpty {
            do {
                try await photoService.deleteAssets(screenshotAssets)
                totalItems += screenshotAssets.count
                totalBytes += screenshotsBytes
            } catch {
                // Continue
            }
        }

        // Delete large videos
        if categories.contains(.largeVideos) && !largeVideoAssets.isEmpty {
            do {
                try await photoService.deleteAssets(largeVideoAssets)
                totalItems += largeVideoAssets.count
                totalBytes += largeVideosBytes
            } catch {
                // Continue
            }
        }

        // Delete duplicate contacts (keep first, delete rest per group)
        if categories.contains(.duplicateContacts) && !duplicateContactGroups.isEmpty {
            for group in duplicateContactGroups {
                let toDelete = Array(group.contacts.dropFirst())
                if !toDelete.isEmpty {
                    do {
                        try await contactService.deleteContacts(toDelete)
                        totalItems += toDelete.count
                    } catch {
                        // Continue
                    }
                }
            }
        }

        // Rescan to update counts
        await scanAll()

        return CleanupResult(
            itemsRemoved: totalItems,
            bytesFreed: totalBytes
        )
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

struct CleanupResult {
    let itemsRemoved: Int
    let bytesFreed: Int64

    var bytesFreedFormatted: String {
        ByteCountFormatter.string(fromByteCount: bytesFreed, countStyle: .file)
    }
}

enum CleanupCategory: Hashable {
    case duplicatePhotos
    case similarPhotos
    case screenshots
    case largeVideos
    case duplicateContacts
}

struct CleanupItem: Identifiable {
    let id = UUID()
    let type: CleanupType
    let count: Int
    let savings: Int64
    var isSelected: Bool = true

    enum CleanupType {
        case duplicatePhotos
        case similarPhotos
        case screenshots
        case largeVideos
        case duplicateContacts

        var title: String {
            switch self {
            case .duplicatePhotos: return "Duplicate Photos"
            case .similarPhotos: return "Similar Photos"
            case .screenshots: return "Screenshots"
            case .largeVideos: return "Large Videos"
            case .duplicateContacts: return "Duplicate Contacts"
            }
        }

        var icon: String {
            switch self {
            case .duplicatePhotos: return "photo.on.rectangle"
            case .similarPhotos: return "square.stack.3d.up"
            case .screenshots: return "camera.viewfinder"
            case .largeVideos: return "video.fill"
            case .duplicateContacts: return "person.2.fill"
            }
        }
    }
}
