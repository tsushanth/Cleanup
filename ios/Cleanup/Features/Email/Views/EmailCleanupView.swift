import SwiftUI
import MessageUI

struct EmailCleanupView: View {
    @StateObject private var viewModel = EmailCleanupViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Info Card
                    InfoCard(
                        icon: "envelope.fill",
                        title: "Email Cleanup",
                        subtitle: "Clean up promotional, spam, and unread emails directly in the Mail app",
                        color: .blue
                    )

                    // Categories
                    VStack(spacing: 12) {
                        ForEach(viewModel.categories) { category in
                            EmailCategoryCard(category: category, viewModel: viewModel)
                        }
                    }

                    // Quick Clean Button
                    Button(action: {
                        viewModel.openMailApp()
                    }) {
                        HStack {
                            Image(systemName: "envelope.open.fill")
                            Text("Open Mail App")
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }

                    // Tips Section
                    TipsSection()
                }
                .padding()
            }
            .navigationTitle("Email Cleanup")
        }
    }
}

struct InfoCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let color: Color

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title)
                .foregroundColor(.white)
                .frame(width: 50, height: 50)
                .background(color)
                .cornerRadius(12)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
    }
}

struct EmailCategoryCard: View {
    let category: EmailCategory
    @ObservedObject var viewModel: EmailCleanupViewModel

    var color: Color {
        switch category.type {
        case .promotional: return .orange
        case .spam: return .red
        case .unread: return .blue
        case .newsletters: return .purple
        case .social: return .green
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: category.type.icon)
                .font(.title2)
                .foregroundColor(.white)
                .frame(width: 44, height: 44)
                .background(color)
                .cornerRadius(10)

            VStack(alignment: .leading, spacing: 2) {
                Text(category.type.rawValue)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text("Tap to view tips for cleaning")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.03), radius: 5, x: 0, y: 2)
    }
}

struct TipsSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Tips for Email Cleanup")
                .font(.headline)

            VStack(alignment: .leading, spacing: 16) {
                TipRow(
                    number: "1",
                    text: "Use Mail app's \"Edit\" and \"Select All\" to bulk delete emails"
                )
                TipRow(
                    number: "2",
                    text: "Unsubscribe from newsletters you no longer read"
                )
                TipRow(
                    number: "3",
                    text: "Set up email filters to auto-delete promotional emails"
                )
                TipRow(
                    number: "4",
                    text: "Empty your Trash folder regularly to free up storage"
                )
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
    }
}

struct TipRow: View {
    let number: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
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

#Preview {
    EmailCleanupView()
}
