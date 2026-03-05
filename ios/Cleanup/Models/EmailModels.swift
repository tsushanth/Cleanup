import Foundation

struct EmailCategory: Identifiable {
    let id = UUID()
    let type: EmailType
    var count: Int
    var emails: [EmailItem]

    enum EmailType: String, CaseIterable {
        case promotional = "Promotional"
        case spam = "Spam"
        case unread = "Unread"
        case newsletters = "Newsletters"
        case social = "Social"

        var icon: String {
            switch self {
            case .promotional: return "tag.fill"
            case .spam: return "xmark.shield.fill"
            case .unread: return "envelope.badge.fill"
            case .newsletters: return "newspaper.fill"
            case .social: return "person.2.fill"
            }
        }

        var color: String {
            switch self {
            case .promotional: return "orange"
            case .spam: return "red"
            case .unread: return "blue"
            case .newsletters: return "purple"
            case .social: return "green"
            }
        }
    }
}

struct EmailItem: Identifiable, Hashable {
    let id: String
    let subject: String
    let sender: String
    let senderEmail: String
    let receivedDate: Date
    let isRead: Bool
    let category: EmailCategory.EmailType
    var isSelected: Bool = false

    var dateFormatted: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: receivedDate, relativeTo: Date())
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: EmailItem, rhs: EmailItem) -> Bool {
        lhs.id == rhs.id
    }
}

struct EmailScanResult {
    let totalCount: Int
    let categories: [EmailCategory]
}
