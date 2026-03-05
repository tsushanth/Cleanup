package com.kreativekoala.cleanup.data.remote

import com.kreativekoala.cleanup.data.model.ArchiveQuota
import com.kreativekoala.cleanup.data.model.DownloadInitiationResponse
import com.kreativekoala.cleanup.data.model.DownloadStatusResponse
import com.kreativekoala.cleanup.data.model.PresignedUploadResponse
import com.kreativekoala.cleanup.data.model.QuotaResponse
import retrofit2.http.*

/**
 * Port of iOS ArchiveAPIClient.
 *
 * Retrofit interface for the 7 archive backend endpoints.
 * Requests are signed by SigV4Interceptor in the OkHttp client.
 */
interface ArchiveApiService {

    companion object {
        const val BASE_URL = "https://875x1tpmtb.execute-api.us-east-1.amazonaws.com/v1/"
    }

    // MARK: - Auth

    @POST("auth/register")
    suspend fun register(@Body body: Map<String, String>): Map<String, Any>

    // MARK: - Upload Flow

    @POST("upload/presign")
    suspend fun requestUploadUrl(@Body body: Map<String, Any>): PresignedUploadResponse

    @POST("upload/complete")
    suspend fun confirmUpload(@Body body: Map<String, Any>): Map<String, Any>

    // MARK: - Items

    @GET("items")
    suspend fun listItems(
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null
    ): PaginatedItemsResponse

    @DELETE("items/{itemId}")
    suspend fun deleteItem(@Path("itemId") itemId: String)

    // MARK: - Download/Retrieval

    @POST("download/initiate")
    suspend fun initiateDownload(@Body body: Map<String, String>): DownloadInitiationResponse

    @GET("download/status/{itemId}")
    suspend fun checkDownloadStatus(@Path("itemId") itemId: String): DownloadStatusResponse

    // MARK: - Quota

    @GET("quota")
    suspend fun getQuota(): QuotaResponse
}

data class PaginatedItemsResponse(
    val items: List<RemoteArchivedItem>,
    val cursor: String? = null
)

data class RemoteArchivedItem(
    val id: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val storageTier: String,
    val archivedDate: String,
    val transferStatus: String? = null,
    val creationDate: String? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val duration: Double? = null
)
