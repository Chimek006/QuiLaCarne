package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginData>>

    @POST("api/auth/refresh")
    fun refresh(
        @Body request: RefreshRequest
    ): Call<ApiResponse<TokenResponse>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @GET("api/auth/csrf")
    suspend fun csrf(): Response<ApiResponse<String>>
}
