import SwiftUI

struct ChargingAnimationsView: View {
    @StateObject private var viewModel = ChargingAnimationsViewModel()
    @State private var selectedAnimation: ChargingAnimation?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Current Animation
                    CurrentAnimationCard(
                        animation: viewModel.currentAnimation,
                        isEnabled: viewModel.isEnabled
                    )

                    // Enable Toggle
                    Toggle("Enable Charging Animation", isOn: $viewModel.isEnabled)
                        .padding()
                        .background(Color(.systemBackground))
                        .cornerRadius(12)

                    // Animation Grid
                    LazyVGrid(columns: [
                        GridItem(.flexible()),
                        GridItem(.flexible())
                    ], spacing: 16) {
                        ForEach(viewModel.animations) { animation in
                            AnimationPreviewCard(
                                animation: animation,
                                isSelected: viewModel.currentAnimation?.id == animation.id,
                                isPremium: animation.isPremium && !viewModel.isPremiumUser
                            ) {
                                if !animation.isPremium || viewModel.isPremiumUser {
                                    viewModel.selectAnimation(animation)
                                } else {
                                    selectedAnimation = animation
                                }
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Charging Animations")
            .sheet(item: $selectedAnimation) { animation in
                PremiumAnimationSheet(animation: animation)
            }
        }
    }
}

// MARK: - Current Animation Card
struct CurrentAnimationCard: View {
    let animation: ChargingAnimation?
    let isEnabled: Bool

    var body: some View {
        VStack(spacing: 16) {
            // Preview Area
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.black)
                    .frame(height: 200)

                if isEnabled, let animation = animation {
                    AnimationPreview(animation: animation)
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 40))
                            .foregroundColor(.gray)
                        Text("No animation selected")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
            }

            HStack {
                VStack(alignment: .leading) {
                    Text("Current Animation")
                        .font(.headline)
                    Text(animation?.name ?? "None")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
                if isEnabled {
                    Text("Active")
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 10, x: 0, y: 5)
    }
}

// MARK: - Animation Preview Card
struct AnimationPreviewCard: View {
    let animation: ChargingAnimation
    let isSelected: Bool
    let isPremium: Bool
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.black)
                    .frame(height: 120)

                AnimationPreview(animation: animation)
                    .scaleEffect(0.6)

                if isPremium {
                    VStack {
                        HStack {
                            Spacer()
                            Image(systemName: "crown.fill")
                                .foregroundColor(.yellow)
                                .padding(6)
                                .background(Color.black.opacity(0.5))
                                .clipShape(Circle())
                        }
                        Spacer()
                    }
                    .padding(8)
                }

                if isSelected {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.blue, lineWidth: 3)
                }
            }

            Text(animation.name)
                .font(.caption)
                .fontWeight(.medium)
        }
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - Animation Preview
struct AnimationPreview: View {
    let animation: ChargingAnimation
    @State private var isAnimating = false

    var body: some View {
        ZStack {
            switch animation.type {
            case .pulse:
                PulseAnimation(isAnimating: isAnimating, color: animation.color)
            case .wave:
                WaveAnimation(isAnimating: isAnimating, color: animation.color)
            case .particles:
                ParticleAnimation(isAnimating: isAnimating, color: animation.color)
            case .glow:
                GlowAnimation(isAnimating: isAnimating, color: animation.color)
            case .lightning:
                LightningAnimation(isAnimating: isAnimating, color: animation.color)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - Animation Types
struct PulseAnimation: View {
    let isAnimating: Bool
    let color: Color

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 60, height: 60)
            .scaleEffect(isAnimating ? 1.5 : 1.0)
            .opacity(isAnimating ? 0.3 : 1.0)
            .overlay {
                Image(systemName: "bolt.fill")
                    .font(.title)
                    .foregroundColor(.white)
            }
    }
}

struct WaveAnimation: View {
    let isAnimating: Bool
    let color: Color

    var body: some View {
        ZStack {
            ForEach(0..<3) { index in
                Circle()
                    .stroke(color, lineWidth: 2)
                    .frame(width: CGFloat(40 + index * 30), height: CGFloat(40 + index * 30))
                    .scaleEffect(isAnimating ? 1.5 : 1.0)
                    .opacity(isAnimating ? 0 : 1)
            }
            Image(systemName: "bolt.fill")
                .font(.title)
                .foregroundColor(color)
        }
    }
}

struct ParticleAnimation: View {
    let isAnimating: Bool
    let color: Color

    var body: some View {
        ZStack {
            ForEach(0..<8) { index in
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                    .offset(y: isAnimating ? -50 : 0)
                    .rotationEffect(.degrees(Double(index) * 45))
                    .opacity(isAnimating ? 0 : 1)
            }
            Image(systemName: "bolt.fill")
                .font(.title)
                .foregroundColor(color)
        }
    }
}

struct GlowAnimation: View {
    let isAnimating: Bool
    let color: Color

    var body: some View {
        ZStack {
            Circle()
                .fill(color.opacity(0.3))
                .frame(width: 100, height: 100)
                .blur(radius: isAnimating ? 30 : 10)

            Image(systemName: "bolt.fill")
                .font(.system(size: 40))
                .foregroundColor(color)
                .shadow(color: color, radius: isAnimating ? 20 : 5)
        }
    }
}

struct LightningAnimation: View {
    let isAnimating: Bool
    let color: Color

    var body: some View {
        ZStack {
            Image(systemName: "bolt.fill")
                .font(.system(size: 50))
                .foregroundColor(color)
                .opacity(isAnimating ? 1 : 0.5)
                .scaleEffect(isAnimating ? 1.2 : 1.0)

            Image(systemName: "bolt.fill")
                .font(.system(size: 50))
                .foregroundColor(.white.opacity(0.5))
                .blur(radius: isAnimating ? 10 : 0)
        }
    }
}

// MARK: - Premium Sheet
struct PremiumAnimationSheet: View {
    let animation: ChargingAnimation
    @Environment(\.dismiss) var dismiss

    var body: some View {
        VStack(spacing: 20) {
            AnimationPreview(animation: animation)
                .frame(height: 200)
                .background(Color.black)
                .cornerRadius(20)

            Text(animation.name)
                .font(.title2)
                .fontWeight(.bold)

            Text("This animation requires a premium subscription")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Upgrade to Premium") {
                // Navigate to paywall
                dismiss()
            }
            .buttonStyle(.borderedProminent)

            Button("Cancel") {
                dismiss()
            }
            .foregroundColor(.secondary)
        }
        .padding()
    }
}

#Preview {
    ChargingAnimationsView()
}
