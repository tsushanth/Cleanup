import Foundation

@MainActor
class FreeUsageManager: ObservableObject {
    static let shared = FreeUsageManager()

    private let defaults = UserDefaults.standard
    private let freeLimit = 5

    enum Category: String, CaseIterable {
        case duplicatePhotos = "free_usage_duplicate_photos"
        case similarPhotos = "free_usage_similar_photos"
        case screenshots = "free_usage_screenshots"
        case videos = "free_usage_videos"
        case contacts = "free_usage_contacts"
    }

    @Published var usageCounts: [Category: Int] = [:]

    private init() {
        for category in Category.allCases {
            usageCounts[category] = defaults.integer(forKey: category.rawValue)
        }
    }

    func remainingFreeActions(for category: Category) -> Int {
        let used = usageCounts[category] ?? 0
        return max(0, freeLimit - used)
    }

    func hasFreeDeletionsAvailable(for category: Category) -> Bool {
        remainingFreeActions(for: category) > 0
    }

    /// Returns the number of items that can be processed for free from the requested count.
    func clampToFreeLimit(requestedCount: Int, category: Category) -> Int {
        let remaining = remainingFreeActions(for: category)
        return min(requestedCount, remaining)
    }

    func recordUsage(count: Int, category: Category) {
        let current = usageCounts[category] ?? 0
        let newCount = current + count
        usageCounts[category] = newCount
        defaults.set(newCount, forKey: category.rawValue)
    }

    func usedCount(for category: Category) -> Int {
        usageCounts[category] ?? 0
    }
}
