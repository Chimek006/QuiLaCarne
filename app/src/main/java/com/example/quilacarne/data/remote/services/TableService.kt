package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.DictionaryResponse
import com.example.quilacarne.data.remote.models.TablesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TableService {

    @GET("api/tables")
    suspend fun getTables(
        @Query("request.startTime") startTime: String,
        @Query("request.endTime") endTime: String
    ): Response<TablesResponse>

    @GET("api/tables/dictionary")
    suspend fun getStatusDictionary(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<DictionaryResponse>
}