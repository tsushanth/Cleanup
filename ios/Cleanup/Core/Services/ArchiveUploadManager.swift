import Foundation

final class ArchiveUploadManager: NSObject, @unchecked Sendable {
    static let shared = ArchiveUploadManager()

    private lazy var backgroundSession: URLSession = {
        let config = URLSessionConfiguration.background(
            withIdentifier: "com.kreativekoala.cleanup.archive.upload"
        )
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        config.allowsCellularAccess = true
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    private let queue = DispatchQueue(label: "com.kreativekoala.cleanup.archive.upload.queue")
    private var activeUploads: [String: UploadTask] = [:]

    struct UploadTask {
        let itemId: String
        let fileSize: Int64
        var progress: Double = 0
        var status: ArchiveTransferStatus = .pending
    }

    private override init() {
        super.init()
    }

    // MARK: - Upload

    func enqueueUpload(
        itemId: String,
        localFileURL: URL,
        presignedURL: URL,
        fileSize: Int64
    ) {
        queue.sync {
            activeUploads[itemId] = UploadTask(
                itemId: itemId,
                fileSize: fileSize,
                status: .uploading(progress: 0)
            )
        }

        var request = URLRequest(url: presignedURL)
        request.httpMethod = "PUT"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")

        let uploadTask = backgroundSession.uploadTask(with: request, fromFile: localFileURL)
        uploadTask.taskDescription = itemId
        uploadTask.resume()

        NotificationCenter.default.post(
            name: .archiveUploadStarted,
            object: nil,
            userInfo: ["itemId": itemId]
        )
    }

    func cancelUpload(itemId: String) {
        backgroundSession.getAllTasks { [weak self] tasks in
            tasks.first { $0.taskDescription == itemId }?.cancel()
            self?.queue.sync {
                self?.activeUploads.removeValue(forKey: itemId)
            }
        }
    }

    func uploadProgress(for itemId: String) -> Double {
        queue.sync { activeUploads[itemId]?.progress ?? 0 }
    }

    func uploadStatus(for itemId: String) -> ArchiveTransferStatus {
        queue.sync { activeUploads[itemId]?.status ?? .pending }
    }

    /// Call from AppDelegate handleEventsForBackgroundURLSession
    func handleBackgroundSessionCompletion(_ completionHandler: @escaping () -> Void) {
        // The background session will call delegate methods, then sessionDidFinishEvents
        // Store the completion handler to call after all events are delivered
        queue.sync {
            _backgroundCompletionHandler = completionHandler
        }
    }

    private var _backgroundCompletionHandler: (() -> Void)?
}

// MARK: - URLSession Delegates

extension ArchiveUploadManager: URLSessionTaskDelegate, URLSessionDataDelegate {

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didSendBodyData bytesSent: Int64,
        totalBytesSent: Int64,
        totalBytesExpectedToSend: Int64
    ) {
        guard let itemId = task.taskDescription else { return }
        let progress = Double(totalBytesSent) / Double(max(totalBytesExpectedToSend, 1))

        queue.sync {
            activeUploads[itemId]?.progress = progress
            activeUploads[itemId]?.status = .uploading(progress: progress)
        }

        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .archiveUploadProgressChanged,
                object: nil,
                userInfo: ["itemId": itemId, "progress": progress]
            )
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let itemId = task.taskDescription else { return }

        if let error = error {
            queue.sync {
                activeUploads[itemId]?.status = .failed(error.localizedDescription)
            }
            DispatchQueue.main.async {
                NotificationCenter.default.post(
                    name: .archiveUploadFailed,
                    object: nil,
                    userInfo: ["itemId": itemId, "error": error.localizedDescription]
                )
            }
        } else {
            queue.sync {
                activeUploads[itemId]?.status = .uploaded
            }
            // Confirm upload with backend
            Task {
                await confirmUploadWithBackend(itemId: itemId)
            }
            DispatchQueue.main.async {
                NotificationCenter.default.post(
                    name: .archiveUploadCompleted,
                    object: nil,
                    userInfo: ["itemId": itemId]
                )
            }
        }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        queue.sync {
            if let handler = _backgroundCompletionHandler {
                DispatchQueue.main.async {
                    handler()
                }
                _backgroundCompletionHandler = nil
            }
        }
    }

    private func confirmUploadWithBackend(itemId: String) async {
        // Retrieve stored item metadata and confirm with API
        let items = getLocalArchivedItems()
        guard let item = items.first(where: { $0.id == itemId }) else { return }

        do {
            try await ArchiveAPIClient.shared.confirmUpload(
                itemId: item.id,
                fileName: item.fileName,
                fileType: item.fileType,
                fileSize: item.fileSize,
                storageTier: item.storageTier,
                metadata: item.metadata,
                thumbnailData: item.thumbnailData
            )
            // Update local status to archived
            updateLocalItemStatus(itemId: itemId, status: .archived)
        } catch {
            updateLocalItemStatus(itemId: itemId, status: .failed(error.localizedDescription))
        }
    }

    private func getLocalArchivedItems() -> [ArchivedItem] {
        guard let data = UserDefaults.standard.data(forKey: "archive_local_items"),
              let items = try? JSONDecoder().decode([ArchivedItem].self, from: data) else {
            return []
        }
        return items
    }

    private func updateLocalItemStatus(itemId: String, status: ArchiveTransferStatus) {
        guard let data = UserDefaults.standard.data(forKey: "archive_local_items"),
              var items = try? JSONDecoder().decode([ArchivedItem].self, from: data),
              let index = items.firstIndex(where: { $0.id == itemId }) else {
            return
        }
        items[index].transferStatus = status
        if let encoded = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(encoded, forKey: "archive_local_items")
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let archiveUploadStarted = Notification.Name("archiveUploadStarted")
    static let archiveUploadProgressChanged = Notification.Name("archiveUploadProgressChanged")
    static let archiveUploadCompleted = Notification.Name("archiveUploadCompleted")
    static let archiveUploadFailed = Notification.Name("archiveUploadFailed")
}
