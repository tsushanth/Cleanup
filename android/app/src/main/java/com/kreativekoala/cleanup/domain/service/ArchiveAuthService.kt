package com.kreativekoala.cleanup.domain.service

import android.content.Context
import com.kreativekoala.cleanup.data.remote.SigV4Interceptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS ArchiveAuthService.
 *
 * Handles Google Sign-In → AWS Cognito Identity Pool → temporary AWS credentials.
 * Used for authenticating archive API requests.
 */
@Singleton
class ArchiveAuthService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val COGNITO_IDENTITY_POOL_ID = "us-east-1:3f2f14d8-55f1-4998-9f87-e995528d6710"
        private const val REGION = "us-east-1"
        private const val COGNITO_ENDPOINT = "https://cognito-identity.us-east-1.amazonaws.com"

        private const val PREFS_NAME = "archive_auth_prefs"
        private const val KEY_IDENTITY_ID = "identity_id"
        private const val KEY_ACCESS_KEY_ID = "access_key_id"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_GOOGLE_TOKEN = "google_id_token"
        private const val KEY_BACKEND_REGISTERED = "backend_registered"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var identityId: String? = prefs.getString(KEY_IDENTITY_ID, null)
    private var accessKeyId: String? = prefs.getString(KEY_ACCESS_KEY_ID, null)
    private var secretKey: String? = prefs.getString(KEY_SECRET_KEY, null)
    private var sessionToken: String? = prefs.getString(KEY_SESSION_TOKEN, null)
    private var tokenExpiresAt: Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
    private var cachedGoogleToken: String? = prefs.getString(KEY_GOOGLE_TOKEN, null)
    private var isBackendRegistered: Boolean = prefs.getBoolean(KEY_BACKEND_REGISTERED, false)

    private val _isAuthenticated = MutableStateFlow(checkAuthenticated())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isFullySetUp = MutableStateFlow(checkAuthenticated() && isBackendRegistered)
    val isFullySetUp: StateFlow<Boolean> = _isFullySetUp.asStateFlow()

    private fun checkAuthenticated(): Boolean {
        return identityId != null && accessKeyId != null &&
                secretKey != null && sessionToken != null &&
                tokenExpiresAt > System.currentTimeMillis()
    }

    // MARK: - Sign In with Google → Cognito

    suspend fun signIn(googleIdToken: String) = withContext(Dispatchers.IO) {
        // 1. Exchange Google ID token for Cognito identity
        val cognitoIdentityId = getCognitoIdentity(googleIdToken)

        // 2. Get temporary AWS credentials
        val credentials = getCognitoCredentials(cognitoIdentityId, googleIdToken)

        // 3. Store credentials
        identityId = cognitoIdentityId
        accessKeyId = credentials.accessKeyId
        secretKey = credentials.secretKey
        sessionToken = credentials.sessionToken
        tokenExpiresAt = credentials.expiresAtMillis
        cachedGoogleToken = googleIdToken

        persistCredentials()
        _isAuthenticated.value = true

        // 4. Register with backend
        registerWithBackend()
        isBackendRegistered = true
        prefs.edit().putBoolean(KEY_BACKEND_REGISTERED, true).apply()
        _isFullySetUp.value = true
    }

    suspend fun ensureRegistered() = withContext(Dispatchers.IO) {
        if (!checkAuthenticated()) throw ArchiveError.NotAuthenticated
        if (isBackendRegistered) return@withContext
        refreshCredentialsIfNeeded()
        registerWithBackend()
        isBackendRegistered = true
        prefs.edit().putBoolean(KEY_BACKEND_REGISTERED, true).apply()
        _isFullySetUp.value = true
    }

    fun getCredentials(): SigV4Interceptor.AwsCredentials? {
        val ak = accessKeyId ?: return null
        val sk = secretKey ?: return null
        val st = sessionToken ?: return null
        val id = identityId ?: return null
        if (tokenExpiresAt <= System.currentTimeMillis()) return null
        return SigV4Interceptor.AwsCredentials(
            accessKeyId = ak,
            secretKey = sk,
            sessionToken = st,
            identityId = id,
            region = REGION
        )
    }

    suspend fun refreshCredentialsIfNeeded() = withContext(Dispatchers.IO) {
        // Refresh if expiring within 5 minutes
        if (tokenExpiresAt - System.currentTimeMillis() > 5 * 60 * 1000) return@withContext

        val identity = identityId ?: throw ArchiveError.NotAuthenticated
        val credentials = getCognitoCredentials(identity, cachedGoogleToken)

        accessKeyId = credentials.accessKeyId
        secretKey = credentials.secretKey
        sessionToken = credentials.sessionToken
        tokenExpiresAt = credentials.expiresAtMillis

        persistCredentials()
        _isAuthenticated.value = true
    }

    fun signOut() {
        identityId = null
        accessKeyId = null
        secretKey = null
        sessionToken = null
        tokenExpiresAt = 0
        cachedGoogleToken = null
        isBackendRegistered = false

        prefs.edit().clear().apply()
        _isAuthenticated.value = false
        _isFullySetUp.value = false
    }

    // MARK: - Cognito API

    private data class CognitoCredentials(
        val accessKeyId: String,
        val secretKey: String,
        val sessionToken: String,
        val expiresAtMillis: Long
    )

    private fun getCognitoIdentity(googleToken: String): String {
        val url = URL(COGNITO_ENDPOINT)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-amz-json-1.1")
        conn.setRequestProperty("X-Amz-Target", "AWSCognitoIdentityService.GetId")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("IdentityPoolId", COGNITO_IDENTITY_POOL_ID)
            put("Logins", JSONObject().apply {
                put("accounts.google.com", googleToken)
            })
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw ArchiveError.ServerError(responseCode)
        }

        val responseBody = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseBody)
        return json.getString("IdentityId")
    }

    private fun getCognitoCredentials(identityId: String, googleToken: String?): CognitoCredentials {
        val url = URL(COGNITO_ENDPOINT)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-amz-json-1.1")
        conn.setRequestProperty("X-Amz-Target", "AWSCognitoIdentityService.GetCredentialsForIdentity")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("IdentityId", identityId)
            if (googleToken != null) {
                put("Logins", JSONObject().apply {
                    put("accounts.google.com", googleToken)
                })
            }
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw ArchiveError.ServerError(responseCode)
        }

        val responseBody = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseBody)
        val credentials = json.getJSONObject("Credentials")

        return CognitoCredentials(
            accessKeyId = credentials.getString("AccessKeyId"),
            secretKey = credentials.getString("SecretKey"),
            sessionToken = credentials.getString("SessionToken"),
            expiresAtMillis = (credentials.getDouble("Expiration") * 1000).toLong()
        )
    }

    private fun registerWithBackend() {
        val creds = getCredentials() ?: throw ArchiveError.NotAuthenticated
        val identity = identityId ?: throw ArchiveError.NotAuthenticated

        val apiUrl = URL("https://875x1tpmtb.execute-api.us-east-1.amazonaws.com/v1/auth/register")
        val conn = apiUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Identity-Id", identity)
        conn.doOutput = true

        val body = JSONObject().apply {
            put("source", "android")
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        // Note: This is unsigned for the register call. For full SigV4 signing,
        // use the Retrofit client with SigV4Interceptor for all other API calls.
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
            throw ArchiveError.UploadFailed("Register HTTP $responseCode: $errorBody")
        }
    }

    private fun persistCredentials() {
        prefs.edit()
            .putString(KEY_IDENTITY_ID, identityId)
            .putString(KEY_ACCESS_KEY_ID, accessKeyId)
            .putString(KEY_SECRET_KEY, secretKey)
            .putString(KEY_SESSION_TOKEN, sessionToken)
            .putLong(KEY_TOKEN_EXPIRY, tokenExpiresAt)
            .putString(KEY_GOOGLE_TOKEN, cachedGoogleToken)
            .apply()
    }

    sealed class ArchiveError : Exception() {
        data object NotAuthenticated : ArchiveError()
        data class ServerError(val statusCode: Int) : ArchiveError()
        data class UploadFailed(override val message: String) : ArchiveError()
        data object QuotaExceeded : ArchiveError()
        data object InvalidResponse : ArchiveError()
    }
}
