import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var entitlementManager: EntitlementManager
    @EnvironmentObject var paywallCoordinator: PaywallCoordinator

    var body: some View {
        TabView(selection: $appState.selectedTab) {
            HomeView()
                .tabItem {
                    Label(AppState.TabItem.home.rawValue, systemImage: AppState.TabItem.home.icon)
                }
                .tag(AppState.TabItem.home)

            PhotosCleanupView()
                .tabItem {
                    Label(AppState.TabItem.photos.rawValue, systemImage: AppState.TabItem.photos.icon)
                }
                .tag(AppState.TabItem.photos)

            VideosCleanupView()
                .tabItem {
                    Label(AppState.TabItem.videos.rawValue, systemImage: AppState.TabItem.videos.icon)
                }
                .tag(AppState.TabItem.videos)

            ContactsCleanupView()
                .tabItem {
                    Label(AppState.TabItem.contacts.rawValue, systemImage: AppState.TabItem.contacts.icon)
                }
                .tag(AppState.TabItem.contacts)

            SettingsView()
                .tabItem {
                    Label(AppState.TabItem.settings.rawValue, systemImage: AppState.TabItem.settings.icon)
                }
                .tag(AppState.TabItem.settings)
        }
        .tint(.blue)
        .sheet(isPresented: $paywallCoordinator.showPaywall, onDismiss: {
            // Only count as a dismiss if user didn't purchase
            if !entitlementManager.isPro {
                paywallCoordinator.trackDismiss()
            }
        }) {
            RemotePaywallView()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .interactiveDismissDisabled(entitlementManager.purchaseInProgress)
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
        .environmentObject(EntitlementManager.shared)
        .environmentObject(PaywallCoordinator.shared)
}
