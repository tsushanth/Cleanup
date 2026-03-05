import Foundation

// MARK: - Archive Tier (Glacier storage class)

enum ArchiveTier: String, Codable, CaseIterable, Identifiable {
    case instant = "instant"
    case flexible = "flexible"
    case deep = "deep"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .instant: return "Instant Access"
        case .flexible: return "Flexible Retrieval"
        case .deep: return "Deep Archive"
        }
    }

    var description: String {
        switch self {
        case .instant: return "Access files in milliseconds"
        case .flexible: return "Files available in 3-5 hours"
        case .deep: return "Files available in 12 hours, cheapest storage"
        }
    }

    var icon: String {
        switch self {
        case .instant: return "bolt.fill"
        case .flexible: return "clock.fill"
        case .deep: return "archivebox.fill"
        }
    }

    var retrievalTimeText: String {
        switch self {
        case .instant: return "Instant"
        case .flexible: return "3-5 hours"
        case .deep: return "Up to 12 hours"
        }
    }

    var costIndicator: String {
        switch self {
        case .instant: return "$$$"
        case .flexible: return "$$"
        case .deep: return "$"
        }
    }

    var minimumStorageDays: Int {
        switch self {
        case .instant: return 90
        case .flexible: return 90
        case .deep: return 180
        }
    }
}

// MARK: - Transfer Status

enum ArchiveTransferStatus: Codable, Equatable {
    case pending
    case uploading(progress: Double)
    case uploaded
    case archived
    case retrieving
    case available(downloadURL: String, expiresAt: Date)
    case failed(String)

    var isInProgress: Bool {
        switch self {
        case .uploading, .retrieving: return true
        default: return false
        }
    }

    var displayText: String {
        switch self {
        case .pending: return "Pending"
        case .uploading(let progress): return "Uploading \(Int(progress * 100))%"
        case .uploaded: return "Uploaded"
        case .archived: return "Archived"
        case .retrieving: return "Retrieving..."
        case .available: return "Ready to Download"
        case .failed(let msg): return "Failed: \(msg)"
        }
    }

    var statusIcon: String {
        switch self {
        case .pending: return "clock"
        case .uploading: return "arrow.up.circle"
        case .uploaded, .archived: return "checkmark.circle.fill"
        case .retrieving: return "arrow.down.circle"
        case .available: return "arrow.down.to.line.circle.fill"
        case .failed: return "exclamationmark.triangle.fill"
        }
    }

    var statusColor: String {
        switch self {
        case .pending: return "gray"
        case .uploading: return "blue"
        case .uploaded, .archived: return "green"
        case .retrieving: return "orange"
        case .available: return "green"
        case .failed: return "red"
        }
    }
}

// MARK: - Archived File Type

enum ArchivedFileType: String, Codable {
    case photo
    case video
    case contact
    case vault

    var icon: String {
        switch self {
        case .photo: return "photo.fill"
        case .video: return "video.fill"
        case .contact: return "person.crop.circle.fill"
        case .vault: return "lock.shield.fill"
        }
    }

    var displayName: String {
        switch self {
        case .photo: return "Photo"
        case .video: return "Video"
        case .contact: return "Contact"
        case .vault: return "Vault Item"
        }
    }
}

// MARK: - Archived Item

struct ArchivedItem: Identifiable, Codable {
    let id: String
    let originalAssetId: String
    let fileName: String
    let fileType: ArchivedFileType
    let fileSize: Int64
    let storageTier: ArchiveTier
    let archivedDate: Date
    var thumbnailData: Data?
    var transferStatus: ArchiveTransferStatus
    var metadata: ArchivedItemMetadata

    var fileSizeFormatted: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    var archivedDateFormatted: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: archivedDate)
    }
}

// MARK: - Archived Item Metadata

struct ArchivedItemMetadata: Codable {
    var creationDate: Date?
    var pixelWidth: Int?
    var pixelHeight: Int?
    var duration: TimeInterval?
    var contactNames: [String]?
    var contactCount: Int?

    init(
        creationDate: Date? = nil,
        pixelWidth: Int? = nil,
        pixelHeight: Int? = nil,
        duration: TimeInterval? = nil,
        contactNames: [String]? = nil,
        contactCount: Int? = nil
    ) {
        self.creationDate = creationDate
        self.pixelWidth = pixelWidth
        self.pixelHeight = pixelHeight
        self.duration = duration
        self.contactNames = contactNames
        self.contactCount = contactCount
    }
}

// MARK: - Archive Quota

struct ArchiveQuota: Codable {
    let totalBytes: Int64
    let usedBytes: Int64
    let itemCount: Int
    let subscriptionProductId: String?
    let tier: ArchiveSubscriptionTier?

    var remainingBytes: Int64 {
        max(0, totalBytes - usedBytes)
    }

    var usedPercentage: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(usedBytes) / Double(totalBytes)
    }

    var totalFormatted: String {
        ByteCountFormatter.string(fromByteCount: totalBytes, countStyle: .file)
    }

    var usedFormatted: String {
        ByteCountFormatter.string(fromByteCount: usedBytes, countStyle: .file)
    }

    var remainingFormatted: String {
        ByteCountFormatter.string(fromByteCount: remainingBytes, countStyle: .file)
    }
}

// MARK: - Archive Subscription Tier

enum ArchiveSubscriptionTier: String, Codable, CaseIterable, Identifiable {
    case tier5GB = "cleanup_archive_5gb__199"
    case tier25GB = "cleanup_archive_25gb_499"
    case tier100GB = "cleanup_archive_100gb_1499"

    var id: String { rawValue }

    var storageLimitBytes: Int64 {
        switch self {
        case .tier5GB: return 5_000_000_000
        case .tier25GB: return 25_000_000_000
        case .tier100GB: return 100_000_000_000
        }
    }

    var displayName: String {
        switch self {
        case .tier5GB: return "5 GB"
        case .tier25GB: return "25 GB"
        case .tier100GB: return "100 GB"
        }
    }

    var monthlyPrice: String {
        switch self {
        case .tier5GB: return "$1.99"
        case .tier25GB: return "$4.99"
        case .tier100GB: return "$14.99"
        }
    }

    var perGBPrice: String {
        switch self {
        case .tier5GB: return "$0.40/GB"
        case .tier25GB: return "$0.20/GB"
        case .tier100GB: return "$0.15/GB"
        }
    }

    var isBestValue: Bool {
        self == .tier100GB
    }
}

// MARK: - API Response Models

struct PresignedUploadResponse: Codable {
    let uploadURL: String
    let s3Key: String
    let expiresAt: Date
}

struct DownloadInitiationResponse: Codable {
    let status: String
    let downloadURL: String?
    let estimatedWaitMinutes: Int?
}

struct DownloadStatusResponse: Codable {
    let status: String
    let downloadURL: String?
    let expiresAt: Date?
}

struct QuotaValidationResponse: Codable {
    let valid: Bool
    let quota: ArchiveQuota
}

struct PaginatedResponse<T: Codable>: Codable {
    let items: [T]
    let nextCursor: String?
    let totalCount: Int
}

// MARK: - Errors

enum ArchiveError: Error, LocalizedError {
    case notAuthenticated
    case forbidden
    case quotaExceeded
    case rateLimited
    case resourceNotFound
    case invalidResponse
    case serverError(Int)
    case uploadFailed(String)
    case retrievalNotReady
    case minimumStoragePeriod(daysRemaining: Int)

    var errorDescription: String? {
        switch self {
        case .notAuthenticated: return "Please sign in to use cloud archival."
        case .forbidden: return "Access denied."
        case .quotaExceeded: return "Storage quota exceeded. Upgrade your plan for more space."
        case .rateLimited: return "Too many requests. Please try again later."
        case .resourceNotFound: return "Could not find the file resource."
        case .invalidResponse: return "Invalid server response."
        case .serverError(let code): return "Server error (code \(code))."
        case .uploadFailed(let msg): return "Upload failed: \(msg)"
        case .retrievalNotReady: return "File is still being retrieved from archive."
        case .minimumStoragePeriod(let days): return "This item must remain archived for \(days) more days to avoid early deletion charges."
        }
    }
}
