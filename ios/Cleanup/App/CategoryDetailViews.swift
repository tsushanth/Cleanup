import SwiftUI
import Photos

// MARK: - Duplicate Photos Detail View
struct DuplicatePhotosDetailView: View {
    @StateObject private var viewModel = DuplicatePhotosViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var selectedItems: Set<String> = []
    @State private var showDeleteConfirmation = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Scanning for duplicates...")
            } else if viewModel.duplicates.isEmpty {
                EmptyStateView(
                    icon: "checkmark.circle.fill",
                    title: "No Duplicates Found",
                    subtitle: "Your photo library is clean!"
                )
            } else {
                List {
                    Section {
                        Text("Found \(viewModel.duplicates.count) duplicate photos")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    Section("Select items to delete") {
                        ForEach(viewModel.duplicates, id: \.id) { photo in
                            PhotoRow(photo: photo, isSelected: selectedItems.contains(photo.id)) {
                                toggleSelection(photo.id)
                            }
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(selectedItems.count == viewModel.duplicates.count ? "Deselect All" : "Select All") {
                            if selectedItems.count == viewModel.duplicates.count {
                                selectedItems.removeAll()
                            } else {
                                selectedItems = Set(viewModel.duplicates.map { $0.id })
                            }
                        }
                    }
                    ToolbarItem(placement: .bottomBar) {
                        Button(action: { attemptDelete() }) {
                            Label("Delete \(selectedItems.count) Items", systemImage: "trash")
                        }
                        .disabled(selectedItems.isEmpty)
                    }
                }
            }
        }
        .navigationTitle("Duplicate Photos")
        .task {
            await viewModel.loadDuplicates()
        }
        .alert("Delete Photos?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                Task {
                    await viewModel.deleteSelected(ids: selectedItems)
                    selectedItems.removeAll()
                }
            }
        } message: {
            Text("This will permanently delete \(selectedItems.count) photos.")
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            // Execute pending action after successful purchase
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .deleteDuplicates:
                    showDeleteConfirmation = true
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            showDeleteConfirmation = true
        } else {
            // Calculate saved space for context
            let selectedPhotos = viewModel.duplicates.filter { selectedItems.contains($0.id) }
            let totalBytes = selectedPhotos.reduce(0) { $0 + $1.fileSize }
            let savedGB = Double(totalBytes) / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptDeleteDuplicates(count: selectedItems.count, savedGB: savedGB),
                pendingAction: .deleteDuplicates(ids: selectedItems)
            )
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedItems.contains(id) {
            selectedItems.remove(id)
        } else {
            selectedItems.insert(id)
        }
    }
}

// MARK: - Similar Photos Detail View
struct SimilarPhotosDetailView: View {
    @StateObject private var viewModel = SimilarPhotosViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var selectedItems: Set<String> = []
    @State private var showDeleteConfirmation = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Finding similar photos...")
            } else if viewModel.groups.isEmpty {
                EmptyStateView(
                    icon: "photo.stack",
                    title: "No Similar Photos Found",
                    subtitle: "No groups of similar photos detected."
                )
            } else {
                List {
                    ForEach(viewModel.groups, id: \.id) { group in
                        Section("Group - \(group.assets.count) photos") {
                            ForEach(group.assets, id: \.id) { photo in
                                PhotoRow(
                                    photo: photo,
                                    isSelected: selectedItems.contains(photo.id),
                                    isBest: photo.id == group.bestAssetId
                                ) {
                                    toggleSelection(photo.id)
                                }
                            }
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .bottomBar) {
                        Button(action: { attemptDelete() }) {
                            Label("Delete \(selectedItems.count) Items", systemImage: "trash")
                        }
                        .disabled(selectedItems.isEmpty)
                    }
                }
            }
        }
        .navigationTitle("Similar Photos")
        .task {
            await viewModel.loadSimilarPhotos()
        }
        .alert("Delete Photos?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                Task {
                    await viewModel.deleteSelected(ids: selectedItems)
                    selectedItems.removeAll()
                }
            }
        } message: {
            Text("This will permanently delete \(selectedItems.count) photos.")
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .deleteSimilar:
                    showDeleteConfirmation = true
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            showDeleteConfirmation = true
        } else {
            // Calculate saved space
            var totalBytes: Int64 = 0
            for group in viewModel.groups {
                for asset in group.assets where selectedItems.contains(asset.id) {
                    totalBytes += asset.fileSize
                }
            }
            let savedGB = Double(totalBytes) / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptDeleteSimilar(count: selectedItems.count, savedGB: savedGB),
                pendingAction: .deleteSimilar(ids: selectedItems)
            )
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedItems.contains(id) {
            selectedItems.remove(id)
        } else {
            selectedItems.insert(id)
        }
    }
}

// MARK: - Screenshots Detail View
struct ScreenshotsDetailView: View {
    @StateObject private var viewModel = ScreenshotsViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var selectedItems: Set<String> = []
    @State private var showDeleteConfirmation = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Loading screenshots...")
            } else if viewModel.screenshots.isEmpty {
                EmptyStateView(
                    icon: "camera.viewfinder",
                    title: "No Screenshots",
                    subtitle: "You don't have any screenshots."
                )
            } else {
                List {
                    Section {
                        Text("\(viewModel.screenshots.count) screenshots using \(viewModel.totalSizeFormatted)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    Section("Select screenshots to delete") {
                        ForEach(viewModel.screenshots, id: \.id) { photo in
                            PhotoRow(photo: photo, isSelected: selectedItems.contains(photo.id)) {
                                toggleSelection(photo.id)
                            }
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(selectedItems.count == viewModel.screenshots.count ? "Deselect All" : "Select All") {
                            if selectedItems.count == viewModel.screenshots.count {
                                selectedItems.removeAll()
                            } else {
                                selectedItems = Set(viewModel.screenshots.map { $0.id })
                            }
                        }
                    }
                    ToolbarItem(placement: .bottomBar) {
                        Button(action: { attemptDelete() }) {
                            Label("Delete \(selectedItems.count) Items", systemImage: "trash")
                        }
                        .disabled(selectedItems.isEmpty)
                    }
                }
            }
        }
        .navigationTitle("Screenshots")
        .task {
            await viewModel.loadScreenshots()
        }
        .alert("Delete Screenshots?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                Task {
                    await viewModel.deleteSelected(ids: selectedItems)
                    selectedItems.removeAll()
                }
            }
        } message: {
            Text("This will permanently delete \(selectedItems.count) screenshots.")
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .deleteScreenshots:
                    showDeleteConfirmation = true
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            showDeleteConfirmation = true
        } else {
            let selectedScreenshots = viewModel.screenshots.filter { selectedItems.contains($0.id) }
            let totalBytes = selectedScreenshots.reduce(0) { $0 + $1.fileSize }
            let savedGB = Double(totalBytes) / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptDeleteScreenshots(count: selectedItems.count, savedGB: savedGB),
                pendingAction: .deleteScreenshots(ids: selectedItems)
            )
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedItems.contains(id) {
            selectedItems.remove(id)
        } else {
            selectedItems.insert(id)
        }
    }
}

// MARK: - Large Videos Detail View
struct LargeVideosDetailView: View {
    @StateObject private var viewModel = LargeVideosViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var selectedItems: Set<String> = []
    @State private var showDeleteConfirmation = false
    @State private var showCompressionSheet = false
    @State private var selectedVideoForCompression: PhotoAsset?

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Finding large videos...")
            } else if viewModel.videos.isEmpty {
                EmptyStateView(
                    icon: "video.fill",
                    title: "No Large Videos",
                    subtitle: "No videos larger than 50MB found."
                )
            } else {
                List {
                    Section {
                        Text("\(viewModel.videos.count) large videos using \(viewModel.totalSizeFormatted)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    Section {
                        ForEach(viewModel.videos, id: \.id) { video in
                            VideoRow(video: video, isSelected: selectedItems.contains(video.id)) {
                                toggleSelection(video.id)
                            } onCompress: {
                                attemptCompress(video)
                            }
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .bottomBar) {
                        Button(action: { attemptDelete() }) {
                            Label("Delete \(selectedItems.count) Items", systemImage: "trash")
                        }
                        .disabled(selectedItems.isEmpty)
                    }
                }
            }
        }
        .navigationTitle("Large Videos")
        .task {
            await viewModel.loadLargeVideos()
        }
        .alert("Delete Videos?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                Task {
                    await viewModel.deleteSelected(ids: selectedItems)
                    selectedItems.removeAll()
                }
            }
        } message: {
            Text("This will permanently delete \(selectedItems.count) videos.")
        }
        .sheet(isPresented: $showCompressionSheet) {
            if let video = selectedVideoForCompression {
                VideoCompressionSheet(video: video)
            }
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .deleteVideos:
                    showDeleteConfirmation = true
                    paywallCoordinator.clearPendingAction()
                case .compressVideo(let videoId):
                    if let video = viewModel.videos.first(where: { $0.id == videoId }) {
                        selectedVideoForCompression = video
                        showCompressionSheet = true
                    }
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            showDeleteConfirmation = true
        } else {
            let selectedVideos = viewModel.videos.filter { selectedItems.contains($0.id) }
            let totalBytes = selectedVideos.reduce(0) { $0 + $1.fileSize }
            let savedGB = Double(totalBytes) / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptDeleteVideos(count: selectedItems.count, savedGB: savedGB),
                pendingAction: .deleteVideos(ids: selectedItems)
            )
        }
    }

    private func attemptCompress(_ video: PhotoAsset) {
        if entitlementManager.isPro {
            selectedVideoForCompression = video
            showCompressionSheet = true
        } else {
            let sizeGB = Double(video.fileSize) / 1_000_000_000

            paywallCoordinator.showPaywall(
                context: .attemptCompressVideo(savedGB: sizeGB * 0.5), // Estimate ~50% savings
                pendingAction: .compressVideo(id: video.id)
            )
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedItems.contains(id) {
            selectedItems.remove(id)
        } else {
            selectedItems.insert(id)
        }
    }
}

// MARK: - Duplicate Contacts Detail View
struct DuplicateContactsDetailView: View {
    @StateObject private var viewModel = DuplicateContactsViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var showMergeConfirmation = false
    @State private var selectedGroup: DuplicateContactGroup?

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Finding duplicate contacts...")
            } else if viewModel.duplicateGroups.isEmpty {
                EmptyStateView(
                    icon: "person.2.fill",
                    title: "No Duplicate Contacts",
                    subtitle: "Your contacts are already organized!"
                )
            } else {
                List {
                    ForEach(viewModel.duplicateGroups) { group in
                        Section("\(group.matchReason.rawValue)") {
                            ForEach(group.contacts, id: \.id) { contact in
                                ContactRow(contact: contact)
                            }
                            Button("Merge These Contacts") {
                                attemptMerge(group)
                            }
                            .foregroundColor(.blue)
                        }
                    }
                }
            }
        }
        .navigationTitle("Duplicate Contacts")
        .task {
            await viewModel.loadDuplicates()
        }
        .alert("Merge Contacts?", isPresented: $showMergeConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Merge") {
                if let group = selectedGroup {
                    Task {
                        await viewModel.mergeContacts(group)
                    }
                }
            }
        } message: {
            Text("This will merge all contacts in this group into one.")
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .mergeContacts(let groupId):
                    if let group = viewModel.duplicateGroups.first(where: { $0.id == groupId }) {
                        selectedGroup = group
                        showMergeConfirmation = true
                    }
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptMerge(_ group: DuplicateContactGroup) {
        if entitlementManager.isPro {
            selectedGroup = group
            showMergeConfirmation = true
        } else {
            paywallCoordinator.showPaywall(
                context: .attemptMergeContacts(count: group.contacts.count),
                pendingAction: .mergeContacts(groupId: group.id)
            )
        }
    }
}

// MARK: - Supporting Views
struct PhotoRow: View {
    let photo: PhotoAsset
    let isSelected: Bool
    var isBest: Bool = false
    let onTap: () -> Void

    @State private var thumbnail: UIImage?

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if let thumbnail = thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 60, height: 60)
                        .cornerRadius(8)
                } else {
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 60, height: 60)
                        .cornerRadius(8)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(photo.creationDate?.formatted(date: .abbreviated, time: .shortened) ?? "Unknown date")
                            .font(.subheadline)
                        if isBest {
                            Text("BEST")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.green)
                                .cornerRadius(4)
                        }
                    }
                    Text(formatBytes(photo.fileSize))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? .blue : .gray)
                    .font(.title2)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .task {
            await loadThumbnail()
        }
    }

    private func loadThumbnail() async {
        let options = PHImageRequestOptions()
        options.deliveryMode = .fastFormat
        options.resizeMode = .fast

        thumbnail = await withCheckedContinuation { continuation in
            PHImageManager.default().requestImage(
                for: photo.asset,
                targetSize: CGSize(width: 120, height: 120),
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

struct VideoRow: View {
    let video: PhotoAsset
    let isSelected: Bool
    let onTap: () -> Void
    let onCompress: () -> Void

    @State private var thumbnail: UIImage?

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onTap) {
                HStack(spacing: 12) {
                    ZStack {
                        if let thumbnail = thumbnail {
                            Image(uiImage: thumbnail)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 60, height: 60)
                                .cornerRadius(8)
                        } else {
                            Rectangle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 60, height: 60)
                                .cornerRadius(8)
                        }
                        Image(systemName: "play.circle.fill")
                            .foregroundColor(.white)
                            .font(.title2)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(video.creationDate?.formatted(date: .abbreviated, time: .shortened) ?? "Unknown date")
                            .font(.subheadline)
                        Text(formatBytes(video.fileSize))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            Button(action: onCompress) {
                Image(systemName: "arrow.down.right.and.arrow.up.left")
                    .foregroundColor(.orange)
            }
            .buttonStyle(BorderlessButtonStyle())

            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundColor(isSelected ? .blue : .gray)
                .font(.title2)
                .onTapGesture { onTap() }
        }
        .task {
            await loadThumbnail()
        }
    }

    private func loadThumbnail() async {
        let options = PHImageRequestOptions()
        options.deliveryMode = .fastFormat
        options.resizeMode = .fast

        thumbnail = await withCheckedContinuation { continuation in
            PHImageManager.default().requestImage(
                for: video.asset,
                targetSize: CGSize(width: 120, height: 120),
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

struct ContactRow: View {
    let contact: ContactItem

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "person.circle.fill")
                .font(.largeTitle)
                .foregroundColor(.gray)

            VStack(alignment: .leading, spacing: 4) {
                Text(contact.fullName)
                    .font(.subheadline)
                    .fontWeight(.medium)
                if !contact.phoneNumbers.isEmpty {
                    Text(contact.phoneNumbers.first ?? "")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                if !contact.emailAddresses.isEmpty {
                    Text(contact.emailAddresses.first ?? "")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

struct VideoCompressionSheet: View {
    let video: PhotoAsset
    @Environment(\.dismiss) private var dismiss
    @State private var selectedQuality: VideoCompressionService.CompressionQuality = .medium
    @State private var isCompressing = false
    @State private var progress: Double = 0
    @State private var result: VideoCompressionService.CompressionResult?

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if isCompressing {
                    VStack(spacing: 16) {
                        ProgressView(value: progress)
                            .progressViewStyle(LinearProgressViewStyle())
                        Text("Compressing... \(Int(progress * 100))%")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                } else if let result = result {
                    VStack(spacing: 16) {
                        Image(systemName: result.success ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(result.success ? .green : .red)

                        if result.success {
                            Text("Saved \(formatBytes(result.savedBytes))")
                                .font(.headline)
                            Text("\(Int(result.savingsPercentage))% smaller")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        } else {
                            Text("Compression Failed")
                                .font(.headline)
                        }

                        Button("Done") {
                            dismiss()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding()
                } else {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Select Compression Quality")
                            .font(.headline)

                        ForEach(VideoCompressionService.CompressionQuality.allCases, id: \.self) { quality in
                            Button(action: { selectedQuality = quality }) {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(quality.rawValue)
                                            .font(.subheadline)
                                            .fontWeight(.medium)
                                        Text(quality.description)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    if selectedQuality == quality {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundColor(.blue)
                                    }
                                }
                                .padding()
                                .background(Color(.systemGray6))
                                .cornerRadius(12)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }

                        Spacer()

                        Button(action: startCompression) {
                            Text("Compress Video")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("Compress Video")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }

    private func startCompression() {
        isCompressing = true
        Task {
            let compressionResult = await VideoCompressionService.shared.compressVideo(
                asset: video.asset,
                quality: selectedQuality
            ) { prog in
                progress = prog
            }

            if compressionResult.success, let outputURL = compressionResult.outputURL {
                try? await VideoCompressionService.shared.replaceWithCompressed(
                    originalAsset: video.asset,
                    compressedURL: outputURL
                )
            }

            result = compressionResult
            isCompressing = false
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

// MARK: - View Models
@MainActor
class DuplicatePhotosViewModel: ObservableObject {
    @Published var duplicates: [PhotoAsset] = []
    @Published var isLoading = false

    func loadDuplicates() async {
        isLoading = true
        let result = await PhotoAnalysisService.shared.findDuplicates()
        duplicates = result.groups.flatMap { group in
            group.assets.filter { $0.id != group.bestAssetId }
        }
        isLoading = false
    }

    func deleteSelected(ids: Set<String>) async {
        let toDelete = duplicates.filter { ids.contains($0.id) }
        try? await PhotoAnalysisService.shared.deleteAssets(toDelete)
        duplicates.removeAll { ids.contains($0.id) }
    }
}

@MainActor
class SimilarPhotosViewModel: ObservableObject {
    @Published var groups: [SimilarPhotoGroup] = []
    @Published var isLoading = false

    func loadSimilarPhotos() async {
        isLoading = true
        let result = await PhotoAnalysisService.shared.findSimilarPhotos()
        groups = result.groups
        isLoading = false
    }

    func deleteSelected(ids: Set<String>) async {
        var toDelete: [PhotoAsset] = []
        for group in groups {
            toDelete.append(contentsOf: group.assets.filter { ids.contains($0.id) })
        }
        try? await PhotoAnalysisService.shared.deleteAssets(toDelete)

        // Update groups
        groups = groups.compactMap { group in
            let remaining = group.assets.filter { !ids.contains($0.id) }
            if remaining.count > 1 {
                return SimilarPhotoGroup(assets: remaining, bestAssetId: group.bestAssetId, similarityScore: group.similarityScore)
            }
            return nil
        }
    }
}

@MainActor
class ScreenshotsViewModel: ObservableObject {
    @Published var screenshots: [PhotoAsset] = []
    @Published var isLoading = false
    @Published var totalSizeFormatted = ""

    func loadScreenshots() async {
        isLoading = true
        let result = await PhotoAnalysisService.shared.findScreenshots()
        screenshots = result.items
        totalSizeFormatted = formatBytes(result.totalBytes)
        isLoading = false
    }

    func deleteSelected(ids: Set<String>) async {
        let toDelete = screenshots.filter { ids.contains($0.id) }
        try? await PhotoAnalysisService.shared.deleteAssets(toDelete)
        screenshots.removeAll { ids.contains($0.id) }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

@MainActor
class LargeVideosViewModel: ObservableObject {
    @Published var videos: [PhotoAsset] = []
    @Published var isLoading = false
    @Published var totalSizeFormatted = ""

    func loadLargeVideos() async {
        isLoading = true
        let result = await PhotoAnalysisService.shared.findLargeVideos()
        videos = result.items
        totalSizeFormatted = formatBytes(result.totalBytes)
        isLoading = false
    }

    func deleteSelected(ids: Set<String>) async {
        let toDelete = videos.filter { ids.contains($0.id) }
        try? await PhotoAnalysisService.shared.deleteAssets(toDelete)
        videos.removeAll { ids.contains($0.id) }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

@MainActor
class DuplicateContactsViewModel: ObservableObject {
    @Published var duplicateGroups: [DuplicateContactGroup] = []
    @Published var isLoading = false

    func loadDuplicates() async {
        isLoading = true
        let result = await ContactService.shared.findDuplicates()
        duplicateGroups = result.groups
        isLoading = false
    }

    func mergeContacts(_ group: DuplicateContactGroup) async {
        guard let primary = group.contacts.first else { return }
        try? await ContactService.shared.mergeContacts(group.contacts, into: primary)
        await loadDuplicates()
    }
}
