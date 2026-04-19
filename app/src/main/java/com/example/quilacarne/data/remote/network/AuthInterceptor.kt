package com.example.quilacarne.data.remote.network

import com.example.quilacarne.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        requestBuilder.addHeader("Content-Type", "application/json")
        requestBuilder.addHeader("Accept", "application/json")

        requestBuilder.addHeader("Accept-Language", Locale.getDefault().language)

        val token = tokenManager.getAccessToken()

        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}