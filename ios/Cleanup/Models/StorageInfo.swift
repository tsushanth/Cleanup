import Foundation

struct StorageInfo {
    let totalSpace: Int64
    let usedSpace: Int64
    let freeSpace: Int64
    let photosSize: String
    let videosSize: String
    let otherSize: String

    var usedPercentage: Double {
        guard totalSpace > 0 else { return 0 }
        return Double(usedSpace) / Double(totalSpace)
    }

    var totalFormatted: String {
        ByteCountFormatter.string(fromByteCount: totalSpace, countStyle: .file)
    }

    var usedFormatted: String {
        ByteCountFormatter.string(fromByteCount: usedSpace, countStyle: .file)
    }

    var freeFormatted: String {
        ByteCountFormatter.string(fromByteCount: freeSpace, countStyle: .file)
    }
}
