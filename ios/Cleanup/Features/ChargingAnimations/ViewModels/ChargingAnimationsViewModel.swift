import SwiftUI

struct ChargingAnimation: Identifiable {
    let id = UUID()
    let name: String
    let type: AnimationType
    let color: Color
    let isPremium: Bool

    enum AnimationType {
        case pulse
        case wave
        case particles
        case glow
        case lightning
    }
}

@MainActor
class ChargingAnimationsViewModel: ObservableObject {
    @Published var animations: [ChargingAnimation] = []
    @Published var currentAnimation: ChargingAnimation?
    @Published var isEnabled: Bool = false
    @Published var isPremiumUser: Bool = false

    private let selectedAnimationKey = "selected_charging_animation"
    private let animationEnabledKey = "charging_animation_enabled"

    init() {
        loadAnimations()
        loadSettings()
    }

    private func loadAnimations() {
        animations = [
            ChargingAnimation(name: "Blue Pulse", type: .pulse, color: .blue, isPremium: false),
            ChargingAnimation(name: "Green Wave", type: .wave, color: .green, isPremium: false),
            ChargingAnimation(name: "Purple Glow", type: .glow, color: .purple, isPremium: false),
            ChargingAnimation(name: "Golden Particles", type: .particles, color: .yellow, isPremium: true),
            ChargingAnimation(name: "Pink Lightning", type: .lightning, color: .pink, isPremium: true),
            ChargingAnimation(name: "Cyan Wave", type: .wave, color: .cyan, isPremium: true),
            ChargingAnimation(name: "Red Pulse", type: .pulse, color: .red, isPremium: true),
            ChargingAnimation(name: "Orange Glow", type: .glow, color: .orange, isPremium: true)
        ]

        // Set default
        if currentAnimation == nil {
            currentAnimation = animations.first
        }
    }

    private func loadSettings() {
        isEnabled = UserDefaults.standard.bool(forKey: animationEnabledKey)

        if let savedIndex = UserDefaults.standard.object(forKey: selectedAnimationKey) as? Int,
           savedIndex < animations.count {
            currentAnimation = animations[savedIndex]
        }
    }

    func selectAnimation(_ animation: ChargingAnimation) {
        currentAnimation = animation
        if let index = animations.firstIndex(where: { $0.id == animation.id }) {
            UserDefaults.standard.set(index, forKey: selectedAnimationKey)
        }
    }

    func toggleEnabled(_ enabled: Bool) {
        isEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: animationEnabledKey)
    }
}

// Note: Implementing actual charging animations on iOS requires:
// 1. A custom Shortcut Automation triggered on "When Charger is Connected"
// 2. The automation opens the app or plays a specific animation
// 3. This is a user-configured feature, not automatic
