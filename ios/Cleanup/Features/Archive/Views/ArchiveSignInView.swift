import SwiftUI
import AuthenticationServices

struct ArchiveSignInView: View {
    @Environment(\.dismiss) private var dismiss
    let onSignIn: () -> Void

    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                // Icon
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [.cyan.opacity(0.2), .blue.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 100, height: 100)

                    Image(systemName: "icloud.and.arrow.up")
                        .font(.system(size: 40))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [.cyan, .blue],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                }

                // Text
                VStack(spacing: 12) {
                    Text("Sign In for Cloud Archive")
                        .font(.title2)
                        .fontWeight(.bold)

                    Text("Sign in with your Apple ID to securely archive files in the cloud. Your data is encrypted and only you can access it.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                // Features
                VStack(alignment: .leading, spacing: 12) {
                    SignInFeatureRow(icon: "lock.shield.fill", text: "End-to-end encrypted storage")
                    SignInFeatureRow(icon: "arrow.triangle.2.circlepath", text: "Retrieve files anytime")
                    SignInFeatureRow(icon: "checkmark.shield.fill", text: "No passwords to remember")
                }
                .padding(.horizontal, 40)

                Spacer()

                // Sign In Button
                if isLoading {
                    ProgressView("Signing in...")
                } else {
                    SignInWithAppleButton(.signIn) { request in
                        request.requestedScopes = [.email]
                    } onCompletion: { result in
                        handleSignInResult(result)
                    }
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 50)
                    .cornerRadius(12)
                    .padding(.horizontal)
                }

                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }

                Text("We only use your Apple ID for authentication. No personal data is collected.")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .padding(.bottom, 16)
            }
            .navigationTitle("Cloud Archive")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func handleSignInResult(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let auth):
            guard let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let token = String(data: tokenData, encoding: .utf8) else {
                errorMessage = "Could not get Apple ID token"
                return
            }

            isLoading = true
            Task {
                do {
                    try await ArchiveAuthService.shared.signIn(appleIdToken: token)
                    await MainActor.run {
                        isLoading = false
                        onSignIn()
                        dismiss()
                    }
                } catch let archiveError as ArchiveError {
                    await MainActor.run {
                        isLoading = false
                        errorMessage = archiveError.errorDescription
                    }
                } catch {
                    await MainActor.run {
                        isLoading = false
                        errorMessage = "Sign-in failed: \(error.localizedDescription)"
                    }
                }
            }

        case .failure(let error):
            if (error as NSError).code != ASAuthorizationError.canceled.rawValue {
                errorMessage = error.localizedDescription
            }
        }
    }
}

struct SignInFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.cyan)
                .frame(width: 24)
            Text(text)
                .font(.subheadline)
                .foregroundColor(.primary)
        }
    }
}
