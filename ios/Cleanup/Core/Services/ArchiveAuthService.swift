import Foundation
import AuthenticationServices

actor ArchiveAuthService {
    static let shared = ArchiveAuthService()

    private let cognitoIdentityPoolId = "us-east-1:3f2f14d8-55f1-4998-9f87-e995528d6710"
    private let region = "us-east-1"

    private let identityIdKey = "archive_cognito_identity_id"
    private let accessKeyIdKey = "archive_access_key_id"
    private let secretKeyKey = "archive_secret_key"
    private let sessionTokenKey = "archive_session_token"
    private let tokenExpiryKey = "archive_token_expiry"
    private let appleTokenKey = "archive_apple_id_token"
    private let registeredKey = "archive_backend_registered"

    private(set) var identityId: String?
    private(set) var accessKeyId: String?
    private(set) var secretKey: String?
    private(set) var sessionToken: String?
    private var tokenExpiresAt: Date?
    private var cachedAppleToken: String?
    private var isBackendRegistered: Bool

    struct AWSCredentials {
        let accessKeyId: String
        let secretKey: String
        let sessionToken: String
        let region: String
    }

    private init() {
        // Restore persisted auth state
        identityId = UserDefaults.standard.string(forKey: identityIdKey)
        accessKeyId = UserDefaults.standard.string(forKey: accessKeyIdKey)
        secretKey = UserDefaults.standard.string(forKey: secretKeyKey)
        sessionToken = UserDefaults.standard.string(forKey: sessionTokenKey)
        cachedAppleToken = UserDefaults.standard.string(forKey: appleTokenKey)
        isBackendRegistered = UserDefaults.standard.bool(forKey: registeredKey)
        if let expiry = UserDefaults.standard.object(forKey: tokenExpiryKey) as? Date {
            tokenExpiresAt = expiry
        }
    }

    var isAuthenticated: Bool {
        identityId != nil && accessKeyId != nil && secretKey != nil && sessionToken != nil &&
        (tokenExpiresAt ?? .distantPast) > Date()
    }

    /// True only when the user is both authenticated AND registered with the backend.
    var isFullySetUp: Bool {
        isAuthenticated && isBackendRegistered
    }

    // MARK: - Sign In with Apple → Cognito

    func signIn(appleIdToken: String) async throws {
        // 1. Exchange Apple ID token for Cognito identity
        let cognitoIdentity = try await getCognitoIdentity(appleToken: appleIdToken)

        // 2. Get temporary AWS credentials
        let credentials = try await getCognitoCredentials(
            identityId: cognitoIdentity.identityId,
            appleToken: appleIdToken
        )

        // 3. Store all credentials
        self.identityId = cognitoIdentity.identityId
        self.accessKeyId = credentials.accessKeyId
        self.secretKey = credentials.secretKey
        self.sessionToken = credentials.sessionToken
        self.tokenExpiresAt = credentials.expiresAt
        self.cachedAppleToken = appleIdToken

        // 4. Persist
        persistCredentials()

        // 5. Register with backend (using SigV4-signed request)
        try await registerWithBackend()
        isBackendRegistered = true
        UserDefaults.standard.set(true, forKey: registeredKey)
    }

    /// Ensures the user is registered with the backend.
    /// Call this before making API calls to handle the case where sign-in partially succeeded.
    func ensureRegistered() async throws {
        guard isAuthenticated else {
            throw ArchiveError.notAuthenticated
        }
        if isBackendRegistered { return }
        try await refreshCredentialsIfNeeded()
        try await registerWithBackend()
        isBackendRegistered = true
        UserDefaults.standard.set(true, forKey: registeredKey)
    }

    func getCredentials() async throws -> AWSCredentials {
        try await refreshCredentialsIfNeeded()
        guard let ak = accessKeyId, let sk = secretKey, let st = sessionToken else {
            throw ArchiveError.notAuthenticated
        }
        return AWSCredentials(accessKeyId: ak, secretKey: sk, sessionToken: st, region: region)
    }

    func refreshCredentialsIfNeeded() async throws {
        guard let expiresAt = tokenExpiresAt else {
            throw ArchiveError.notAuthenticated
        }
        // Refresh if expiring within 5 minutes
        guard expiresAt.timeIntervalSinceNow < 300 else { return }

        guard let identity = identityId else {
            throw ArchiveError.notAuthenticated
        }

        let credentials = try await getCognitoCredentials(
            identityId: identity,
            appleToken: cachedAppleToken
        )
        self.accessKeyId = credentials.accessKeyId
        self.secretKey = credentials.secretKey
        self.sessionToken = credentials.sessionToken
        self.tokenExpiresAt = credentials.expiresAt

        persistCredentials()
    }

    func signOut() {
        identityId = nil
        accessKeyId = nil
        secretKey = nil
        sessionToken = nil
        tokenExpiresAt = nil
        cachedAppleToken = nil
        isBackendRegistered = false
        for key in [identityIdKey, accessKeyIdKey, secretKeyKey, sessionTokenKey, tokenExpiryKey, appleTokenKey, registeredKey] {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    // MARK: - Private

    private func persistCredentials() {
        UserDefaults.standard.set(identityId, forKey: identityIdKey)
        UserDefaults.standard.set(accessKeyId, forKey: accessKeyIdKey)
        UserDefaults.standard.set(secretKey, forKey: secretKeyKey)
        UserDefaults.standard.set(sessionToken, forKey: sessionTokenKey)
        UserDefaults.standard.set(tokenExpiresAt, forKey: tokenExpiryKey)
        UserDefaults.standard.set(cachedAppleToken, forKey: appleTokenKey)
    }

    // MARK: - Cognito API Calls

    private struct CognitoIdentityResponse {
        let identityId: String
    }

    private struct CognitoCredentialsResponse {
        let accessKeyId: String
        let secretKey: String
        let sessionToken: String
        let expiresAt: Date
    }

    private func getCognitoIdentity(appleToken: String) async throws -> CognitoIdentityResponse {
        let url = URL(string: "https://cognito-identity.\(region).amazonaws.com")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-amz-json-1.1", forHTTPHeaderField: "Content-Type")
        request.setValue("AWSCognitoIdentityService.GetId", forHTTPHeaderField: "X-Amz-Target")

        let body: [String: Any] = [
            "IdentityPoolId": cognitoIdentityPoolId,
            "Logins": [
                "appleid.apple.com": appleToken
            ]
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ArchiveError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw ArchiveError.serverError(httpResponse.statusCode)
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let identityId = json["IdentityId"] as? String else {
            throw ArchiveError.invalidResponse
        }

        return CognitoIdentityResponse(identityId: identityId)
    }

    private func getCognitoCredentials(identityId: String, appleToken: String?) async throws -> CognitoCredentialsResponse {
        let url = URL(string: "https://cognito-identity.\(region).amazonaws.com")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-amz-json-1.1", forHTTPHeaderField: "Content-Type")
        request.setValue("AWSCognitoIdentityService.GetCredentialsForIdentity", forHTTPHeaderField: "X-Amz-Target")

        var body: [String: Any] = ["IdentityId": identityId]
        if let token = appleToken {
            body["Logins"] = ["appleid.apple.com": token]
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ArchiveError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw ArchiveError.serverError(httpResponse.statusCode)
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let credentials = json["Credentials"] as? [String: Any],
              let accessKeyId = credentials["AccessKeyId"] as? String,
              let secretKey = credentials["SecretKey"] as? String,
              let sessionToken = credentials["SessionToken"] as? String,
              let expiration = credentials["Expiration"] as? TimeInterval else {
            throw ArchiveError.invalidResponse
        }

        return CognitoCredentialsResponse(
            accessKeyId: accessKeyId,
            secretKey: secretKey,
            sessionToken: sessionToken,
            expiresAt: Date(timeIntervalSince1970: expiration)
        )
    }

    /// Syncs the current archive subscription to the backend so quota is set correctly.
    func syncSubscription() async throws {
        guard isAuthenticated else { return }
        try await refreshCredentialsIfNeeded()
        try await registerWithBackend()
    }

    private func registerWithBackend() async throws {
        let creds = try await getCredentials()
        guard let identity = identityId else {
            throw ArchiveError.notAuthenticated
        }
        guard let url = URL(string: "\(ArchiveAPIClient.baseURL)/auth/register") else {
            throw ArchiveError.invalidResponse
        }

        // Include subscription product ID so backend can set quota
        let subscriptionProductId = await MainActor.run {
            EntitlementManager.shared.currentArchiveTier?.rawValue
        }

        var bodyDict: [String: Any] = ["source": "ios"]
        if let productId = subscriptionProductId {
            bodyDict["subscriptionProductId"] = productId
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(identity, forHTTPHeaderField: "X-Identity-Id")
        request.httpBody = try JSONSerialization.data(withJSONObject: bodyDict)

        let signedRequest = SigV4Signer.sign(
            request: request,
            credentials: creds,
            service: "execute-api"
        )

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: signedRequest)
        } catch {
            throw ArchiveError.uploadFailed("Network error: \(error.localizedDescription)")
        }

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            let body = String(data: data, encoding: .utf8) ?? "No body"
            throw ArchiveError.uploadFailed("Register HTTP \(statusCode): \(body)")
        }
    }
}
