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
        prefs.edit()
            .remove("ACCESS_TOKEN")
            .remove("REFRESH_TOKEN")
            .remove("CURRENT_USERNAME")
            .apply()
    }

    fun setBootstrapped(status: Boolean) {
        prefs.edit().putBoolean("IS_BOOTSTRAPPED", status).apply()
    }

    fun isBootstrapped(): Boolean = prefs.getBoolean("IS_BOOTSTRAPPED", false)

    fun setCurrentUsername(username: String) {
        prefs.edit().putString("CURRENT_USERNAME", username).apply()
    }

    fun getCurrentUsername(): String? = prefs.getString("CURRENT_USERNAME", null)
}
