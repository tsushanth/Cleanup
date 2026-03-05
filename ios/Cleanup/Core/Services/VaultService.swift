import Foundation
import Photos
import LocalAuthentication
import Security

actor VaultService {
    static let shared = VaultService()

    private let fileManager = FileManager.default
    private var vaultDirectory: URL {
        let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return documentsPath.appendingPathComponent(".vault", isDirectory: true)
    }

    private let settingsKey = "vault_settings"
    private let itemsKey = "vault_items"

    private init() {
        setupVaultDirectory()
    }

    // MARK: - Setup
    private nonisolated func setupVaultDirectory() {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let vaultDir = documentsPath.appendingPathComponent(".vault", isDirectory: true)

        if !FileManager.default.fileExists(atPath: vaultDir.path) {
            try? FileManager.default.createDirectory(at: vaultDir, withIntermediateDirectories: true)
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            var url = vaultDir
            try? url.setResourceValues(resourceValues)
        }
    }

    private func createVaultDirectoryIfNeeded() {
        if !fileManager.fileExists(atPath: vaultDirectory.path) {
            try? fileManager.createDirectory(at: vaultDirectory, withIntermediateDirectories: true)
            // Exclude from backup
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            var url = vaultDirectory
            try? url.setResourceValues(resourceValues)
        }
    }

    // MARK: - Authentication
    func authenticateWithBiometrics() async -> Bool {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return false
        }

        do {
            return try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Access your private vault"
            )
        } catch {
            return false
        }
    }

    func verifyPin(_ pin: String) async -> Bool {
        let settings = await getSettings()
        return settings.pin == pin
    }

    func setPin(_ pin: String) async {
        var settings = await getSettings()
        settings.pin = pin
        settings.isPinEnabled = true
        await saveSettings(settings)
    }

    // MARK: - Settings
    func getSettings() async -> VaultSettings {
        guard let data = UserDefaults.standard.data(forKey: settingsKey),
              let settings = try? JSONDecoder().decode(VaultSettings.self, from: data) else {
            return VaultSettings()
        }
        return settings
    }

    func saveSettings(_ settings: VaultSettings) async {
        if let data = try? JSONEncoder().encode(settings) {
            UserDefaults.standard.set(data, forKey: settingsKey)
        }
    }

    // MARK: - Items
    func getItems() async -> [VaultItem] {
        guard let data = UserDefaults.standard.data(forKey: itemsKey),
              let items = try? JSONDecoder().decode([VaultItem].self, from: data) else {
            return []
        }
        return items
    }

    private func saveItems(_ items: [VaultItem]) async {
        if let data = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(data, forKey: itemsKey)
        }
    }

    // MARK: - Add to Vault
    func addToVault(asset: PHAsset, deleteOriginal: Bool = false) async throws -> VaultItem {
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first else {
            throw VaultError.resourceNotFound
        }

        let fileName = resource.originalFilename
        let fileType: VaultFileType = asset.mediaType == .video ? .video : .photo
        let fileSize = (resource.value(forKey: "fileSize") as? Int64) ?? 0

        let itemId = UUID()
        let destinationURL = vaultDirectory.appendingPathComponent(itemId.uuidString)

        // Copy file to vault
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true

            PHAssetResourceManager.default().writeData(for: resource, toFile: destinationURL, options: options) { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }

        // Generate thumbnail
        let thumbnailData = await generateThumbnail(for: asset)

        let item = VaultItem(
            id: itemId,
            originalAssetId: asset.localIdentifier,
            fileName: fileName,
            fileType: fileType,
            addedDate: Date(),
            fileSize: fileSize,
            thumbnailData: thumbnailData
        )

        // Save item metadata
        var items = await getItems()
        items.append(item)
        await saveItems(items)

        // Delete original if requested
        if deleteOriginal {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.deleteAssets([asset] as NSFastEnumeration)
            }
        }

        return item
    }

    // MARK: - Remove from Vault
    func removeFromVault(_ item: VaultItem, restoreToPhotos: Bool = false) async throws {
        let fileURL = vaultDirectory.appendingPathComponent(item.id.uuidString)

        if restoreToPhotos {
            // Restore to Photos library
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                if item.fileType == .video {
                    request.addResource(with: .video, fileURL: fileURL, options: nil)
                } else {
                    request.addResource(with: .photo, fileURL: fileURL, options: nil)
                }
            }
        }

        // Delete file from vault
        try fileManager.removeItem(at: fileURL)

        // Remove from items list
        var items = await getItems()
        items.removeAll { $0.id == item.id }
        await saveItems(items)
    }

    // MARK: - Get File URL
    func getFileURL(for item: VaultItem) -> URL {
        return vaultDirectory.appendingPathComponent(item.id.uuidString)
    }

    // MARK: - Helpers
    private func generateThumbnail(for asset: PHAsset) async -> Data? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .fastFormat
            options.resizeMode = .fast
            options.isSynchronous = false

            PHImageManager.default().requestImage(
                for: asset,
                targetSize: CGSize(width: 200, height: 200),
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                continuation.resume(returning: image?.jpegData(compressionQuality: 0.5))
            }
        }
    }

    enum VaultError: Error, LocalizedError {
        case resourceNotFound
        case authenticationFailed
        case fileOperationFailed

        var errorDescription: String? {
            switch self {
            case .resourceNotFound: return "Could not find the file resource"
            case .authenticationFailed: return "Authentication failed"
            case .fileOperationFailed: return "File operation failed"
            }
        }
    }
}
