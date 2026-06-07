import SwiftUI
import TikTokBusinessSDK
import FirebaseCore
import AppTrackingTransparency
import RatingKit

// MARK: - App Delegate (Firebase + RevenueCat only — TikTok initialized after ATT)

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Firebase
        FirebaseApp.configure()

        // RevenueCat
        EntitlementManager.shared.configure()

        // Apple Search Ads attribution (AdServices.framework)
        AttributionService.shared.trackAttribution()

        return true
    }

    static func initializeTikTok() {
        let tiktokAppId = "7604492390119866375"
        if let config = TikTokConfig(appId: Bundle.main.bundleIdentifier ?? "", tiktokAppId: tiktokAppId) {
            config.setLogLevel(TikTokLogLevel(0))
            TikTokBusiness.initializeSdk(config)
        }
    }
}

@main
struct CleanupApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @AppStorage("has_completed_onboarding") private var hasCompletedOnboarding = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasRequestedTracking = false

    init() {
        // FASTLANE_SNAPSHOT: skip onboarding and paywalls for automated screenshots
        if ProcessInfo.processInfo.arguments.contains("-FASTLANE_SNAPSHOT") {
            UserDefaults.standard.set(true, forKey: "has_completed_onboarding")
        }

        // Server-driven rating prompts with variant testing + offer-code redemption
        RatingKit.configure(
            appId: "smartspace",
            apiUrl: "https://paywallkit-api.fly.dev"
        )
    }

    var body: some Scene {
        WindowGroup {
            if hasCompletedOnboarding {
                ContentView()
                    .ratingPrompt()
                    .environmentObject(appState)
                    .environmentObject(entitlementManager)
                    .environmentObject(paywallCoordinator)
                    .task {
                        await entitlementManager.refreshEntitlements()
                        RatingKit.shared.trackAppOpen()
                    }
                    .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                        Task {
                            await entitlementManager.refreshEntitlements()
                            paywallCoordinator.checkWinbackEligibility()
                        }
                    }
                    .sheet(isPresented: $paywallCoordinator.showWinbackOffer, onDismiss: {
                        paywallCoordinator.markWinbackShown()
                    }) {
                        WinbackOfferView()
                            .presentationDetents([.large])
                    }
            } else {
                OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                    .environmentObject(appState)
            }
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active && hasCompletedOnboarding && !hasRequestedTracking {
                hasRequestedTracking = true
                ATTrackingManager.requestTrackingAuthorization { status in
                    if status == .authorized {
                        AppDelegate.initializeTikTok()
                    }
                }
            }
        }
    }
}
