package com.example.quilacarne.data.remote.api

import com.example.quilacarne.data.remote.dto.request.LoginRequest
import com.example.quilacarne.data.remote.dto.request.RefreshRequest
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.remote.dto.response.LoginData
import com.example.quilacarne.data.remote.dto.response.TokenResponse
import com.example.quilacarne.data.remote.dto.response.UserProfileData
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
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

    @GET("auth/me")
    suspend fun me(
        @Header("Authorization") authorization: String
    ): Response<ApiResponse<UserProfileData>>

    @GET("auth/csrf")
    suspend fun csrf(): Response<ApiResponse<String>>
}
