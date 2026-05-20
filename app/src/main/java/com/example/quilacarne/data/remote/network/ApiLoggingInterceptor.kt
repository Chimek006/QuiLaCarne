package com.example.quilacarne.data.remote.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class ApiLoggingInterceptor(
    private val tag: String = "API_HTTP"
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = "${request.url.scheme}://${request.url.host}"

        Log.d(tag, "--> ${request.method} $target")

        return try {
            val response = chain.proceed(request)
            Log.d(tag, "<-- ${response.code} ${request.method} $target")
            response
        } catch (e: Exception) {
            Log.e(tag, "<-- HTTP FAILED ${request.method} $target: ${e.message}", e)
            throw e
        }
    }
}
