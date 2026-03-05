import Foundation
import Photos
import Contacts

actor ArchiveService {
    static let shared = ArchiveService()

    private let apiClient = ArchiveAPIClient.shared
    private let uploadManager = ArchiveUploadManager.shared
    private let authService = ArchiveAuthService.shared

    private let localItemsKey = "archive_local_items"
    private let localQuotaKey = "archive_local_quota"

    private init() {}

    // MARK: - Archive Photos/Videos

    func archiveAsset(
        _ asset: PHAsset,
        tier: ArchiveTier,
        deleteAfterArchive: Bool = true
    ) async throws -> ArchivedItem {
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first else {
            throw ArchiveError.resourceNotFound
        }

        let fileName = resource.originalFilename
        let fileSize = (resource.value(forKey: "fileSize") as? Int64) ?? 0
        let mimeType = resource.uniformTypeIdentifier
        let itemId = UUID().uuidString

        // Write asset to temporary file
        let fileExtension = URL(string: fileName)?.pathExtension ?? "dat"
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(itemId)
            .appendingPathExtension(fileExtension)

        try await writeAssetToFile(resource: resource, destination: tempURL)

        // Generate thumbnail
        let thumbnailData = await generateThumbnail(for: asset)

        // Request presigned upload URL
        let presignResponse = try await apiClient.requestUploadURL(
            itemId: itemId,
            filename: fileName,
            fileSize: fileSize,
            mimeType: mimeType,
            tier: tier
        )

        // Create local metadata record
        let archivedItem = ArchivedItem(
            id: itemId,
            originalAssetId: asset.localIdentifier,
            fileName: fileName,
            fileType: asset.mediaType == .video ? .video : .photo,
            fileSize: fileSize,
            storageTier: tier,
            archivedDate: Date(),
            thumbnailData: thumbnailData,
            transferStatus: .uploading(progress: 0),
            metadata: ArchivedItemMetadata(
                creationDate: asset.creationDate,
                pixelWidth: asset.pixelWidth,
                pixelHeight: asset.pixelHeight,
                duration: asset.mediaType == .video ? asset.duration : nil
            )
        )

        // Save to local cache
        var items = getLocalItems()
        items.append(archivedItem)
        saveLocalItems(items)

        // Enqueue background upload
        uploadManager.enqueueUpload(
            itemId: itemId,
            localFileURL: tempURL,
            presignedURL: URL(string: presignResponse.uploadURL)!,
            fileSize: fileSize
        )

        // Delete original after upload is queued
        if deleteAfterArchive {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.deleteAssets([asset] as NSFastEnumeration)
            }
        }

        return archivedItem
    }

    // MARK: - Archive Contacts

    func archiveContacts(
        _ contacts: [ContactItem],
        tier: ArchiveTier
    ) async throws -> ArchivedItem {
        let cnContacts = contacts.map { $0.contact }
        let contactsData = try CNContactVCardSerialization.data(with: cnContacts)

        let itemId = UUID().uuidString
        let fileName = "contacts_\(itemId).vcf"

        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName)
        try contactsData.write(to: tempURL)

        let presignResponse = try await apiClient.requestUploadURL(
            itemId: itemId,
            filename: fileName,
            fileSize: Int64(contactsData.count),
            mimeType: "text/vcard",
            tier: tier
        )

        let archivedItem = ArchivedItem(
            id: itemId,
            originalAssetId: contacts.first?.id ?? "",
            fileName: fileName,
            fileType: .contact,
            fileSize: Int64(contactsData.count),
            storageTier: tier,
            archivedDate: Date(),
            thumbnailData: nil,
            transferStatus: .uploading(progress: 0),
            metadata: ArchivedItemMetadata(
                contactNames: contacts.map { $0.fullName },
                contactCount: contacts.count
            )
        )

        var items = getLocalItems()
        items.append(archivedItem)
        saveLocalItems(items)

        uploadManager.enqueueUpload(
            itemId: itemId,
            localFileURL: tempURL,
            presignedURL: URL(string: presignResponse.uploadURL)!,
            fileSize: Int64(contactsData.count)
        )

        return archivedItem
    }

    // MARK: - Archive Vault Items

    func archiveVaultItem(
        _ item: VaultItem,
        tier: ArchiveTier
    ) async throws -> ArchivedItem {
        let fileURL = await VaultService.shared.getFileURL(for: item)
        let itemId = UUID().uuidString

        let mimeType = item.fileType == .video ? "video/mp4" : "image/jpeg"

        let presignResponse = try await apiClient.requestUploadURL(
            itemId: itemId,
            filename: item.fileName,
            fileSize: item.fileSize,
            mimeType: mimeType,
            tier: tier
        )

        let archivedItem = ArchivedItem(
            id: itemId,
            originalAssetId: item.originalAssetId,
            fileName: item.fileName,
            fileType: item.fileType == .video ? .video : .photo,
            fileSize: item.fileSize,
            storageTier: tier,
            archivedDate: Date(),
            thumbnailData: item.thumbnailData,
            transferStatus: .uploading(progress: 0),
            metadata: ArchivedItemMetadata()
        )

        var items = getLocalItems()
        items.append(archivedItem)
        saveLocalItems(items)

        uploadManager.enqueueUpload(
            itemId: itemId,
            localFileURL: fileURL,
            presignedURL: URL(string: presignResponse.uploadURL)!,
            fileSize: item.fileSize
        )

        return archivedItem
    }

    // MARK: - Retrieval

    func initiateRetrieval(itemId: String) async throws -> DownloadInitiationResponse {
        let response = try await apiClient.initiateDownload(itemId: itemId)

        var items = getLocalItems()
        if let index = items.firstIndex(where: { $0.id == itemId }) {
            if response.status == "available", let url = response.downloadURL {
                items[index].transferStatus = .available(
                    downloadURL: url,
                    expiresAt: Date().addingTimeInterval(86400)
                )
            } else {
                items[index].transferStatus = .retrieving
            }
            saveLocalItems(items)
        }

        return response
    }

    func checkRetrievalStatus(itemId: String) async throws -> DownloadStatusResponse {
        let response = try await apiClient.checkDownloadStatus(itemId: itemId)

        var items = getLocalItems()
        if let index = items.firstIndex(where: { $0.id == itemId }) {
            if response.status == "available", let url = response.downloadURL {
                items[index].transferStatus = .available(
                    downloadURL: url,
                    expiresAt: response.expiresAt ?? Date().addingTimeInterval(86400)
                )
            }
            saveLocalItems(items)
        }

        return response
    }

    func downloadAndRestore(itemId: String, downloadURL: URL) async throws {
        let (tempURL, _) = try await URLSession.shared.download(from: downloadURL)

        let items = getLocalItems()
        guard let item = items.first(where: { $0.id == itemId }) else {
            try? FileManager.default.removeItem(at: tempURL)
            throw ArchiveError.resourceNotFound
        }

        // Move to a stable path with the correct file extension so Photos can identify the type
        let stableURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(URL(string: item.fileName)?.pathExtension ?? "jpg")
        try FileManager.default.moveItem(at: tempURL, to: stableURL)

        defer { try? FileManager.default.removeItem(at: stableURL) }

        // Restore to Photos library
        if item.fileType == .photo || item.fileType == .video {
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                if item.fileType == .video {
                    request.addResource(with: .video, fileURL: stableURL, options: nil)
                } else {
                    request.addResource(with: .photo, fileURL: stableURL, options: nil)
                }
            }
        } else if item.fileType == .contact {
            let data = try Data(contentsOf: stableURL)
            let contacts = try CNContactVCardSerialization.contacts(with: data)
            let store = CNContactStore()
            let saveRequest = CNSaveRequest()
            for contact in contacts {
                saveRequest.add(contact.mutableCopy() as! CNMutableContact, toContainerWithIdentifier: nil)
            }
            try store.execute(saveRequest)
        }
    }

    // MARK: - Quota

    func getQuota() async throws -> ArchiveQuota {
        let quota = try await apiClient.getQuota()
        if let data = try? JSONEncoder().encode(quota) {
            UserDefaults.standard.set(data, forKey: localQuotaKey)
        }
        return quota
    }

    nonisolated func getCachedQuota() -> ArchiveQuota? {
        guard let data = UserDefaults.standard.data(forKey: localQuotaKey) else { return nil }
        return try? JSONDecoder().decode(ArchiveQuota.self, from: data)
    }

    // MARK: - List and Delete

    func getArchivedItems() -> [ArchivedItem] {
        return getLocalItems()
    }

    func syncItems() async throws {
        let response = try await apiClient.listItems()
        let remoteItems = response.items

        var localItems = getLocalItems()

        // Update local items with remote status
        for remoteItem in remoteItems {
            if let index = localItems.firstIndex(where: { $0.id == remoteItem.id }) {
                localItems[index].transferStatus = remoteItem.transferStatus
            } else {
                // Item exists on server but not locally (e.g. synced from another device)
                localItems.append(remoteItem)
            }
        }

        // Remove local items that were deleted on server
        let remoteIds = Set(remoteItems.map { $0.id })
        localItems.removeAll { item in
            !remoteIds.contains(item.id) && item.transferStatus != .uploading(progress: 0)
        }

        saveLocalItems(localItems)
    }

    func deleteArchivedItem(itemId: String) async throws {
        // Remove from local cache immediately for responsive UI
        var localItems = getLocalItems()
        localItems.removeAll { $0.id == itemId }
        saveLocalItems(localItems)

        // Delete from backend (fire and forget — early deletion charges are negligible)
        try await apiClient.deleteItem(itemId: itemId)
    }

    // MARK: - Private Helpers

    private func getLocalItems() -> [ArchivedItem] {
        guard let data = UserDefaults.standard.data(forKey: localItemsKey),
              let items = try? JSONDecoder().decode([ArchivedItem].self, from: data) else {
            return []
        }
        return items
    }

    private func saveLocalItems(_ items: [ArchivedItem]) {
        if let data = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(data, forKey: localItemsKey)
        }
    }

    private func writeAssetToFile(resource: PHAssetResource, destination: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true

            PHAssetResourceManager.default().writeData(
                for: resource, toFile: destination, options: options
            ) { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

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
}
