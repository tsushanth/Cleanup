import Foundation
import Contacts

actor ContactService {
    static let shared = ContactService()

    private let store = CNContactStore()

    /// Cached scan results to avoid rescanning when navigating between tabs
    private var cachedDuplicates: ContactScanResult?
    private var cachedIncomplete: IncompleteContactResult?

    private init() {}

    func invalidateCache() {
        cachedDuplicates = nil
        cachedIncomplete = nil
    }

    // MARK: - Find Duplicate Contacts
    func findDuplicates() async -> ContactScanResult {
        if let cached = cachedDuplicates { return cached }

        let keysToFetch: [CNKeyDescriptor] = [
            CNContactGivenNameKey as CNKeyDescriptor,
            CNContactFamilyNameKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactEmailAddressesKey as CNKeyDescriptor,
            CNContactImageDataAvailableKey as CNKeyDescriptor,
            CNContactThumbnailImageDataKey as CNKeyDescriptor,
            CNContactFormatter.descriptorForRequiredKeys(for: .fullName)
        ]

        let request = CNContactFetchRequest(keysToFetch: keysToFetch)

        var allContacts: [ContactItem] = []
        var duplicateGroups: [DuplicateContactGroup] = []

        do {
            try store.enumerateContacts(with: request) { contact, _ in
                let item = ContactItem(
                    id: contact.identifier,
                    contact: contact
                )
                allContacts.append(item)
            }
        } catch {
            // Fetch failed
            return ContactScanResult(count: 0, groups: [])
        }

        // Find duplicates by name
        var nameGroups: [String: [ContactItem]] = [:]
        for contact in allContacts {
            let normalizedName = contact.fullName.lowercased().trimmingCharacters(in: .whitespaces)
            if !normalizedName.isEmpty && normalizedName != "no name" {
                if var existing = nameGroups[normalizedName] {
                    existing.append(contact)
                    nameGroups[normalizedName] = existing
                } else {
                    nameGroups[normalizedName] = [contact]
                }
            }
        }

        for (_, contacts) in nameGroups where contacts.count > 1 {
            let group = DuplicateContactGroup(
                contacts: contacts,
                matchReason: .sameName
            )
            duplicateGroups.append(group)
        }

        // Find duplicates by phone number
        var phoneGroups: [String: [ContactItem]] = [:]
        for contact in allContacts {
            for phone in contact.phoneNumbers {
                let normalizedPhone = normalizePhoneNumber(phone)
                if !normalizedPhone.isEmpty {
                    if var existing = phoneGroups[normalizedPhone] {
                        // Avoid adding same contact twice
                        if !existing.contains(where: { $0.id == contact.id }) {
                            existing.append(contact)
                            phoneGroups[normalizedPhone] = existing
                        }
                    } else {
                        phoneGroups[normalizedPhone] = [contact]
                    }
                }
            }
        }

        for (_, contacts) in phoneGroups where contacts.count > 1 {
            // Check if this group is already covered by name matching
            let ids = Set(contacts.map { $0.id })
            let alreadyGrouped = duplicateGroups.contains { group in
                let groupIds = Set(group.contacts.map { $0.id })
                return !ids.isDisjoint(with: groupIds)
            }

            if !alreadyGrouped {
                let group = DuplicateContactGroup(
                    contacts: contacts,
                    matchReason: .samePhone
                )
                duplicateGroups.append(group)
            }
        }

        let totalDuplicates = duplicateGroups.reduce(0) { $0 + $1.contacts.count - 1 }

        let result = ContactScanResult(count: totalDuplicates, groups: duplicateGroups)
        cachedDuplicates = result
        return result
    }

    // MARK: - Find Incomplete Contacts
    func findIncompleteContacts() async -> IncompleteContactResult {
        if let cached = cachedIncomplete { return cached }

        let keysToFetch: [CNKeyDescriptor] = [
            CNContactGivenNameKey as CNKeyDescriptor,
            CNContactFamilyNameKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactEmailAddressesKey as CNKeyDescriptor,
            CNContactFormatter.descriptorForRequiredKeys(for: .fullName)
        ]

        let request = CNContactFetchRequest(keysToFetch: keysToFetch)

        var incompleteContacts: [ContactItem] = []

        do {
            try store.enumerateContacts(with: request) { contact, _ in
                let item = ContactItem(
                    id: contact.identifier,
                    contact: contact
                )

                // Check if incomplete (no name, no phone, no email)
                if item.isIncomplete {
                    incompleteContacts.append(item)
                }
            }
        } catch {
            // Fetch failed
        }

        let result = IncompleteContactResult(count: incompleteContacts.count, contacts: incompleteContacts)
        cachedIncomplete = result
        return result
    }

    // MARK: - Merge Contacts
    func mergeContacts(_ contacts: [ContactItem], into primaryContact: ContactItem) async throws {
        let mutableContact = primaryContact.contact.mutableCopy() as! CNMutableContact

        // Collect all phone numbers from other contacts
        var existingPhones = Set(primaryContact.contact.phoneNumbers.map { $0.value.stringValue })
        var newPhoneNumbers = mutableContact.phoneNumbers

        for contact in contacts where contact.id != primaryContact.id {
            for phoneNumber in contact.contact.phoneNumbers {
                let phoneString = phoneNumber.value.stringValue
                if !existingPhones.contains(phoneString) {
                    existingPhones.insert(phoneString)
                    newPhoneNumbers.append(phoneNumber)
                }
            }
        }
        mutableContact.phoneNumbers = newPhoneNumbers

        // Collect all email addresses from other contacts
        var existingEmails = Set(primaryContact.contact.emailAddresses.map { $0.value as String })
        var newEmails = mutableContact.emailAddresses

        for contact in contacts where contact.id != primaryContact.id {
            for email in contact.contact.emailAddresses {
                let emailString = email.value as String
                if !existingEmails.contains(emailString) {
                    existingEmails.insert(emailString)
                    newEmails.append(email)
                }
            }
        }
        mutableContact.emailAddresses = newEmails

        // Update primary contact
        let saveRequest = CNSaveRequest()
        saveRequest.update(mutableContact)

        // Delete other contacts
        for contact in contacts where contact.id != primaryContact.id {
            let mutableToDelete = contact.contact.mutableCopy() as! CNMutableContact
            saveRequest.delete(mutableToDelete)
        }

        try store.execute(saveRequest)
    }

    // MARK: - Delete Contacts
    func deleteContacts(_ contacts: [ContactItem]) async throws {
        let saveRequest = CNSaveRequest()

        for contact in contacts {
            let mutableContact = contact.contact.mutableCopy() as! CNMutableContact
            saveRequest.delete(mutableContact)
        }

        try store.execute(saveRequest)
    }

    // MARK: - Helpers
    private func normalizePhoneNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        // Return last 10 digits for comparison
        if digits.count >= 10 {
            return String(digits.suffix(10))
        }
        return digits
    }
}
