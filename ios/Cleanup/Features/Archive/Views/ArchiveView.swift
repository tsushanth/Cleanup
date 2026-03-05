import SwiftUI

struct ArchiveView: View {
    @StateObject private var viewModel = ArchiveViewModel()
    @ObservedObject private var entitlementManager = EntitlementManager.shared

    @State private var selectedItem: ArchivedItem?
    @State private var showDeleteConfirmation = false
    @State private var itemToDelete: ArchivedItem?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let quota = viewModel.quota {
                    ArchiveQuotaBar(quota: quota)
                        .padding()
                }

                Picker("Filter", selection: $viewModel.selectedFilter) {
                    ForEach(ArchiveViewModel.ArchiveFilter.allCases, id: \.self) { filter in
                        Text(filter.rawValue).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.bottom, 8)

                if viewModel.isLoading && viewModel.archivedItems.isEmpty {
                    Spacer()
                    ProgressView("Loading archive...")
                    Spacer()
                } else if viewModel.filteredItems.isEmpty {
                    Spacer()
                    archiveEmptyState
                    Spacer()
                } else {
                    ScrollView {
                        LazyVGrid(
                            columns: [
                                GridItem(.flexible(), spacing: 4),
                                GridItem(.flexible(), spacing: 4),
                                GridItem(.flexible(), spacing: 4)
                            ],
                            spacing: 4
                        ) {
                            ForEach(viewModel.filteredItems) { item in
                                ArchiveItemThumbnail(item: item)
                                    .onTapGesture { selectedItem = item }
                            }
                        }
                        .padding(.horizontal, 4)
                    }
                }
            }
            .navigationTitle("Cloud Archive")
            .refreshable {
                viewModel.syncWithServer()
            }
            .onAppear {
                viewModel.loadArchivedItems()
            }
            .sheet(item: $selectedItem) { item in
                ArchiveItemDetailSheet(
                    item: item,
                    onRetrieve: {
                        viewModel.initiateRetrieval(for: item)
                        selectedItem = nil
                    },
                    onCheckStatus: {
                        viewModel.checkRetrievalStatus(for: item)
                        selectedItem = nil
                    },
                    onDelete: {
                        itemToDelete = item
                        showDeleteConfirmation = true
                        selectedItem = nil
                    }
                )
                .presentationDetents([.medium])
            }
            .alert("Delete Archived Item", isPresented: $showDeleteConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Delete Permanently", role: .destructive) {
                    if let item = itemToDelete {
                        viewModel.deleteArchivedItem(item)
                        selectedItem = nil
                    }
                }
            } message: {
                Text("This will permanently delete this item from cloud storage. This cannot be undone.")
            }
            .alert("Restored Successfully", isPresented: $viewModel.showRestoreSuccess) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("The item has been restored to your photo library and removed from the archive.")
            }
            .alert("Error", isPresented: .init(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
    }

    private var archiveEmptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "icloud.fill")
                .font(.system(size: 48))
                .foregroundColor(.cyan.opacity(0.5))

            Text("No Archived Items")
                .font(.title3)
                .fontWeight(.semibold)

            Text("When you archive files instead of deleting them, they'll appear here. You can retrieve them anytime.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }
}

// MARK: - Quota Bar

struct ArchiveQuotaBar: View {
    let quota: ArchiveQuota

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "icloud.fill")
                    .foregroundColor(.cyan)
                Text("Cloud Storage")
                    .font(.subheadline)
                    .fontWeight(.medium)
                Spacer()
                Text("\(quota.usedFormatted) / \(quota.totalFormatted)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            ProgressView(value: quota.usedPercentage)
                .tint(quota.usedPercentage > 0.9 ? .red : quota.usedPercentage > 0.75 ? .orange : .cyan)

            HStack {
                Text("\(quota.itemCount) items archived")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(quota.remainingFormatted) remaining")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

// MARK: - Item Thumbnail

struct ArchiveItemThumbnail: View {
    let item: ArchivedItem

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            if let thumbnailData = item.thumbnailData,
               let uiImage = UIImage(data: thumbnailData) {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(1, contentMode: .fill)
                    .clipped()
            } else {
                Rectangle()
                    .fill(Color(.tertiarySystemBackground))
                    .aspectRatio(1, contentMode: .fill)
                    .overlay {
                        Image(systemName: item.fileType.icon)
                            .font(.title2)
                            .foregroundColor(.secondary)
                    }
            }

            // Status indicator
            HStack(spacing: 4) {
                Image(systemName: item.transferStatus.statusIcon)
                    .font(.caption2)
                if item.transferStatus.isInProgress {
                    Text(item.transferStatus.displayText)
                        .font(.system(size: 9))
                }
            }
            .padding(4)
            .background(.ultraThinMaterial)
            .cornerRadius(4)
            .padding(4)

            // File type badge
            if item.fileType == .video {
                VStack {
                    HStack {
                        Spacer()
                        Image(systemName: "video.fill")
                            .font(.caption2)
                            .padding(4)
                            .background(.ultraThinMaterial)
                            .cornerRadius(4)
                            .padding(4)
                    }
                    Spacer()
                }
            }
        }
        .cornerRadius(4)
    }
}

// MARK: - Item Detail Sheet

struct ArchiveItemDetailSheet: View {
    let item: ArchivedItem
    let onRetrieve: () -> Void
    let onCheckStatus: () -> Void
    let onDelete: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                // Thumbnail
                if let thumbnailData = item.thumbnailData,
                   let uiImage = UIImage(data: thumbnailData) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(maxHeight: 200)
                        .cornerRadius(12)
                } else {
                    Image(systemName: item.fileType.icon)
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                        .frame(height: 100)
                }

                // Info
                VStack(spacing: 8) {
                    Text(item.fileName)
                        .font(.headline)
                        .lineLimit(1)

                    HStack(spacing: 16) {
                        Label(item.fileSizeFormatted, systemImage: "doc.fill")
                        Label(item.storageTier.displayName, systemImage: item.storageTier.icon)
                        Label(item.archivedDateFormatted, systemImage: "calendar")
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }

                // Status
                HStack {
                    Image(systemName: item.transferStatus.statusIcon)
                    Text(item.transferStatus.displayText)
                }
                .font(.subheadline)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(8)

                Spacer()

                // Actions
                VStack(spacing: 12) {
                    switch item.transferStatus {
                    case .failed:
                        EmptyView()
                    default:
                        Button(action: onRetrieve) {
                            Label("Retrieve & Restore", systemImage: "arrow.down.to.line")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.cyan)
                    }

                    Button(role: .destructive, action: onDelete) {
                        Label("Delete from Archive", systemImage: "trash")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding()
            .navigationTitle("Archived Item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
