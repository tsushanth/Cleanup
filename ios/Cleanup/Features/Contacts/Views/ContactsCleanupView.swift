import SwiftUI
import Contacts

struct ContactsCleanupView: View {
    @StateObject private var viewModel = ContactsCleanupViewModel()
    @EnvironmentObject var appState: AppState
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var freeUsage = FreeUsageManager.shared

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Free tier banner
                if !entitlementManager.isPro {
                    FreeUsageBanner(
                        remaining: freeUsage.remainingFreeActions(for: .contacts),
                        category: .contacts
                    )
                }

                Picker("Category", selection: $appState.contactsSelectedSegment) {
                    Text("Duplicates").tag(0)
                    Text("Incomplete").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()

                TabView(selection: $appState.contactsSelectedSegment) {
                    DuplicateContactsListView(viewModel: viewModel)
                        .tag(0)

                    IncompleteContactsListView(viewModel: viewModel)
                        .tag(1)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .navigationTitle("Contacts Cleanup")
            .onAppear {
                if !appState.hasContactPermission {
                    Task {
                        await appState.requestContactPermission()
                    }
                }
                viewModel.scan()
            }
            .overlay {
                if viewModel.isScanning {
                    ScanningOverlayView(progress: 0, message: "Scanning contacts...")
                }
            }
        }
    }
}

// MARK: - Duplicate Contacts List
struct DuplicateContactsListView: View {
    @ObservedObject var viewModel: ContactsCleanupViewModel

    var body: some View {
        Group {
            if viewModel.duplicateGroups.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "checkmark.circle.fill",
                    title: "No Duplicates",
                    subtitle: "Your contacts are clean!"
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(viewModel.duplicateGroups) { group in
                            DuplicateContactGroupCard(group: group, viewModel: viewModel)
                        }
                    }
                    .padding()
                }
            }
        }
    }
}

struct DuplicateContactGroupCard: View {
    let group: DuplicateContactGroup
    @ObservedObject var viewModel: ContactsCleanupViewModel
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @StateObject private var archiveViewModel = ArchiveViewModel()
    @State private var showMergeSheet = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(group.contacts.count) duplicates")
                    .font(.headline)
                Spacer()
                Text(group.matchReason.rawValue)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.orange.opacity(0.2))
                    .foregroundColor(.orange)
                    .cornerRadius(8)
            }

            ForEach(group.contacts) { contact in
                ContactRowView(contact: contact)
            }

            HStack {
                Button("Merge All") {
                    attemptMerge()
                }
                .buttonStyle(.bordered)

                Button("Archive") {
                    let duplicates = Array(group.contacts.dropFirst())
                    let totalBytes = Int64(duplicates.count * 1024) // ~1KB per contact vCard
                    let result = paywallCoordinator.checkArchiveAccess(
                        requiredBytes: totalBytes,
                        context: .attemptArchive(count: duplicates.count, totalGB: Double(totalBytes) / 1_073_741_824)
                    )
                    if result == .allowed {
                        archiveViewModel.archiveSelectedContacts(duplicates)
                    }
                }
                .buttonStyle(.bordered)
                .tint(.cyan)

                Spacer()

                Button("Delete Duplicates") {
                    attemptDelete()
                }
                .buttonStyle(.bordered)
                .tint(.red)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
        .sheet(isPresented: $showMergeSheet) {
            MergeContactsSheet(group: group, viewModel: viewModel)
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
                viewModel.rescan()
            }
        } message: {
            Text("\(archiveViewModel.archiveSuccessCount) items have been archived to the cloud. They are uploading in the background.")
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .mergeContacts(let groupId) where groupId == group.id:
                    showMergeSheet = true
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptMerge() {
        if entitlementManager.isPro {
            showMergeSheet = true
        } else {
            let remaining = FreeUsageManager.shared.remainingFreeActions(for: .contacts)
            if remaining > 0 {
                showMergeSheet = true
                FreeUsageManager.shared.recordUsage(count: 1, category: .contacts)
            } else {
                paywallCoordinator.showPaywall(
                    context: .attemptMergeContacts(count: group.contacts.count),
                    pendingAction: .mergeContacts(groupId: group.id)
                )
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            viewModel.deleteDuplicatesInGroup(group)
        } else {
            let remaining = FreeUsageManager.shared.remainingFreeActions(for: .contacts)
            if remaining > 0 {
                viewModel.deleteDuplicatesInGroup(group)
                FreeUsageManager.shared.recordUsage(count: 1, category: .contacts)
            } else {
                paywallCoordinator.showPaywall(
                    context: .attemptMergeContacts(count: group.contacts.count),
                    pendingAction: .mergeContacts(groupId: group.id)
                )
            }
        }
    }
}

struct ContactRowView: View {
    let contact: ContactItem

    var body: some View {
        HStack(spacing: 12) {
            // Avatar
            Circle()
                .fill(Color.blue.opacity(0.2))
                .frame(width: 44, height: 44)
                .overlay {
                    Text(contact.fullName.prefix(1).uppercased())
                        .font(.headline)
                        .foregroundColor(.blue)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(contact.fullName)
                    .font(.subheadline)
                    .fontWeight(.medium)

                if let phone = contact.phoneNumbers.first {
                    Text(phone)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                if let email = contact.emailAddresses.first {
                    Text(email)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Merge Contacts Sheet
struct MergeContactsSheet: View {
    let group: DuplicateContactGroup
    @ObservedObject var viewModel: ContactsCleanupViewModel
    @Environment(\.dismiss) var dismiss
    @State private var selectedPrimaryIndex = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text("Select the primary contact to keep. Information from other contacts will be merged into it.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding()

                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(Array(group.contacts.enumerated()), id: \.element.id) { index, contact in
                            HStack {
                                ContactRowView(contact: contact)

                                Image(systemName: selectedPrimaryIndex == index ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(selectedPrimaryIndex == index ? .blue : .gray)
                            }
                            .padding()
                            .background(selectedPrimaryIndex == index ? Color.blue.opacity(0.1) : Color(.systemGray6))
                            .cornerRadius(12)
                            .onTapGesture {
                                selectedPrimaryIndex = index
                            }
                        }
                    }
                    .padding()
                }

                Button("Merge Contacts") {
                    viewModel.mergeContacts(group, primaryIndex: selectedPrimaryIndex)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .padding()
            }
            .navigationTitle("Merge Contacts")
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
}

// MARK: - Incomplete Contacts List
struct IncompleteContactsListView: View {
    @ObservedObject var viewModel: ContactsCleanupViewModel
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @State private var selectAll = false

    var body: some View {
        Group {
            if viewModel.incompleteContacts.isEmpty && !viewModel.isScanning {
                EmptyStateView(
                    icon: "checkmark.circle.fill",
                    title: "No Incomplete Contacts",
                    subtitle: "All contacts have complete information"
                )
            } else {
                VStack(spacing: 0) {
                    HStack {
                        Text("\(viewModel.incompleteContacts.count) incomplete")
                            .font(.headline)
                        Spacer()
                        Button(selectAll ? "Deselect All" : "Select All") {
                            selectAll.toggle()
                            viewModel.selectAllIncomplete(selectAll)
                        }
                    }
                    .padding()

                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(viewModel.incompleteContacts) { contact in
                                IncompleteContactRow(
                                    contact: contact,
                                    isSelected: viewModel.isIncompleteSelected(contact)
                                ) {
                                    viewModel.toggleIncompleteSelection(contact)
                                }
                            }
                        }
                        .padding()
                    }

                    if viewModel.selectedIncompleteCount > 0 {
                        Button("Delete Selected (\(viewModel.selectedIncompleteCount))") {
                            attemptDelete()
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                        .padding()
                    }
                }
            }
        }
        .onChange(of: entitlementManager.isPro) { isPro in
            if isPro, let pending = paywallCoordinator.pendingAction {
                switch pending {
                case .mergeContacts:
                    // Delete incomplete contacts after purchase
                    viewModel.deleteSelectedIncomplete()
                    paywallCoordinator.clearPendingAction()
                default:
                    break
                }
            }
        }
    }

    private func attemptDelete() {
        if entitlementManager.isPro {
            viewModel.deleteSelectedIncomplete()
        } else {
            let remaining = FreeUsageManager.shared.remainingFreeActions(for: .contacts)
            if remaining > 0 && viewModel.selectedIncompleteCount <= remaining {
                viewModel.deleteSelectedIncomplete()
                FreeUsageManager.shared.recordUsage(count: viewModel.selectedIncompleteCount, category: .contacts)
            } else {
                paywallCoordinator.showPaywall(
                    context: .attemptMergeContacts(count: viewModel.selectedIncompleteCount),
                    pendingAction: .mergeContacts(groupId: UUID())
                )
            }
        }
    }
}

struct IncompleteContactRow: View {
    let contact: ContactItem
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        HStack {
            ContactRowView(contact: contact)

            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundColor(isSelected ? .blue : .gray)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .onTapGesture {
            onTap()
        }
    }
}

#Preview {
    ContactsCleanupView()
        .environmentObject(AppState())
}
