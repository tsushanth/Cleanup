import StoreKit
import UIKit

enum AppReviewManager {
    private static let hasRequestedReviewKey = "has_requested_first_review"

    static func requestReviewIfNeeded() {
        guard !UserDefaults.standard.bool(forKey: hasRequestedReviewKey) else { return }
        UserDefaults.standard.set(true, forKey: hasRequestedReviewKey)

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                SKStoreReviewController.requestReview(in: scene)
            }
        }
    }
}
