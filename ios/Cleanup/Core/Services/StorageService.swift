import Foundation
import Photos

actor StorageService {
    static let shared = StorageService()

    private init() {}

    func getStorageInfo() async -> StorageInfo? {
        guard let attributes = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()),
              let totalSpace = attributes[.systemSize] as? Int64,
              let freeSpace = attributes[.systemFreeSize] as? Int64 else {
            return nil
        }

        let usedSpace = totalSpace - freeSpace

        // Get photos and videos size
        let mediaSize = await getMediaLibrarySize()

        return StorageInfo(
            totalSpace: totalSpace,
            usedSpace: usedSpace,
            freeSpace: freeSpace,
            photosSize: formatBytes(mediaSize.photos),
            videosSize: formatBytes(mediaSize.videos),
            otherSize: formatBytes(usedSpace - mediaSize.photos - mediaSize.videos)
        )
    }

    private func getMediaLibrarySize() async -> (photos: Int64, videos: Int64) {
        var photosSize: Int64 = 0
        var videosSize: Int64 = 0

        let photoOptions = PHFetchOptions()
        photoOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)

        let videoOptions = PHFetchOptions()
        videoOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)

        let photos = PHAsset.fetchAssets(with: .image, options: photoOptions)
        let videos = PHAsset.fetchAssets(with: .video, options: videoOptions)

        // Sample size calculation (full calculation would be too slow)
        let photoSampleSize = min(100, photos.count)
        let videoSampleSize = min(50, videos.count)

        if photoSampleSize > 0 {
            var sampleTotal: Int64 = 0
            for i in 0..<photoSampleSize {
                let asset = photos.object(at: i)
                if let size = await getAssetFileSize(asset) {
                    sampleTotal += size
                }
            }
            let avgSize = sampleTotal / Int64(photoSampleSize)
            photosSize = avgSize * Int64(photos.count)
        }

        if videoSampleSize > 0 {
            var sampleTotal: Int64 = 0
            for i in 0..<videoSampleSize {
                let asset = videos.object(at: i)
                if let size = await getAssetFileSize(asset) {
                    sampleTotal += size
                }
            }
            let avgSize = sampleTotal / Int64(videoSampleSize)
            videosSize = avgSize * Int64(videos.count)
        }

        return (photosSize, videosSize)
    }

    private func getAssetFileSize(_ asset: PHAsset) async -> Int64? {
        return await withCheckedContinuation { continuation in
            let resources = PHAssetResource.assetResources(for: asset)
            if let resource = resources.first,
               let fileSize = resource.value(forKey: "fileSize") as? Int64 {
                continuation.resume(returning: fileSize)
            } else {
                continuation.resume(returning: nil)
            }
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}
