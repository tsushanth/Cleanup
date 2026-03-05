import Foundation
import AVFoundation
import Photos

actor VideoCompressionService {
    static let shared = VideoCompressionService()

    private init() {}

    enum CompressionQuality: String, CaseIterable {
        case high = "High"
        case medium = "Medium"
        case low = "Low"

        var preset: String {
            switch self {
            case .high: return AVAssetExportPreset1920x1080
            case .medium: return AVAssetExportPreset1280x720
            case .low: return AVAssetExportPreset960x540
            }
        }

        var estimatedReduction: Double {
            switch self {
            case .high: return 0.7
            case .medium: return 0.5
            case .low: return 0.3
            }
        }

        var description: String {
            switch self {
            case .high: return "1080p - Best quality, ~30% smaller"
            case .medium: return "720p - Good quality, ~50% smaller"
            case .low: return "540p - Smaller file, ~70% smaller"
            }
        }
    }

    struct CompressionResult {
        let success: Bool
        let originalSize: Int64
        let compressedSize: Int64
        let savedBytes: Int64
        let outputURL: URL?
        let error: Error?

        var savingsPercentage: Double {
            guard originalSize > 0 else { return 0 }
            return Double(savedBytes) / Double(originalSize) * 100
        }
    }

    // MARK: - Estimate Compression
    func estimateCompression(for asset: PHAsset, quality: CompressionQuality) async -> Int64 {
        guard let fileSize = await getAssetFileSize(asset) else { return 0 }
        return Int64(Double(fileSize) * (1 - quality.estimatedReduction))
    }

    // MARK: - Compress Video
    func compressVideo(asset: PHAsset, quality: CompressionQuality, progress: @escaping @Sendable (Double) -> Void) async -> CompressionResult {
        let originalSize = await getAssetFileSize(asset) ?? 0

        // Get video URL
        guard let videoURL = await getVideoURL(for: asset) else {
            return CompressionResult(
                success: false,
                originalSize: originalSize,
                compressedSize: 0,
                savedBytes: 0,
                outputURL: nil,
                error: NSError(domain: "VideoCompression", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not load video"])
            )
        }

        let avAsset = AVURLAsset(url: videoURL)

        guard let exportSession = AVAssetExportSession(asset: avAsset, presetName: quality.preset) else {
            return CompressionResult(
                success: false,
                originalSize: originalSize,
                compressedSize: 0,
                savedBytes: 0,
                outputURL: nil,
                error: NSError(domain: "VideoCompression", code: 2, userInfo: [NSLocalizedDescriptionKey: "Could not create export session"])
            )
        }

        let outputURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("mp4")

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        exportSession.shouldOptimizeForNetworkUse = true

        // Export with progress monitoring
        await withTaskGroup(of: Void.self) { group in
            // Progress monitoring task
            group.addTask { @Sendable in
                while !Task.isCancelled {
                    await MainActor.run {
                        progress(Double(exportSession.progress))
                    }
                    try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
                    if exportSession.status != .waiting && exportSession.status != .exporting {
                        break
                    }
                }
            }

            // Export task
            group.addTask {
                await exportSession.export()
            }

            // Wait for export to complete, then cancel progress monitoring
            await group.next()
            group.cancelAll()
        }

        switch exportSession.status {
        case .completed:
            let compressedSize = (try? FileManager.default.attributesOfItem(atPath: outputURL.path)[.size] as? Int64) ?? 0
            let savedBytes = originalSize - compressedSize

            return CompressionResult(
                success: true,
                originalSize: originalSize,
                compressedSize: compressedSize,
                savedBytes: savedBytes,
                outputURL: outputURL,
                error: nil
            )

        case .failed, .cancelled:
            return CompressionResult(
                success: false,
                originalSize: originalSize,
                compressedSize: 0,
                savedBytes: 0,
                outputURL: nil,
                error: exportSession.error
            )

        default:
            return CompressionResult(
                success: false,
                originalSize: originalSize,
                compressedSize: 0,
                savedBytes: 0,
                outputURL: nil,
                error: NSError(domain: "VideoCompression", code: 3, userInfo: [NSLocalizedDescriptionKey: "Unknown export status"])
            )
        }
    }

    // MARK: - Replace Original with Compressed
    func replaceWithCompressed(originalAsset: PHAsset, compressedURL: URL) async throws {
        try await PHPhotoLibrary.shared().performChanges {
            // Create new asset from compressed video
            let creationRequest = PHAssetCreationRequest.forAsset()
            creationRequest.addResource(with: .video, fileURL: compressedURL, options: nil)

            // Copy metadata
            if let creationDate = originalAsset.creationDate {
                creationRequest.creationDate = creationDate
            }
            if let location = originalAsset.location {
                creationRequest.location = location
            }

            // Delete original
            PHAssetChangeRequest.deleteAssets([originalAsset] as NSFastEnumeration)
        }

        // Clean up temp file
        try? FileManager.default.removeItem(at: compressedURL)
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

    private func getVideoURL(for asset: PHAsset) async -> URL? {
        await withCheckedContinuation { continuation in
            let options = PHVideoRequestOptions()
            options.version = .original
            options.isNetworkAccessAllowed = true

            PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
                if let urlAsset = avAsset as? AVURLAsset {
                    continuation.resume(returning: urlAsset.url)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}
