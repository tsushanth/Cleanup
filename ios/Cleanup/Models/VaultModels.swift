import Foundation
import Photos

struct VaultItem: Identifiable, Codable {
    let id: UUID
    let originalAssetId: String
    let fileName: String
    let fileType: VaultFileType
    let addedDate: Date
    let fileSize: Int64
    var thumbnailData: Data?

    var fileSizeFormatted: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    var addedDateFormatted: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: addedDate)
    }
}

enum VaultFileType: String, Codable {
    case photo
    case video
    case document

    var icon: String {
        switch self {
        case .photo: return "photo.fill"
        case .video: return "video.fill"
        case .document: return "doc.fill"
        }
    }
}

struct VaultSettings: Codable {
    var isPinEnabled: Bool = true
    var pin: String = ""
    var useBiometrics: Bool = true
    var autoLockInterval: TimeInterval = 60
    var fakePin: String? = nil
    var showFakeContent: Bool = false
}
