import Foundation

actor ArchiveAPIClient {
    static let shared = ArchiveAPIClient()
    static let baseURL = "https://875x1tpmtb.execute-api.us-east-1.amazonaws.com/v1"

    private let session = URLSession.shared
    private let authService = ArchiveAuthService.shared
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private init() {}

    // MARK: - Upload Flow

    func requestUploadURL(
        itemId: String,
        filename: String,
        fileSize: Int64,
        mimeType: String,
        tier: ArchiveTier
    ) async throws -> PresignedUploadResponse {
        let body: [String: Any] = [
            "itemId": itemId,
            "filename": filename,
            "fileSize": fileSize,
            "mimeType": mimeType,
            "storageTier": tier.rawValue
        ]
        return try await post("/upload/presign", body: body)
    }

    func confirmUpload(
        itemId: String,
        fileName: String,
        fileType: ArchivedFileType,
        fileSize: Int64,
        storageTier: ArchiveTier,
        metadata: ArchivedItemMetadata,
        thumbnailData: Data?
    ) async throws {
        var body: [String: Any] = [
            "itemId": itemId,
            "fileName": fileName,
            "fileType": fileType.rawValue,
            "fileSize": fileSize,
            "storageTier": storageTier.rawValue
        ]
        if let thumb = thumbnailData {
            body["thumbnailBase64"] = thumb.base64EncodedString()
        }
        if let created = metadata.creationDate {
            body["creationDate"] = ISO8601DateFormatter().string(from: created)
        }
        if let w = metadata.pixelWidth { body["pixelWidth"] = w }
        if let h = metadata.pixelHeight { body["pixelHeight"] = h }
        if let d = metadata.duration { body["duration"] = d }
        if let names = metadata.contactNames { body["contactNames"] = names }
        if let count = metadata.contactCount { body["contactCount"] = count }

        let _: EmptyResponse = try await post("/upload/complete", body: body)
    }

    // MARK: - Items

    func listItems(cursor: String? = nil, limit: Int = 50) async throws -> PaginatedResponse<ArchivedItem> {
        var params = "limit=\(limit)"
        if let cursor = cursor { params += "&cursor=\(cursor)" }
        return try await get("/items?\(params)")
    }

    func getItem(itemId: String) async throws -> ArchivedItem {
        return try await get("/items/\(itemId)")
    }

    func deleteItem(itemId: String) async throws {
        try await deleteRequest("/items/\(itemId)")
    }

    // MARK: - Download/Retrieval

    func initiateDownload(itemId: String) async throws -> DownloadInitiationResponse {
        return try await post("/download/initiate", body: ["itemId": itemId])
    }

    func checkDownloadStatus(itemId: String) async throws -> DownloadStatusResponse {
        return try await get("/download/status/\(itemId)")
    }

    // MARK: - Quota

    func getQuota() async throws -> ArchiveQuota {
        return try await get("/quota")
    }

    func validateReceipt(_ receiptData: String) async throws -> QuotaValidationResponse {
        return try await post("/quota/validate-receipt", body: ["receipt": receiptData])
    }

    // MARK: - HTTP Helpers

    private func signedRequest(url: URL, method: String = "GET", body: Data? = nil, contentType: String? = nil) async throws -> URLRequest {
        let creds = try await authService.getCredentials()
        let identityId = await authService.identityId
        var request = URLRequest(url: url)
        request.httpMethod = method
        if let ct = contentType {
            request.setValue(ct, forHTTPHeaderField: "Content-Type")
        }
        if let identity = identityId {
            request.setValue(identity, forHTTPHeaderField: "X-Identity-Id")
        }
        if let body = body {
            request.httpBody = body
        }
        return SigV4Signer.sign(request: request, credentials: creds, service: "execute-api")
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        let url = URL(string: Self.baseURL + path)!
        let request = try await signedRequest(url: url, contentType: "application/json")

        let (data, response) = try await session.data(for: request)
        try validateHTTPResponse(response)
        return try decoder.decode(T.self, from: data)
    }

    private func post<T: Decodable>(_ path: String, body: [String: Any]) async throws -> T {
        let url = URL(string: Self.baseURL + path)!
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = try await signedRequest(url: url, method: "POST", body: bodyData, contentType: "application/json")

        let (data, response) = try await session.data(for: request)
        try validateHTTPResponse(response)
        return try decoder.decode(T.self, from: data)
    }

    private func deleteRequest(_ path: String) async throws {
        let url = URL(string: Self.baseURL + path)!
        let request = try await signedRequest(url: url, method: "DELETE")

        let (_, response) = try await session.data(for: request)
        try validateHTTPResponse(response)
    }

    private func validateHTTPResponse(_ response: URLResponse) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ArchiveError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            switch httpResponse.statusCode {
            case 401: throw ArchiveError.notAuthenticated
            case 403: throw ArchiveError.forbidden
            case 413: throw ArchiveError.quotaExceeded
            case 429: throw ArchiveError.rateLimited
            default: throw ArchiveError.serverError(httpResponse.statusCode)
            }
        }
    }
}

private struct EmptyResponse: Codable {}
