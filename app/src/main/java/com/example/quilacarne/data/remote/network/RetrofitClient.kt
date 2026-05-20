package com.example.quilacarne.data.remote.network

import android.content.Context
import android.util.Log
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.api.AuthService
import com.example.quilacarne.data.remote.api.TableService
import com.example.quilacarne.data.remote.api.OrderService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.quilacarne.data.remote.api.DishService

object RetrofitClient {
    private val BASE_URL = BuildConfig.BASE_URL
    private var tokenManager: TokenManager? = null

    fun init(context: Context) {
        if (tokenManager == null) {
            validateHttpsForRelease()
            tokenManager = TokenManager(context)
            Log.d("API_HTTP", "Retrofit initialized")
        }
    }

    private fun getTokenManager() = tokenManager!!

    private val authOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(ApiLoggingInterceptor())
            .build()
    }

    private val authenticatedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(getTokenManager()))
            .addInterceptor(ApiLoggingInterceptor())
            .authenticator(TokenAuthenticator(getTokenManager()))
            .build()
    }

    private fun createRetrofit(client: OkHttpClient): Retrofit {
        validateHttpsForRelease()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun validateHttpsForRelease() {
        check(BuildConfig.DEBUG || BASE_URL.startsWith("https://", ignoreCase = true)) {
            "Release builds require an HTTPS BASE_URL."
        }
    }

    val authService: AuthService by lazy {
        createRetrofit(authOkHttpClient).create(AuthService::class.java)
    }

    val authenticatedAuthService: AuthService by lazy {
        createRetrofit(authenticatedOkHttpClient).create(AuthService::class.java)
    }

    val tableService: TableService by lazy {
        createRetrofit(authenticatedOkHttpClient).create(TableService::class.java)
    }

    val orderService: OrderService by lazy {
        createRetrofit(authenticatedOkHttpClient).create(OrderService::class.java)
    }

    val dishService: DishService by lazy {
        createRetrofit(authenticatedOkHttpClient).create(DishService::class.java)
    }
}
