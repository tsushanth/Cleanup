import SwiftUI
import Contacts

@MainActor
class ContactsCleanupViewModel: ObservableObject {
    @Published var duplicateGroups: [DuplicateContactGroup] = []
    @Published var incompleteContacts: [ContactItem] = []
    @Published var selectedIncomplete: Set<String> = []
    @Published var isScanning = false

    private let contactService = ContactService.shared
    private var hasScanned = false

    var selectedIncompleteCount: Int {
        selectedIncomplete.count
    }

    // MARK: - Scanning
    func scan() {
        guard !isScanning && !hasScanned else { return }

        Task {
            isScanning = true

            async let duplicatesTask = contactService.findDuplicates()
            async let incompleteTask = contactService.findIncompleteContacts()

            let duplicateResult = await duplicatesTask
            let incompleteResult = await incompleteTask

            duplicateGroups = duplicateResult.groups
            incompleteContacts = incompleteResult.contacts

            hasScanned = true
            isScanning = false
        }
    }

    func rescan() {
        hasScanned = false
        Task { await contactService.invalidateCache() }
        scan()
    }

    // MARK: - Duplicate Actions
    func mergeContacts(_ group: DuplicateContactGroup, primaryIndex: Int) {
        guard primaryIndex < group.contacts.count else { return }

        Task {
            let primaryContact = group.contacts[primaryIndex]
            do {
                try await contactService.mergeContacts(group.contacts, into: primaryContact)
                // Remove the merged group locally instead of rescanning
                duplicateGroups.removeAll { $0.id == group.id }
                AppReviewManager.requestReviewIfNeeded()
            } catch {
                // Merge failed silently
            }
        }
    }

    func deleteDuplicatesInGroup(_ group: DuplicateContactGroup) {
        // Keep the first one, delete the rest
        let toDelete = Array(group.contacts.dropFirst())

        Task {
            do {
                try await contactService.deleteContacts(toDelete)
                // Remove the group locally instead of rescanning
                duplicateGroups.removeAll { $0.id == group.id }
                AppReviewManager.requestReviewIfNeeded()
            } catch {
                // Delete failed silently
            }
        }
    }

    // MARK: - Incomplete Contacts
    func isIncompleteSelected(_ contact: ContactItem) -> Bool {
        selectedIncomplete.contains(contact.id)
    }

    func toggleIncompleteSelection(_ contact: ContactItem) {
        if selectedIncomplete.contains(contact.id) {
            selectedIncomplete.remove(contact.id)
        } else {
            selectedIncomplete.insert(contact.id)
        }
    }

    func selectAllIncomplete(_ select: Bool) {
        if select {
            for contact in incompleteContacts {
                selectedIncomplete.insert(contact.id)
            }
        } else {
            selectedIncomplete.removeAll()
        }
    }

    func deleteSelectedIncomplete() {
        let toDelete = incompleteContacts.filter { selectedIncomplete.contains($0.id) }

        Task {
            do {
                try await contactService.deleteContacts(toDelete)
                let deletedIds = selectedIncomplete
                selectedIncomplete.removeAll()
                // Remove deleted items locally instead of rescanning
                incompleteContacts = incompleteContacts.filter { !deletedIds.contains($0.id) }
                AppReviewManager.requestReviewIfNeeded()
            } catch {
                // Delete failed silently
            }
        }
    }
}
