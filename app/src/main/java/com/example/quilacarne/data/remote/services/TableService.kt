package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TableService {

    @GET("api/tables")
    suspend fun getTables(
        @Query("request.startTime") startTime: String,
        @Query("request.endTime") endTime: String
    ): Response<ApiResponse<TablesData>>

    @GET("api/tables/dictionary")
    suspend fun getStatusDictionary(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<List<StatusDictionaryDto>>>

    @GET("api/sync/tables")
    suspend fun syncTables(
        @Query("page") page: Int = 1
    ): Response<TableSyncResponse>
}