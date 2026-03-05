import SwiftUI
import Photos
import LocalAuthentication

@MainActor
class VaultViewModel: ObservableObject {
    @Published var items: [VaultItem] = []
    @Published var settings: VaultSettings = VaultSettings()

    private let vaultService = VaultService.shared

    var canUseBiometrics: Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    var biometricType: LABiometryType {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType
    }

    init() {
        loadSettings()
    }

    // MARK: - Settings
    func loadSettings() {
        Task {
            let loadedSettings = await vaultService.getSettings()
            await MainActor.run {
                self.settings = loadedSettings
            }
        }
    }

    func hasPin() async -> Bool {
        let settings = await vaultService.getSettings()
        return settings.isPinEnabled && !settings.pin.isEmpty
    }

    func verifyPin(_ pin: String) async -> Bool {
        await vaultService.verifyPin(pin)
    }

    func setPin(_ pin: String) async {
        await vaultService.setPin(pin)
        await MainActor.run {
            self.settings.pin = pin
            self.settings.isPinEnabled = true
        }
    }

    func authenticateWithBiometrics() async -> Bool {
        await vaultService.authenticateWithBiometrics()
    }

    // MARK: - Items
    func loadItems() {
        Task {
            let loadedItems = await vaultService.getItems()
            await MainActor.run {
                self.items = loadedItems
            }
        }
    }

    func addToVault(asset: PHAsset, deleteOriginal: Bool) async throws {
        let item = try await vaultService.addToVault(asset: asset, deleteOriginal: deleteOriginal)
        await MainActor.run {
            self.items.append(item)
        }
    }

    func removeFromVault(_ item: VaultItem, restoreToPhotos: Bool) async throws {
        try await vaultService.removeFromVault(item, restoreToPhotos: restoreToPhotos)
        await MainActor.run {
            self.items.removeAll { $0.id == item.id }
        }
    }
}
