import Foundation
import Photos

struct PhotoAsset: Identifiable, Hashable {
    let id: String
    let asset: PHAsset
    let fileSize: Int64
    let creationDate: Date?
    let mediaType: PHAssetMediaType
    var isSelected: Bool = false

    var fileSizeFormatted: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: PhotoAsset, rhs: PhotoAsset) -> Bool {
        lhs.id == rhs.id
    }
}

struct DuplicateGroup: Identifiable {
    let id = UUID()
    var assets: [PhotoAsset]
    var bestAssetId: String?

    var totalSize: Int64 {
        assets.reduce(0) { $0 + $1.fileSize }
    }

    var potentialSavings: Int64 {
        guard assets.count > 1 else { return 0 }
        let sortedAssets = assets.sorted { $0.fileSize > $1.fileSize }
        return sortedAssets.dropFirst().reduce(0) { $0 + $1.fileSize }
    }
}

struct SimilarPhotoGroup: Identifiable {
    let id = UUID()
    var assets: [PhotoAsset]
    var bestAssetId: String?
    let similarityScore: Double

    var suggestedToDelete: [PhotoAsset] {
        guard let bestId = bestAssetId else {
            return Array(assets.dropFirst())
        }
        return assets.filter { $0.id != bestId }
    }
}

struct ScanResult {
    let count: Int
    let totalBytes: Int64
    let items: [PhotoAsset]
}

struct DuplicateScanResult {
    let duplicateCount: Int
    let totalBytes: Int64
    let groups: [DuplicateGroup]
}

struct SimilarScanResult {
    let groupCount: Int
    let potentialSavings: Int64
    let groups: [SimilarPhotoGroup]
}

struct VideoAsset: Identifiable, Hashable {
    let id: String
    let asset: PHAsset
    let fileSize: Int64
    let duration: TimeInterval
    let creationDate: Date?
    var isSelected: Bool = false
    var compressionSavings: Int64?

    var fileSizeFormatted: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    var durationFormatted: String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: VideoAsset, rhs: VideoAsset) -> Bool {
        lhs.id == rhs.id
    }
}
