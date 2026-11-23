package com.kreativekoala.cleanup.ui.archive

import androidx.annotation.StringRes
import com.kreativekoala.cleanup.R
import com.kreativekoala.cleanup.domain.service.ArchiveAuthService.ArchiveError
import retrofit2.HttpException
import java.io.IOException

/**
 * Maps archive backend failures to string resources so users see them in their own language.
 * The Lambdas return `{ "error": "<English text>", "code": "<STABLE_CODE>" }`; the English text is
 * for logs only and is never shown. Keep [CODES] in step with backend/lambdas (a backend test
 * checks that every code a Lambda can return is listed here).
 */
object ArchiveErrorMapper {

    val CODES: Map<String, Int> = mapOf(
        "MISSING_IDENTITY" to R.string.archive_error_not_authenticated,
        "MISSING_ITEM_ID" to R.string.archive_error_invalid_request,
        "MISSING_FIELDS" to R.string.archive_error_invalid_request,
        "ITEM_NOT_FOUND" to R.string.archive_error_item_not_found,
        "USER_NOT_FOUND" to R.string.archive_error_user_not_found,
        "QUOTA_EXCEEDED" to R.string.archive_error_quota_exceeded,
        "INTERNAL_ERROR" to R.string.archive_error_server
    )

    private val codeRegex = Regex("\"code\"\\s*:\\s*\"([A-Z_]+)\"")

    fun parseCode(body: String?): String? = body?.let { codeRegex.find(it)?.groupValues?.get(1) }

    /** Known code first, then the HTTP status as a fallback for older or non-Lambda responses. */
    @StringRes
    fun resFor(httpStatus: Int, body: String?): Int {
        parseCode(body)?.let { CODES[it]?.let { res -> return res } }
        return when {
            httpStatus == 400 -> R.string.archive_error_invalid_request
            httpStatus == 401 || httpStatus == 403 -> R.string.archive_error_not_authenticated
            httpStatus == 404 -> R.string.archive_error_item_not_found
            httpStatus == 413 -> R.string.archive_error_quota_exceeded
            httpStatus >= 500 -> R.string.archive_error_server
            else -> R.string.archive_error_generic
        }
    }

    @StringRes
    fun resFor(t: Throwable): Int = when (t) {
        is HttpException -> resFor(t.code(), readBody(t))
        is ArchiveError.NotAuthenticated -> R.string.archive_error_not_authenticated
        is ArchiveError.QuotaExceeded -> R.string.archive_error_quota_exceeded
        is ArchiveError.ServerError -> resFor(t.statusCode, null)
        is ArchiveError.UploadFailed -> R.string.archive_error_upload_failed
        is ArchiveError.InvalidResponse -> R.string.archive_error_server
        is IOException -> R.string.archive_error_network
        else -> R.string.archive_error_generic
    }

    private fun readBody(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
}
