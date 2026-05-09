package com.example.quilacarne.data.remote.network

import android.content.Context
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.services.AuthService
import com.example.quilacarne.data.remote.services.TableService
import com.example.quilacarne.data.remote.services.OrderService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.quilacarne.data.remote.services.DishService

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

    private fun createRetrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

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
