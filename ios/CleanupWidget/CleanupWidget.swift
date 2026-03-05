import WidgetKit
import SwiftUI

// MARK: - Storage Widget
struct StorageEntry: TimelineEntry {
    let date: Date
    let usedPercentage: Double
    let usedSpace: String
    let totalSpace: String
    let freeSpace: String

    static var placeholder: StorageEntry {
        StorageEntry(
            date: Date(),
            usedPercentage: 0.65,
            usedSpace: "83 GB",
            totalSpace: "128 GB",
            freeSpace: "45 GB"
        )
    }
}

struct StorageProvider: TimelineProvider {
    func placeholder(in context: Context) -> StorageEntry {
        StorageEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (StorageEntry) -> Void) {
        let entry = getStorageEntry()
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StorageEntry>) -> Void) {
        let entry = getStorageEntry()
        let nextUpdate = Calendar.current.date(byAdding: .hour, value: 1, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func getStorageEntry() -> StorageEntry {
        guard let attributes = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()),
              let totalSpace = attributes[.systemSize] as? Int64,
              let freeSpace = attributes[.systemFreeSize] as? Int64 else {
            return StorageEntry.placeholder
        }

        let usedSpace = totalSpace - freeSpace
        let usedPercentage = Double(usedSpace) / Double(totalSpace)

        return StorageEntry(
            date: Date(),
            usedPercentage: usedPercentage,
            usedSpace: ByteCountFormatter.string(fromByteCount: usedSpace, countStyle: .file),
            totalSpace: ByteCountFormatter.string(fromByteCount: totalSpace, countStyle: .file),
            freeSpace: ByteCountFormatter.string(fromByteCount: freeSpace, countStyle: .file)
        )
    }
}

struct StorageWidgetEntryView: View {
    var entry: StorageProvider.Entry
    @Environment(\.widgetFamily) var family

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                SmallStorageWidget(entry: entry)
            case .systemMedium:
                MediumStorageWidget(entry: entry)
            default:
                SmallStorageWidget(entry: entry)
            }
        }
        .widgetBackground(Color(.systemBackground))
    }
}

struct SmallStorageWidget: View {
    let entry: StorageEntry

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .stroke(Color.gray.opacity(0.2), lineWidth: 10)

                Circle()
                    .trim(from: 0, to: entry.usedPercentage)
                    .stroke(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))

                VStack(spacing: 2) {
                    Text("\(Int(entry.usedPercentage * 100))%")
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("Used")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding(8)

            Text("\(entry.freeSpace) free")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(8)
    }
}

struct MediumStorageWidget: View {
    let entry: StorageEntry

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(Color.gray.opacity(0.2), lineWidth: 8)

                Circle()
                    .trim(from: 0, to: entry.usedPercentage)
                    .stroke(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(lineWidth: 8, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))

                Text("\(Int(entry.usedPercentage * 100))%")
                    .font(.headline)
                    .fontWeight(.bold)
            }
            .frame(width: 60, height: 60)

            VStack(alignment: .leading, spacing: 4) {
                Text("Storage")
                    .font(.headline)

                Text("\(entry.usedSpace) / \(entry.totalSpace)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                HStack {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 8, height: 8)
                    Text("\(entry.freeSpace) free")
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }

            Spacer()

            VStack {
                Image(systemName: "sparkles")
                    .font(.title2)
                    .foregroundColor(.blue)
                Text("Clean")
                    .font(.caption2)
            }
            .padding(8)
            .background(Color.blue.opacity(0.1))
            .cornerRadius(12)
        }
        .padding()
    }
}

struct StorageWidget: Widget {
    let kind: String = "StorageWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: StorageProvider()) { entry in
            StorageWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Storage")
        .description("Monitor your device storage at a glance")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Battery Widget
struct BatteryEntry: TimelineEntry {
    let date: Date
    let level: Float
    let isCharging: Bool

    static var placeholder: BatteryEntry {
        BatteryEntry(date: Date(), level: 0.78, isCharging: true)
    }
}

struct BatteryProvider: TimelineProvider {
    func placeholder(in context: Context) -> BatteryEntry {
        BatteryEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (BatteryEntry) -> Void) {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let entry = BatteryEntry(
            date: Date(),
            level: UIDevice.current.batteryLevel,
            isCharging: UIDevice.current.batteryState == .charging
        )
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BatteryEntry>) -> Void) {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let entry = BatteryEntry(
            date: Date(),
            level: UIDevice.current.batteryLevel,
            isCharging: UIDevice.current.batteryState == .charging
        )
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

struct BatteryWidgetEntryView: View {
    var entry: BatteryProvider.Entry

    var batteryColor: Color {
        if entry.level < 0.2 {
            return .red
        } else if entry.level < 0.5 {
            return .orange
        } else {
            return .green
        }
    }

    var batteryIcon: String {
        let level = Int(entry.level * 100)
        switch level {
        case 0..<25: return "battery.25"
        case 25..<50: return "battery.50"
        case 50..<75: return "battery.75"
        default: return "battery.100"
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Image(systemName: entry.isCharging ? "battery.100.bolt" : batteryIcon)
                    .font(.system(size: 40))
                    .foregroundColor(batteryColor)
            }

            Text("\(Int(entry.level * 100))%")
                .font(.title2)
                .fontWeight(.bold)

            Text(entry.isCharging ? "Charging" : "Battery")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .widgetBackground(Color(.systemBackground))
    }
}

struct BatteryWidget: Widget {
    let kind: String = "BatteryWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: BatteryProvider()) { entry in
            BatteryWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Battery")
        .description("Monitor your battery level")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Quick Cleanup Widget
struct QuickCleanupEntry: TimelineEntry {
    let date: Date

    static var placeholder: QuickCleanupEntry {
        QuickCleanupEntry(date: Date())
    }
}

struct QuickCleanupProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickCleanupEntry {
        QuickCleanupEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickCleanupEntry) -> Void) {
        completion(QuickCleanupEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickCleanupEntry>) -> Void) {
        let entry = QuickCleanupEntry(date: Date())
        let timeline = Timeline(entries: [entry], policy: .never)
        completion(timeline)
    }
}

struct QuickCleanupWidgetEntryView: View {
    var entry: QuickCleanupProvider.Entry

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "sparkles")
                .font(.system(size: 30))
                .foregroundStyle(
                    LinearGradient(
                        colors: [.blue, .purple],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Text("Cleanup")
                .font(.headline)

            Text("Tap to start")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .widgetBackground(Color(.systemBackground))
        .widgetURL(URL(string: "cleanup://quick-cleanup"))
    }
}

struct QuickCleanupWidget: Widget {
    let kind: String = "QuickCleanupWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: QuickCleanupProvider()) { entry in
            QuickCleanupWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Quick Cleanup")
        .description("One tap to start cleanup")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Widget Background Extension (iOS 16 Compatible)
extension View {
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

// MARK: - Widget Bundle
@main
struct CleanupWidgetBundle: WidgetBundle {
    var body: some Widget {
        StorageWidget()
        BatteryWidget()
        QuickCleanupWidget()
    }
}
