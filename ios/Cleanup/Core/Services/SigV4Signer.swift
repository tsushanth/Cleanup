import Foundation
import CommonCrypto

/// Lightweight AWS Signature Version 4 request signer for API Gateway.
enum SigV4Signer {

    static func sign(
        request: URLRequest,
        credentials: ArchiveAuthService.AWSCredentials,
        service: String
    ) -> URLRequest {
        var req = request
        let url = req.url!
        let host = url.host!
        let method = req.httpMethod ?? "GET"
        let body = req.httpBody ?? Data()

        // Timestamp
        let now = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        dateFormatter.timeZone = TimeZone(identifier: "UTC")
        dateFormatter.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        let amzDate = dateFormatter.string(from: now)

        dateFormatter.dateFormat = "yyyyMMdd"
        let dateStamp = dateFormatter.string(from: now)

        // Set required headers
        req.setValue(host, forHTTPHeaderField: "Host")
        req.setValue(amzDate, forHTTPHeaderField: "X-Amz-Date")
        req.setValue(credentials.sessionToken, forHTTPHeaderField: "X-Amz-Security-Token")

        // Canonical URI (path)
        let canonicalURI = url.path.isEmpty ? "/" : url.path

        // Canonical query string
        let canonicalQueryString = url.query ?? ""

        // Canonical headers — must be sorted lowercase
        let payloadHash = sha256Hex(body)

        var headersToSign: [(String, String)] = [
            ("host", host),
            ("x-amz-date", amzDate),
            ("x-amz-security-token", credentials.sessionToken),
        ]

        // Include content-type if present
        if let contentType = req.value(forHTTPHeaderField: "Content-Type") {
            headersToSign.append(("content-type", contentType))
        }

        // Include x-identity-id if present
        if let identityId = req.value(forHTTPHeaderField: "X-Identity-Id") {
            headersToSign.append(("x-identity-id", identityId))
        }

        headersToSign.sort { $0.0 < $1.0 }

        let canonicalHeaders = headersToSign.map { "\($0.0):\($0.1)\n" }.joined()
        let signedHeaders = headersToSign.map { $0.0 }.joined(separator: ";")

        // Canonical request
        let canonicalRequest = [
            method,
            canonicalURI,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ].joined(separator: "\n")

        // Credential scope
        let credentialScope = "\(dateStamp)/\(credentials.region)/\(service)/aws4_request"

        // String to sign
        let stringToSign = [
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.data(using: .utf8)!)
        ].joined(separator: "\n")

        // Signing key
        let kDate = hmacSHA256(key: "AWS4\(credentials.secretKey)".data(using: .utf8)!, data: dateStamp.data(using: .utf8)!)
        let kRegion = hmacSHA256(key: kDate, data: credentials.region.data(using: .utf8)!)
        let kService = hmacSHA256(key: kRegion, data: service.data(using: .utf8)!)
        let kSigning = hmacSHA256(key: kService, data: "aws4_request".data(using: .utf8)!)

        // Signature
        let signature = hmacSHA256(key: kSigning, data: stringToSign.data(using: .utf8)!).hexString

        // Authorization header
        let authorization = "AWS4-HMAC-SHA256 Credential=\(credentials.accessKeyId)/\(credentialScope), SignedHeaders=\(signedHeaders), Signature=\(signature)"

        req.setValue(authorization, forHTTPHeaderField: "Authorization")

        return req
    }

    // MARK: - Crypto Helpers

    private static func sha256Hex(_ data: Data) -> String {
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { buffer in
            _ = CC_SHA256(buffer.baseAddress, CC_LONG(data.count), &hash)
        }
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    private static func hmacSHA256(key: Data, data: Data) -> Data {
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        key.withUnsafeBytes { keyBuffer in
            data.withUnsafeBytes { dataBuffer in
                CCHmac(
                    CCHmacAlgorithm(kCCHmacAlgSHA256),
                    keyBuffer.baseAddress, key.count,
                    dataBuffer.baseAddress, data.count,
                    &hash
                )
            }
        }
        return Data(hash)
    }
}

private extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
