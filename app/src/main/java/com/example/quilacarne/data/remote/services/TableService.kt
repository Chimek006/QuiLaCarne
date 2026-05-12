package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface TableService {

    @GET("tables")
    suspend fun getTables(
        @Query("request.startTime") startTime: String,
        @Query("request.endTime") endTime: String
    ): Response<ApiResponse<TablesData>>

    @GET("tables/dictionary")
    suspend fun getStatusDictionary(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<DictionaryData<StatusDictionaryDto>>>

    @GET("sync/tables")
    suspend fun syncTables(
        @Query("page") page: Int = 1
    ): Response<TableSyncResponse>

    @PATCH("tables/{token}/clear")
    suspend fun markTableCleaning(
        @Path("token") token: String
    ): Response<ApiResponse<Unit>>

    @PATCH("tables/{token}/out-of-services")
    suspend fun markTableOutOfService(
        @Path("token") token: String
    ): Response<ApiResponse<Unit>>
}
