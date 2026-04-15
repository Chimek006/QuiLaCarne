package com.example.quilacarne.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("ACCESS_TOKEN", accessToken)
            .putString("REFRESH_TOKEN", refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString("ACCESS_TOKEN", null)
    fun getRefreshToken(): String? = prefs.getString("REFRESH_TOKEN", null)

    fun clearTokens() {
        prefs.edit().clear().apply()
    }
}