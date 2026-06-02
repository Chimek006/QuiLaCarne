package com.example.quilacarne.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.quilacarne.utils.SecurePreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.encrypted(context, ENCRYPTED_PREFS_NAME)
    private val legacyPrefs: SharedPreferences = SecurePreferences.legacy(context, LEGACY_PREFS_NAME)

    init {
        migrateLegacyPrefs()
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CURRENT_USERNAME)
            .remove(KEY_CURRENT_USER_REMOTE_TOKEN)
            .apply()
    }

    fun setBootstrapped(status: Boolean) {
        prefs.edit().putBoolean(KEY_IS_BOOTSTRAPPED, status).apply()
    }

    fun isBootstrapped(): Boolean = prefs.getBoolean(KEY_IS_BOOTSTRAPPED, false)

    fun setCurrentUsername(username: String) {
        prefs.edit().putString(KEY_CURRENT_USERNAME, username).apply()
    }

    fun getCurrentUsername(): String? = prefs.getString(KEY_CURRENT_USERNAME, null)

    fun setCurrentUserRemoteToken(token: String) {
        prefs.edit().putString(KEY_CURRENT_USER_REMOTE_TOKEN, token).apply()
    }

    fun getCurrentUserRemoteToken(): String? = prefs.getString(KEY_CURRENT_USER_REMOTE_TOKEN, null)

    private fun migrateLegacyPrefs() {
        val hasLegacyData = LEGACY_KEYS.any { legacyPrefs.contains(it) }
        if (!hasLegacyData) return

        prefs.edit {
            legacyPrefs.getString(KEY_ACCESS_TOKEN, null)?.let { putString(KEY_ACCESS_TOKEN, it) }
            legacyPrefs.getString(KEY_REFRESH_TOKEN, null)?.let { putString(KEY_REFRESH_TOKEN, it) }
            legacyPrefs.getString(KEY_CURRENT_USERNAME, null)?.let { putString(KEY_CURRENT_USERNAME, it) }
            if (legacyPrefs.contains(KEY_IS_BOOTSTRAPPED)) {
                putBoolean(KEY_IS_BOOTSTRAPPED, legacyPrefs.getBoolean(KEY_IS_BOOTSTRAPPED, false))
            }
        }

        legacyPrefs.edit {
            LEGACY_KEYS.forEach(::remove)
        }
    }

    companion object {
        const val LEGACY_PREFS_NAME = "auth_prefs"
        const val ENCRYPTED_PREFS_NAME = "auth_prefs_encrypted"
        const val KEY_ACCESS_TOKEN = "ACCESS_TOKEN"
        const val KEY_REFRESH_TOKEN = "REFRESH_TOKEN"
        const val KEY_CURRENT_USERNAME = "CURRENT_USERNAME"
        const val KEY_CURRENT_USER_REMOTE_TOKEN = "CURRENT_USER_REMOTE_TOKEN"
        const val KEY_IS_BOOTSTRAPPED = "IS_BOOTSTRAPPED"
        private val LEGACY_KEYS = listOf(
            KEY_ACCESS_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_CURRENT_USERNAME,
            KEY_CURRENT_USER_REMOTE_TOKEN,
            KEY_IS_BOOTSTRAPPED
        )

        fun clearForTests(context: Context) {
            SecurePreferences.clearForTests(context, LEGACY_PREFS_NAME)
            SecurePreferences.clearForTests(context, ENCRYPTED_PREFS_NAME)
        }
    }
}
