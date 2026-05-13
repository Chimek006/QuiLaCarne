package com.example.quilacarne.data.remote.api

import com.example.quilacarne.data.remote.dto.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthService {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginData>>

    @POST("auth/refresh")
    fun refresh(
        @Body request: RefreshRequest
    ): Call<ApiResponse<TokenResponse>>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @GET("auth/csrf")
    suspend fun csrf(): Response<ApiResponse<String>>
}
