import Foundation
import Photos
import UIKit
import Vision
import CryptoKit

actor PhotoAnalysisService {
    static let shared = PhotoAnalysisService()

    /// Cache of Vision feature prints to avoid recomputation
    private var featurePrintCache: [String: VNFeaturePrintObservation] = [:]

    /// Cached scan results to avoid rescanning when navigating between tabs
    private var cachedDuplicates: DuplicateScanResult?
    private var cachedSimilarPhotos: SimilarScanResult?
    private var cachedScreenshots: ScanResult?
    private var cachedLargeVideos: ScanResult?

    private init() {}

    func invalidateCache() {
        cachedDuplicates = nil
        cachedSimilarPhotos = nil
        cachedScreenshots = nil
        cachedLargeVideos = nil
    }

    // MARK: - Find Duplicates
    func findDuplicates() async -> DuplicateScanResult {
        if let cached = cachedDuplicates { return cached }

        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)

        let allPhotos = PHAsset.fetchAssets(with: fetchOptions)

        // Step 1: Group by file size first (fast pre-filter - true duplicates have identical sizes)
        var sizeMap: [Int64: [PHAsset]] = [:]

        for i in 0..<allPhotos.count {
            let asset = allPhotos.object(at: i)
            let fileSize = await getAssetFileSize(asset) ?? 0

            // Skip very small files (likely thumbnails or corrupt)
            guard fileSize > 1000 else { continue }

            if var existing = sizeMap[fileSize] {
                existing.append(asset)
                sizeMap[fileSize] = existing
            } else {
                sizeMap[fileSize] = [asset]
            }
        }

        // Step 2: For assets with same size, compare actual content hash
        var groups: [DuplicateGroup] = []
        var totalDuplicateBytes: Int64 = 0
        var totalDuplicateCount = 0

        for (fileSize, assets) in sizeMap where assets.count > 1 {
            var hashMap: [String: [PHAsset]] = [:]

            for asset in assets {
                if let contentHash = await generateContentHash(for: asset) {
                    if var existing = hashMap[contentHash] {
                        existing.append(asset)
                        hashMap[contentHash] = existing
                    } else {
                        hashMap[contentHash] = [asset]
                    }
                }
            }

            // Build groups with ALL matching assets (original + duplicates)
            for (_, matchingAssets) in hashMap where matchingAssets.count > 1 {
                let sortedAssets = matchingAssets.sorted {
                    ($0.creationDate ?? Date.distantPast) < ($1.creationDate ?? Date.distantPast)
                }

                let photoAssets = sortedAssets.map { asset in
                    PhotoAsset(
                        id: asset.localIdentifier,
                        asset: asset,
                        fileSize: fileSize,
                        creationDate: asset.creationDate,
                        mediaType: asset.mediaType
                    )
                }

                // Best = oldest (original)
                let bestId = photoAssets.first?.id
                let group = DuplicateGroup(assets: photoAssets, bestAssetId: bestId)
                groups.append(group)

                // Count duplicates (all except the original)
                let duplicateCount = matchingAssets.count - 1
                totalDuplicateCount += duplicateCount
                totalDuplicateBytes += fileSize * Int64(duplicateCount)
            }
        }

        let result = DuplicateScanResult(duplicateCount: totalDuplicateCount, totalBytes: totalDuplicateBytes, groups: groups)
        cachedDuplicates = result
        return result
    }

    // MARK: - Find Similar Photos (using Apple Vision Framework)
    func findSimilarPhotos() async -> SimilarScanResult {
        if let cached = cachedSimilarPhotos { return cached }

        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)

        let allPhotos = PHAsset.fetchAssets(with: fetchOptions)

        var groups: [SimilarPhotoGroup] = []
        var processedIds: Set<String> = []
        var potentialSavings: Int64 = 0

        // Similarity threshold: lower distance = more similar
        // VNFeaturePrintObservation distances: <1.0 nearly identical, 1-3 very similar, >5 different
        let similarityThreshold: Float = 3.0
        let timeWindowSeconds: TimeInterval = 60

        for i in 0..<allPhotos.count {
            let asset = allPhotos.object(at: i)

            guard !processedIds.contains(asset.localIdentifier),
                  let creationDate = asset.creationDate else { continue }

            guard let featurePrint = await computeFeaturePrint(for: asset) else {
                processedIds.insert(asset.localIdentifier)
                continue
            }

            let fileSize = await getAssetFileSize(asset) ?? 0
            var groupAssets: [PhotoAsset] = []

            let photoAsset = PhotoAsset(
                id: asset.localIdentifier,
                asset: asset,
                fileSize: fileSize,
                creationDate: asset.creationDate,
                mediaType: asset.mediaType
            )
            groupAssets.append(photoAsset)
            processedIds.insert(asset.localIdentifier)

            // Compare with nearby photos within time window
            for j in (i + 1)..<allPhotos.count {
                let otherAsset = allPhotos.object(at: j)

                guard let otherDate = otherAsset.creationDate else { continue }

                let timeDiff = otherDate.timeIntervalSince(creationDate)

                // Early break - photos are sorted by date, so if we exceed time window, stop
                if timeDiff > timeWindowSeconds {
                    break
                }

                guard !processedIds.contains(otherAsset.localIdentifier) else { continue }

                guard let otherFeaturePrint = await computeFeaturePrint(for: otherAsset) else { continue }

                // Compare using Vision framework distance
                var distance: Float = 0
                do {
                    try featurePrint.computeDistance(&distance, to: otherFeaturePrint)
                } catch {
                    continue
                }

                if distance < similarityThreshold {
                    let otherFileSize = await getAssetFileSize(otherAsset) ?? 0
                    let otherPhotoAsset = PhotoAsset(
                        id: otherAsset.localIdentifier,
                        asset: otherAsset,
                        fileSize: otherFileSize,
                        creationDate: otherAsset.creationDate,
                        mediaType: otherAsset.mediaType
                    )
                    groupAssets.append(otherPhotoAsset)
                    processedIds.insert(otherAsset.localIdentifier)
                }
            }

            if groupAssets.count > 1 {
                let sortedAssets = groupAssets.sorted { $0.fileSize > $1.fileSize }
                let bestId = sortedAssets.first?.id

                // Calculate average similarity score for the group
                let similarityScore = await calculateGroupSimilarity(groupAssets.map { $0.asset })

                let group = SimilarPhotoGroup(
                    assets: groupAssets,
                    bestAssetId: bestId,
                    similarityScore: similarityScore
                )
                groups.append(group)

                potentialSavings += sortedAssets.dropFirst().reduce(0) { $0 + $1.fileSize }
            }
        }

        let result = SimilarScanResult(
            groupCount: groups.count,
            potentialSavings: potentialSavings,
            groups: groups
        )
        cachedSimilarPhotos = result
        return result
    }

    // MARK: - Vision Feature Print Similarity

    /// Compute a feature print for a photo using Apple's Vision framework
    private func computeFeaturePrint(for asset: PHAsset) async -> VNFeaturePrintObservation? {
        if let cached = featurePrintCache[asset.localIdentifier] {
            return cached
        }

        let cgImage: CGImage? = await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .fastFormat
            options.resizeMode = .fast
            options.isSynchronous = false
            options.isNetworkAccessAllowed = false

            PHImageManager.default().requestImage(
                for: asset,
                targetSize: CGSize(width: 300, height: 300),
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                continuation.resume(returning: image?.cgImage)
            }
        }

        guard let cgImage = cgImage else { return nil }

        let request = VNGenerateImageFeaturePrintRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])

        do {
            try handler.perform([request])
            guard let result = request.results?.first as? VNFeaturePrintObservation else { return nil }
            featurePrintCache[asset.localIdentifier] = result
            return result
        } catch {
            return nil
        }
    }

    /// Calculate average similarity score for a group using Vision feature prints
    private func calculateGroupSimilarity(_ assets: [PHAsset]) async -> Double {
        guard assets.count >= 2 else { return 1.0 }

        guard let firstPrint = await computeFeaturePrint(for: assets[0]) else { return 0.5 }

        var totalScore = 0.0
        var count = 0
        for asset in assets.dropFirst() {
            if let otherPrint = await computeFeaturePrint(for: asset) {
                var distance: Float = 0
                do {
                    try firstPrint.computeDistance(&distance, to: otherPrint)
                    // Convert distance to 0-1 similarity score (lower distance = higher similarity)
                    totalScore += max(0, 1.0 - Double(distance) / 10.0)
                    count += 1
                } catch {
                    continue
                }
            }
        }

        return count > 0 ? totalScore / Double(count) : 0.5
    }

    // MARK: - Find Screenshots
    func findScreenshots() async -> ScanResult {
        if let cached = cachedScreenshots { return cached }

        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]

        // Screenshots have specific subtypes
        fetchOptions.predicate = NSPredicate(
            format: "mediaType == %d AND (mediaSubtypes & %d) != 0",
            PHAssetMediaType.image.rawValue,
            PHAssetMediaSubtype.photoScreenshot.rawValue
        )

        let screenshots = PHAsset.fetchAssets(with: fetchOptions)

        var items: [PhotoAsset] = []
        var totalBytes: Int64 = 0

        for i in 0..<screenshots.count {
            let asset = screenshots.object(at: i)
            let fileSize = await getAssetFileSize(asset) ?? 0

            let photoAsset = PhotoAsset(
                id: asset.localIdentifier,
                asset: asset,
                fileSize: fileSize,
                creationDate: asset.creationDate,
                mediaType: asset.mediaType
            )
            items.append(photoAsset)
            totalBytes += fileSize
        }

        let result = ScanResult(count: items.count, totalBytes: totalBytes, items: items)
        cachedScreenshots = result
        return result
    }

    // MARK: - Find Large Videos
    func findLargeVideos(minimumSize: Int64 = 50_000_000) async -> ScanResult {
        if let cached = cachedLargeVideos { return cached }

        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)

        let allVideos = PHAsset.fetchAssets(with: fetchOptions)

        var largeVideos: [PhotoAsset] = []
        var totalBytes: Int64 = 0

        for i in 0..<allVideos.count {
            let asset = allVideos.object(at: i)
            let fileSize = await getAssetFileSize(asset) ?? 0

            if fileSize >= minimumSize {
                let photoAsset = PhotoAsset(
                    id: asset.localIdentifier,
                    asset: asset,
                    fileSize: fileSize,
                    creationDate: asset.creationDate,
                    mediaType: asset.mediaType
                )
                largeVideos.append(photoAsset)
                totalBytes += fileSize
            }
        }

        let result = ScanResult(count: largeVideos.count, totalBytes: totalBytes, items: largeVideos)
        cachedLargeVideos = result
        return result
    }

    // MARK: - Delete Assets
    func deleteAssets(_ assets: [PhotoAsset]) async throws {
        let phAssets = assets.map { $0.asset }

        try await PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.deleteAssets(phAssets as NSFastEnumeration)
        }
    }

    // MARK: - Helpers
    private func getAssetFileSize(_ asset: PHAsset) async -> Int64? {
        let resources = PHAssetResource.assetResources(for: asset)
        if let resource = resources.first,
           let fileSize = resource.value(forKey: "fileSize") as? Int64 {
            return fileSize
        }
        return nil
    }

    /// Generate a content-based hash using actual image data
    private func generateContentHash(for asset: PHAsset) async -> String? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.version = .current
            options.deliveryMode = .highQualityFormat
            options.isNetworkAccessAllowed = true
            options.isSynchronous = false

            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, _ in
                guard let imageData = data else {
                    continuation.resume(returning: nil)
                    return
                }

                // Use SHA256 hash of the actual image data
                let hash = SHA256.hash(data: imageData)
                let hashString = hash.compactMap { String(format: "%02x", $0) }.joined()
                continuation.resume(returning: hashString)
            }
        }
    }
}
