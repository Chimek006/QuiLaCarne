package com.example.quilacarne.data.remote.network

import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.models.RefreshRequest
import com.example.quilacarne.data.remote.services.AuthService
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticator(private val tokenManager: TokenManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        val refreshService = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)

        val refreshResponse = refreshService.refresh(RefreshRequest(refreshToken)).execute()

        if (refreshResponse.isSuccessful) {
            val newTokens = refreshResponse.body()?.data
            if (newTokens != null) {
                tokenManager.saveTokens(newTokens.token, newTokens.refreshToken)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.token}")
                    .build()
            }
        }

        tokenManager.clearTokens()
        return null
    }
}