import SwiftUI
import TikTokBusinessSDK
import FirebaseCore
import AppTrackingTransparency

// MARK: - App Delegate (TikTok + Firebase + RevenueCat)

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Firebase
        FirebaseApp.configure()

        // TikTok
        let tiktokAppId = "7604492390119866375"
        if let config = TikTokConfig(appId: Bundle.main.bundleIdentifier ?? "", tiktokAppId: tiktokAppId) {
            config.setLogLevel(TikTokLogLevel(0))
            TikTokBusiness.initializeSdk(config)
        }

        // RevenueCat
        EntitlementManager.shared.configure()

        return true
    }
}

@main
struct CleanupApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState()
    @ObservedObject private var entitlementManager = EntitlementManager.shared
    @ObservedObject private var paywallCoordinator = PaywallCoordinator.shared
    @AppStorage("has_completed_onboarding") private var hasCompletedOnboarding = false

    var body: some Scene {
        WindowGroup {
            if hasCompletedOnboarding {
                ContentView()
                    .environmentObject(appState)
                    .environmentObject(entitlementManager)
                    .environmentObject(paywallCoordinator)
                    .task {
                        await entitlementManager.refreshEntitlements()
                    }
                    .task {
                        // Delay slightly so the app UI is visible before the prompt
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        ATTrackingManager.requestTrackingAuthorization { _ in }
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
    }
}
