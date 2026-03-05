package com.kreativekoala.cleanup.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Port of iOS SigV4Signer.
 *
 * OkHttp interceptor that signs requests with AWS Signature Version 4
 * for authenticated API Gateway access.
 */
class SigV4Interceptor(
    private val credentialsProvider: () -> AwsCredentials?
) : Interceptor {

    data class AwsCredentials(
        val accessKeyId: String,
        val secretKey: String,
        val sessionToken: String,
        val identityId: String,
        val region: String = "us-east-1"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val credentials = credentialsProvider() ?: return chain.proceed(original)

        val signed = signRequest(original, credentials)
        return chain.proceed(signed)
    }

    private fun signRequest(request: Request, creds: AwsCredentials): Request {
        val url = request.url
        val host = url.host
        val method = request.method
        val body = request.body?.let { body ->
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } ?: ByteArray(0)

        val now = Date()
        val amzDate = amzDateFormat.get()!!.format(now)
        val dateStamp = dateStampFormat.get()!!.format(now)

        // Build headers to sign
        val headersToSign = mutableListOf(
            "host" to host,
            "x-amz-date" to amzDate,
            "x-amz-security-token" to creds.sessionToken
        )

        val contentType = request.header("Content-Type")
        if (contentType != null) {
            headersToSign.add("content-type" to contentType)
        }

        // Always include identity ID
        headersToSign.add("x-identity-id" to creds.identityId)

        headersToSign.sortBy { it.first }

        val canonicalHeaders = headersToSign.joinToString("") { "${it.first}:${it.second}\n" }
        val signedHeaders = headersToSign.joinToString(";") { it.first }

        // Canonical URI
        val canonicalUri = url.encodedPath.ifEmpty { "/" }

        // Canonical query string
        val canonicalQueryString = url.query ?: ""

        // Payload hash
        val payloadHash = sha256Hex(body)

        // Canonical request
        val canonicalRequest = listOf(
            method,
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        // Credential scope
        val service = "execute-api"
        val credentialScope = "$dateStamp/${creds.region}/$service/aws4_request"

        // String to sign
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        // Signing key
        val kDate = hmacSha256("AWS4${creds.secretKey}".toByteArray(Charsets.UTF_8), dateStamp.toByteArray(Charsets.UTF_8))
        val kRegion = hmacSha256(kDate, creds.region.toByteArray(Charsets.UTF_8))
        val kService = hmacSha256(kRegion, service.toByteArray(Charsets.UTF_8))
        val kSigning = hmacSha256(kService, "aws4_request".toByteArray(Charsets.UTF_8))

        // Signature
        val signature = hmacSha256(kSigning, stringToSign.toByteArray(Charsets.UTF_8)).toHex()

        // Authorization header
        val authorization = "AWS4-HMAC-SHA256 Credential=${creds.accessKeyId}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return request.newBuilder()
            .header("Host", host)
            .header("X-Amz-Date", amzDate)
            .header("X-Amz-Security-Token", creds.sessionToken)
            .header("X-Identity-Id", creds.identityId)
            .header("Authorization", authorization)
            .build()
    }

    // MARK: - Crypto Helpers

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHex()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    companion object {
        private val amzDateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        private val dateStampFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
