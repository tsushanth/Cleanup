import SwiftUI
import Photos
import Contacts

@MainActor
class AppState: ObservableObject {
    @Published var hasPhotoPermission = false
    @Published var hasContactPermission = false
    @Published var storageInfo: StorageInfo?
    @Published var isLoading = false
    @Published var selectedTab: TabItem = .home

    // Selected segments for each tab (to allow deep linking from HomeView)
    @Published var photosSelectedSegment: Int = 0  // 0=Duplicates, 1=Similar, 2=Screenshots
    @Published var videosSelectedSegment: Int = 0  // 0=Large Videos, 1=Compress
    @Published var contactsSelectedSegment: Int = 0 // 0=Duplicates, 1=Incomplete

    enum TabItem: String, CaseIterable {
        case home = "Home"
        case photos = "Photos"
        case videos = "Videos"
        case contacts = "Contacts"
        case settings = "Settings"

        var icon: String {
            switch self {
            case .home: return "house.fill"
            case .photos: return "photo.fill"
            case .videos: return "video.fill"
            case .contacts: return "person.2.fill"
            case .settings: return "gearshape.fill"
            }
        }
    }

    init() {
        checkPermissions()
        loadStorageInfo()
    }

    func checkPermissions() {
        // Photo permission
        let photoStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        hasPhotoPermission = photoStatus == .authorized || photoStatus == .limited

        // Contact permission
        let contactStatus = CNContactStore.authorizationStatus(for: .contacts)
        hasContactPermission = contactStatus == .authorized
    }

    func requestPhotoPermission() async {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        await MainActor.run {
            hasPhotoPermission = status == .authorized || status == .limited
        }
    }

    func requestContactPermission() async {
        let store = CNContactStore()
        do {
            let granted = try await store.requestAccess(for: .contacts)
            await MainActor.run {
                hasContactPermission = granted
            }
        } catch {
            // Permission request failed
        }
    }

    func loadStorageInfo() {
        Task {
            let info = await StorageService.shared.getStorageInfo()
            await MainActor.run {
                self.storageInfo = info
            }
        }
    }
}
