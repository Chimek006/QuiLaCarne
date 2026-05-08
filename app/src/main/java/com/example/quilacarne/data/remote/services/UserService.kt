package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.ApiResponse
import com.example.quilacarne.data.remote.models.ChangePasswordRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Query

interface UserService {
    @PATCH("api/user/me/username")
    suspend fun updateUsername(
        @Query("userName") userName: String
    ): Response<ApiResponse<Unit>>

    @PATCH("api/user/me/password")
    suspend fun updatePassword(
        @Body request: ChangePasswordRequest
    ): Response<ApiResponse<Unit>>
}
