package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.LoginRequest
import com.example.quilacarne.data.remote.models.LoginResponse
import com.example.quilacarne.data.remote.models.RefreshRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/auth/refresh")
    fun refresh(
        @Body request: RefreshRequest
    ): Call<LoginResponse>
}