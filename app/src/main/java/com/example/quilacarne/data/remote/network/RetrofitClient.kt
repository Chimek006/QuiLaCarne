package com.example.quilacarne.data.remote.network

import android.content.Context
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.services.AuthService
import com.example.quilacarne.data.remote.services.TableService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val BASE_URL = BuildConfig.BASE_URL

    private var tokenManager: TokenManager? = null

    fun init(context: Context) {
        if (tokenManager == null) {
            tokenManager = TokenManager(context)
        }
    }

    private fun getTokenManager() = tokenManager!!

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val authenticatedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(getTokenManager()))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(getTokenManager()))
            .build()
    }

    val authService: AuthService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)
    }

    val tableService: TableService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authenticatedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TableService::class.java)
    }
}