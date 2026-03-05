import Foundation
import Contacts

struct ContactItem: Identifiable, Hashable {
    let id: String
    let contact: CNContact
    var isSelected: Bool = false

    var fullName: String {
        CNContactFormatter.string(from: contact, style: .fullName) ?? "No Name"
    }

    var phoneNumbers: [String] {
        contact.phoneNumbers.map { $0.value.stringValue }
    }

    var emailAddresses: [String] {
        contact.emailAddresses.map { $0.value as String }
    }

    var hasImage: Bool {
        contact.imageDataAvailable
    }

    var isIncomplete: Bool {
        fullName.isEmpty || (phoneNumbers.isEmpty && emailAddresses.isEmpty)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: ContactItem, rhs: ContactItem) -> Bool {
        lhs.id == rhs.id
    }
}

struct DuplicateContactGroup: Identifiable {
    let id = UUID()
    var contacts: [ContactItem]
    var mergedContact: CNMutableContact?

    var matchReason: MatchReason

    enum MatchReason: String {
        case sameName = "Same Name"
        case samePhone = "Same Phone"
        case sameEmail = "Same Email"
        case similar = "Similar"
    }
}

struct ContactScanResult {
    let count: Int
    let groups: [DuplicateContactGroup]
}

struct IncompleteContactResult {
    let count: Int
    let contacts: [ContactItem]
}
