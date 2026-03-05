import SwiftUI

struct FreeUsageBanner: View {
    let remaining: Int
    let category: FreeUsageManager.Category

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: remaining > 0 ? "gift.fill" : "lock.fill")
                .foregroundColor(remaining > 0 ? .blue : .orange)

            if remaining > 0 {
                Text("\(remaining) free deletions remaining")
                    .font(.caption)
                    .fontWeight(.medium)
            } else {
                Text("Free deletions used up")
                    .font(.caption)
                    .fontWeight(.medium)

                Spacer()

                Text("Upgrade")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(
                        LinearGradient(
                            colors: [.blue, .purple],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(12)
                    .onTapGesture {
                        PaywallCoordinator.shared.showPaywall(context: .generic)
                    }
            }

            if remaining > 0 {
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(remaining > 0 ? Color.blue.opacity(0.08) : Color.orange.opacity(0.08))
    }
}
