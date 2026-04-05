import SwiftUI
import WidgetKit

struct WidgetsView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Instructions
                    InstructionsCard()

                    // Widget Previews
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Available Widgets")
                            .font(.headline)

                        WidgetPreviewCard(
                            title: "Storage Widget",
                            description: "See your storage status at a glance",
                            size: "Small & Medium",
                            preview: StorageWidgetPreview()
                        )

                        WidgetPreviewCard(
                            title: "Battery Widget",
                            description: "Monitor your battery level",
                            size: "Small",
                            preview: BatteryWidgetPreview()
                        )

                        WidgetPreviewCard(
                            title: "Quick Cleanup",
                            description: "One tap to start cleanup",
                            size: "Small",
                            preview: QuickCleanupWidgetPreview()
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Widgets")
        }
    }
}

// MARK: - Instructions Card
struct InstructionsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "apps.iphone")
                    .font(.title2)
                    .foregroundColor(.blue)
                Text("How to Add Widgets")
                    .font(.headline)
            }

            VStack(alignment: .leading, spacing: 8) {
                InstructionStep(number: 1, text: "Long press on your Home Screen")
                InstructionStep(number: 2, text: "Tap the + button in the top corner")
                InstructionStep(number: 3, text: "Search for \"Cleanup\"")
                InstructionStep(number: 4, text: "Choose a widget and tap \"Add Widget\"")
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
    }
}

struct InstructionStep: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Text("\(number)")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(width: 24, height: 24)
                .background(Color.blue)
                .clipShape(Circle())

            Text(text)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Widget Preview Card
struct WidgetPreviewCard<Content: View>: View {
    let title: String
    let description: String
    let size: String
    let preview: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Text(description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Text(size)
                    .font(.caption2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.blue.opacity(0.1))
                    .foregroundColor(.blue)
                    .cornerRadius(8)
            }

            preview
                .frame(maxWidth: .infinity)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.03), radius: 5, x: 0, y: 2)
    }
}

// MARK: - Widget Previews
struct StorageWidgetPreview: View {
    var body: some View {
        HStack {
            // Small widget preview
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .stroke(Color.gray.opacity(0.2), lineWidth: 8)
                    Circle()
                        .trim(from: 0, to: 0.65)
                        .stroke(Color.blue, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .frame(width: 50, height: 50)

                Text("65% Used")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(width: 100, height: 100)
            .background(Color(.systemGray6))
            .cornerRadius(16)

            // Medium widget preview
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(Color.gray.opacity(0.2), lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: 0.65)
                        .stroke(Color.blue, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Storage")
                        .font(.caption)
                        .fontWeight(.medium)
                    Text("83 GB / 128 GB")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text("45 GB free")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
            }
            .padding()
            .frame(height: 100)
            .background(Color(.systemGray6))
            .cornerRadius(16)
        }
    }
}

struct BatteryWidgetPreview: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "battery.75")
                .font(.system(size: 30))
                .foregroundColor(.green)

            Text("78%")
                .font(.headline)

            Text("Charging")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(width: 100, height: 100)
        .background(Color(.systemGray6))
        .cornerRadius(16)
    }
}

struct QuickCleanupWidgetPreview: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "sparkles")
                .font(.system(size: 24))
                .foregroundColor(.blue)

            Text("SmartSpace")
                .font(.caption2)
                .fontWeight(.medium)
                .multilineTextAlignment(.center)

            Text("Tap to start")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(width: 100, height: 100)
        .background(Color(.systemGray6))
        .cornerRadius(16)
    }
}

#Preview {
    WidgetsView()
}
