import SwiftUI

struct CleanupConfirmationView: View {
    @ObservedObject var viewModel: HomeViewModel
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @StateObject private var archiveViewModel = ArchiveViewModel()
    @Environment(\.dismiss) var dismiss
    @State private var isProcessing = false
    @State private var cleanupResult: CleanupResult?

    // Track selection state for each category
    @State private var duplicatePhotosSelected = true
    @State private var similarPhotosSelected = true
    @State private var screenshotsSelected = true
    @State private var largeVideosSelected = true
    @State private var duplicateContactsSelected = true

    private var selectedCategories: Set<CleanupCategory> {
        var categories = Set<CleanupCategory>()
        if duplicatePhotosSelected && viewModel.duplicatePhotosCount > 0 { categories.insert(.duplicatePhotos) }
        if similarPhotosSelected && viewModel.similarPhotosCount > 0 { categories.insert(.similarPhotos) }
        if screenshotsSelected && viewModel.screenshotsCount > 0 { categories.insert(.screenshots) }
        if largeVideosSelected && viewModel.largeVideosCount > 0 { categories.insert(.largeVideos) }
        if duplicateContactsSelected && viewModel.duplicateContactsCount > 0 { categories.insert(.duplicateContacts) }
        return categories
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let result = cleanupResult {
                    SuccessView(result: result, dismiss: dismiss)
                } else {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 50))
                            .foregroundColor(.blue)

                        Text("Review Before Cleanup")
                            .font(.title2)
                            .fontWeight(.bold)

                        Text("Select items you want to remove")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.top, 30)
                    .padding(.bottom, 20)

                    // Items List
                    ScrollView {
                        VStack(spacing: 12) {
                            if viewModel.duplicatePhotosCount > 0 {
                                CleanupItemRow(
                                    icon: "photo.on.rectangle",
                                    iconColor: .blue,
                                    title: "Duplicate Photos",
                                    count: viewModel.duplicatePhotosCount,
                                    savings: viewModel.duplicatePhotosSavings,
                                    isSelected: $duplicatePhotosSelected
                                )
                            }

                            if viewModel.similarPhotosCount > 0 {
                                CleanupItemRow(
                                    icon: "square.stack.3d.up",
                                    iconColor: .indigo,
                                    title: "Similar Photos",
                                    count: viewModel.similarPhotosCount,
                                    savings: viewModel.similarPhotosSavings,
                                    isSelected: $similarPhotosSelected
                                )
                            }

                            if viewModel.screenshotsCount > 0 {
                                CleanupItemRow(
                                    icon: "camera.viewfinder",
                                    iconColor: .green,
                                    title: "Screenshots",
                                    count: viewModel.screenshotsCount,
                                    savings: viewModel.screenshotsSavings,
                                    isSelected: $screenshotsSelected
                                )
                            }

                            if viewModel.largeVideosCount > 0 {
                                CleanupItemRow(
                                    icon: "video.fill",
                                    iconColor: .purple,
                                    title: "Large Videos",
                                    count: viewModel.largeVideosCount,
                                    savings: viewModel.largeVideosSavings,
                                    isSelected: $largeVideosSelected
                                )
                            }

                            if viewModel.duplicateContactsCount > 0 {
                                CleanupItemRow(
                                    icon: "person.2.fill",
                                    iconColor: .orange,
                                    title: "Duplicate Contacts",
                                    count: viewModel.duplicateContactsCount,
                                    savings: nil,
                                    isSelected: $duplicateContactsSelected
                                )
                            }
                        }
                        .padding()
                    }

                    // Footer
                    VStack(spacing: 16) {
                        // Warning
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.orange)
                            Text("Items will be moved to Recently Deleted")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        // Buttons
                        VStack(spacing: 10) {
                            // Archive button
                            Button(action: archiveSelected) {
                                HStack {
                                    Image(systemName: "icloud.and.arrow.up")
                                    Text("Archive to Cloud")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(
                                    selectedCategories.isEmpty
                                        ? AnyShapeStyle(Color.gray)
                                        : AnyShapeStyle(LinearGradient(colors: [.cyan, .blue], startPoint: .leading, endPoint: .trailing))
                                )
                                .foregroundColor(.white)
                                .cornerRadius(12)
                            }
                            .disabled(isProcessing || selectedCategories.isEmpty)

                            HStack(spacing: 12) {
                                Button(action: { dismiss() }) {
                                    Text("Cancel")
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color(.systemGray5))
                                        .foregroundColor(.primary)
                                        .cornerRadius(12)
                                }

                                Button(action: performCleanup) {
                                    HStack {
                                        if isProcessing {
                                            ProgressView()
                                                .tint(.white)
                                        } else {
                                            Image(systemName: "trash.fill")
                                        }
                                        Text(isProcessing ? "Cleaning..." : "Delete")
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(selectedCategories.isEmpty ? Color.gray : Color.red)
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                                }
                                .disabled(isProcessing || selectedCategories.isEmpty)
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("SmartSpace")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if cleanupResult == nil {
                        Button("Cancel") {
                            dismiss()
                        }
                    }
                }
            }
            .sheet(isPresented: $archiveViewModel.showSignIn) {
                ArchiveSignInView(onSignIn: { archiveViewModel.onSignInComplete() })
            }
            .sheet(isPresented: $paywallCoordinator.showArchivePaywall) {
                RemoteArchivePaywallView()
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $archiveViewModel.showArchiveUpgradePaywall) {
                RemoteArchivePaywallView()
                    .presentationDetents([.large])
            }
            .alert("Archive Error", isPresented: .init(
                get: { archiveViewModel.errorMessage != nil },
                set: { if !$0 { archiveViewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(archiveViewModel.errorMessage ?? "")
            }
            .alert("Archived Successfully", isPresented: $archiveViewModel.showArchiveSuccess) {
                Button("OK", role: .cancel) {
                    Task { await viewModel.scanAll() }
                }
            } message: {
                Text("\(archiveViewModel.archiveSuccessCount) items have been archived to the cloud. They are uploading in the background.")
            }
        }
    }

    private func performCleanup() {
        isProcessing = true

        Task {
            let result = await viewModel.performCleanup(categories: selectedCategories)
            isProcessing = false
            cleanupResult = result
            AppReviewManager.requestReviewIfNeeded()
        }
    }

    private func archiveSelected() {
        // Collect all photo/video assets from selected categories
        var photos: [PhotoAsset] = []
        var contacts: [ContactItem] = []

        if selectedCategories.contains(.duplicatePhotos) {
            photos.append(contentsOf: viewModel.duplicatePhotoAssets)
        }
        if selectedCategories.contains(.similarPhotos) {
            for group in viewModel.similarPhotoGroups {
                photos.append(contentsOf: group.suggestedToDelete)
            }
        }
        if selectedCategories.contains(.screenshots) {
            photos.append(contentsOf: viewModel.screenshotAssets)
        }
        if selectedCategories.contains(.largeVideos) {
            photos.append(contentsOf: viewModel.largeVideoAssets)
        }
        if selectedCategories.contains(.duplicateContacts) {
            for group in viewModel.duplicateContactGroups {
                contacts.append(contentsOf: Array(group.contacts.dropFirst()))
            }
        }

        let totalBytes = photos.reduce(Int64(0)) { $0 + $1.fileSize } + Int64(contacts.count * 1024)
        let totalCount = photos.count + contacts.count

        // Check archive access (Pro + subscription + quota)
        let result = paywallCoordinator.checkArchiveAccess(
            requiredBytes: totalBytes,
            context: .attemptArchive(count: totalCount, totalGB: Double(totalBytes) / 1_073_741_824)
        )

        guard result == .allowed else { return }

        // Pass all items to archive view model
        archiveViewModel.archiveSelectedPhotos(photos)
        if !contacts.isEmpty {
            // Append contacts to existing items
            let contactItems = contacts.map { ArchiveViewModel.ArchivableItem.contact($0) }
            archiveViewModel.itemsToArchive.append(contentsOf: contactItems)
        }
    }
}

struct CleanupItemRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let count: Int
    let savings: String?
    @Binding var isSelected: Bool

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.white)
                .frame(width: 44, height: 44)
                .background(iconColor)
                .cornerRadius(10)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)

                HStack(spacing: 8) {
                    Text("\(count) items")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    if let savings = savings {
                        Text("•")
                            .foregroundColor(.secondary)
                        Text(savings)
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }
            }

            Spacer()

            Button(action: { isSelected.toggle() }) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundColor(isSelected ? .blue : .gray)
            }
        }
        .padding()
        .background(isSelected ? Color(.systemBackground) : Color(.systemBackground).opacity(0.5))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.03), radius: 5, x: 0, y: 2)
        .opacity(isSelected ? 1.0 : 0.5)
    }
}

struct SuccessView: View {
    let result: CleanupResult
    let dismiss: DismissAction
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Success Animation
            ZStack {
                Circle()
                    .fill(Color.green.opacity(0.1))
                    .frame(width: 120, height: 120)

                Circle()
                    .fill(Color.green.opacity(0.2))
                    .frame(width: 90, height: 90)

                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.green)
            }

            VStack(spacing: 8) {
                Text("Cleanup Complete!")
                    .font(.title)
                    .fontWeight(.bold)

                Text("Your device is now cleaner and has more free space")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            // Stats
            HStack(spacing: 30) {
                StatItem(value: "\(result.itemsRemoved)", label: "Items Removed")
                if result.bytesFreed > 0 {
                    StatItem(value: result.bytesFreedFormatted, label: "Space Freed")
                }
            }

            // Post-delete archive suggestion
            if !entitlementManager.hasArchiveSubscription && result.itemsRemoved > 0 {
                Button {
                    paywallCoordinator.showArchivePaywall = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "shield.checkered")
                            .foregroundColor(.cyan)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Next time, archive first")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundColor(.primary)
                            Text("Keep a safety net before deleting. From $1.99/mo.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(12)
                    .background(Color.cyan.opacity(0.08))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.cyan.opacity(0.2), lineWidth: 1)
                    )
                }
                .buttonStyle(PlainButtonStyle())
            }

            Spacer()

            Button(action: { dismiss() }) {
                Text("Done")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .padding()
        }
        .sheet(isPresented: $paywallCoordinator.showArchivePaywall) {
            RemoteArchivePaywallView()
                .presentationDetents([.large])
        }
    }
}

struct StatItem: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.blue)

            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

#Preview {
    CleanupConfirmationView(viewModel: HomeViewModel())
}
