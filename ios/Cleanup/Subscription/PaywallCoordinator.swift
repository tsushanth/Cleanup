import SwiftUI

// MARK: - Paywall Context
enum PaywallContext {
    case generic
    case attemptCleanup(totalGB: Double, itemCount: Int)
    case attemptDeleteDuplicates(count: Int, savedGB: Double)
    case attemptDeleteSimilar(count: Int, savedGB: Double)
    case attemptDeleteScreenshots(count: Int, savedGB: Double)
    case attemptDeleteVideos(count: Int, savedGB: Double)
    case attemptCompressVideo(savedGB: Double)
    case attemptMergeContacts(count: Int)
    case attemptArchive(count: Int, totalGB: Double)
    case archiveStorageFull(usedGB: Double, limitGB: Double)

    var title: String {
        switch self {
        case .generic:
            return "Unlock Cleanup Pro"
        case .attemptCleanup(let totalGB, _):
            return "Free up \(String(format: "%.1f", totalGB)) GB instantly"
        case .attemptDeleteDuplicates(let count, _):
            return "Remove \(count) duplicates in one tap"
        case .attemptDeleteSimilar(let count, _):
            return "Clean up \(count) similar photos"
        case .attemptDeleteScreenshots(let count, _):
            return "Delete \(count) screenshots instantly"
        case .attemptDeleteVideos(let count, let savedGB):
            return "Free up \(String(format: "%.1f", savedGB)) GB from \(count) videos"
        case .attemptCompressVideo(let savedGB):
            return "Save \(String(format: "%.1f", savedGB)) GB with compression"
        case .attemptMergeContacts(let count):
            return "Merge \(count) duplicate contacts"
        case .attemptArchive(let count, let totalGB):
            return "Archive \(count) items (\(String(format: "%.1f", totalGB)) GB)"
        case .archiveStorageFull(let usedGB, let limitGB):
            return "Storage full (\(String(format: "%.1f", usedGB))/\(String(format: "%.0f", limitGB)) GB)"
        }
    }

    var subtitle: String {
        switch self {
        case .generic:
            return "Unlimited cleanups & duplicate removal"
        case .attemptCleanup, .attemptDeleteDuplicates, .attemptDeleteSimilar:
            return "Unlimited photo cleanups included"
        case .attemptDeleteScreenshots:
            return "Bulk delete screenshots anytime"
        case .attemptDeleteVideos, .attemptCompressVideo:
            return "Compress & delete large videos"
        case .attemptMergeContacts:
            return "Keep your contacts organized"
        case .attemptArchive:
            return "Keep files safe in the cloud instead of deleting"
        case .archiveStorageFull:
            return "Upgrade your archive plan for more space"
        }
    }

    var analyticsName: String {
        switch self {
        case .generic: return "generic"
        case .attemptCleanup: return "attempt_cleanup"
        case .attemptDeleteDuplicates: return "attempt_delete_duplicates"
        case .attemptDeleteSimilar: return "attempt_delete_similar"
        case .attemptDeleteScreenshots: return "attempt_delete_screenshots"
        case .attemptDeleteVideos: return "attempt_delete_videos"
        case .attemptCompressVideo: return "attempt_compress_video"
        case .attemptMergeContacts: return "attempt_merge_contacts"
        case .attemptArchive: return "attempt_archive"
        case .archiveStorageFull: return "archive_storage_full"
        }
    }
}

// MARK: - Pending Cleanup Action
enum PendingCleanupAction {
    case deleteDuplicates(ids: Set<String>)
    case deleteSimilar(ids: Set<String>)
    case deleteScreenshots(ids: Set<String>)
    case deleteVideos(ids: Set<String>)
    case compressVideo(id: String)
    case mergeContacts(groupId: UUID)
    case oneTapCleanup
    case archivePhotos(ids: Set<String>)
    case archiveVideos(ids: Set<String>)
    case archiveContacts(ids: [String])
}

// MARK: - Paywall Coordinator
@MainActor
class PaywallCoordinator: ObservableObject {
    static let shared = PaywallCoordinator()

    @Published var showPaywall: Bool = false
    @Published var paywallContext: PaywallContext = .generic
    @Published var pendingAction: PendingCleanupAction?
    @Published var paywallDismissCount: Int = 0
    @Published var showWinbackOffer: Bool = false

    // Archive paywall state
    @Published var showArchivePaywall: Bool = false
    @Published var archivePaywallContext: PaywallContext = .generic

    private let defaults = UserDefaults.standard
    private let dismissCountKey = "paywall_dismiss_count"
    private let lastDismissDateKey = "paywall_last_dismiss_date"
    private let winbackShownDateKey = "winback_last_shown_date"

    private init() {
        paywallDismissCount = defaults.integer(forKey: dismissCountKey)
    }

    // MARK: - Show Paywall
    func showPaywall(context: PaywallContext, pendingAction: PendingCleanupAction? = nil) {
        self.paywallContext = context
        self.pendingAction = pendingAction
        self.showPaywall = true
    }

    // MARK: - Dismiss Paywall
    func dismissPaywall() {
        showPaywall = false
    }

    // MARK: - Track Dismiss (called from sheet onDismiss when user didn't purchase)
    func trackDismiss() {
        paywallDismissCount += 1
        defaults.set(paywallDismissCount, forKey: dismissCountKey)
        defaults.set(Date().timeIntervalSince1970, forKey: lastDismissDateKey)
    }

    // MARK: - Clear Pending Action
    func clearPendingAction() {
        pendingAction = nil
    }

    // MARK: - Check Access and Show Paywall if Needed
    /// Returns .pro if user is pro, .freeTier if free deletions available, .paywall if paywall was shown
    enum AccessResult {
        case pro
        case freeTier(remaining: Int)
        case paywall
    }

    func checkAccess(
        category: FreeUsageManager.Category,
        requestedCount: Int,
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = nil
    ) -> AccessResult {
        if EntitlementManager.shared.isPro {
            return .pro
        }

        let remaining = FreeUsageManager.shared.remainingFreeActions(for: category)
        if remaining > 0 {
            return .freeTier(remaining: remaining)
        }

        showPaywall(context: context, pendingAction: pendingAction)
        return .paywall
    }

    /// Returns true if user has access (isPro or free tier), false if paywall was shown
    func checkAccessOrShowPaywall(
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = nil
    ) -> Bool {
        if EntitlementManager.shared.isPro {
            return true
        } else {
            showPaywall(context: context, pendingAction: pendingAction)
            return false
        }
    }

    // MARK: - Archive Access Check

    enum ArchiveAccessResult {
        case allowed
        case needsArchiveSubscription
        case quotaExceeded
    }

    func checkArchiveAccess(
        requiredBytes: Int64,
        context: PaywallContext,
        pendingAction: PendingCleanupAction? = nil
    ) -> ArchiveAccessResult {
        guard EntitlementManager.shared.hasArchiveSubscription else {
            showArchivePaywall = true
            archivePaywallContext = context
            self.pendingAction = pendingAction
            return .needsArchiveSubscription
        }

        // Quota is checked in confirmArchive() using local subscription tier
        return .allowed
    }

    // MARK: - Winback Offer Logic

    /// Call this on app foreground or after paywall dismiss to check if winback should show
    func checkWinbackEligibility() {
        guard !EntitlementManager.shared.isPro else {
            showWinbackOffer = false
            return
        }
        // Show winback after user has dismissed the paywall 3+ times
        guard paywallDismissCount >= 3 else {
            showWinbackOffer = false
            return
        }
        // Only show if last dismiss was at least 1 day ago (give them breathing room)
        let lastDismiss = defaults.double(forKey: lastDismissDateKey)
        guard lastDismiss > 0 else {
            showWinbackOffer = false
            return
        }
        let daysSinceLastDismiss = (Date().timeIntervalSince1970 - lastDismiss) / 86400
        guard daysSinceLastDismiss >= 1 else {
            showWinbackOffer = false
            return
        }
        // Don't show if we already showed winback today
        let lastWinbackShown = defaults.double(forKey: winbackShownDateKey)
        if lastWinbackShown > 0 {
            let daysSinceWinback = (Date().timeIntervalSince1970 - lastWinbackShown) / 86400
            guard daysSinceWinback >= 1 else {
                showWinbackOffer = false
                return
            }
        }
        showWinbackOffer = true
    }

    func markWinbackShown() {
        defaults.set(Date().timeIntervalSince1970, forKey: winbackShownDateKey)
        showWinbackOffer = false
    }

    /// Whether user qualifies for a nudge banner (lower threshold than full winback sheet)
    var shouldShowNudgeBanner: Bool {
        guard !EntitlementManager.shared.isPro else { return false }
        return paywallDismissCount >= 2
    }
}

// MARK: - Paywall Features
struct LocalPaywallFeature: Identifiable {
    let id = UUID()
    let icon: String
    let title: String
    let description: String

    static let allFeatures: [LocalPaywallFeature] = [
        LocalPaywallFeature(
            icon: "sparkles",
            title: "Unlimited Cleanups",
            description: "Clean your device as often as you want"
        ),
        LocalPaywallFeature(
            icon: "photo.on.rectangle.angled",
            title: "Delete Duplicates & Similar",
            description: "Find and remove duplicate photos instantly"
        ),
        LocalPaywallFeature(
            icon: "video.fill",
            title: "Large Video Management",
            description: "Compress or delete space-hogging videos"
        ),
        LocalPaywallFeature(
            icon: "lock.shield",
            title: "Private & Secure",
            description: "All scanning happens on your device"
        )
    ]
}
