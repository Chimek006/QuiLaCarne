package com.example.quilacarne.utils

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

object SecurityUtil {
    private const val LEGACY_PREFS_NAME = "secure_prefs"
    private const val ENCRYPTED_PREFS_NAME = "secure_prefs_encrypted"
    private const val KEY_DB_PASSWORD = "db_password"
    private const val PASSWORD_BYTES = 32

    fun getDatabasePassword(context: Context): ByteArray {
        val encryptedPrefs = runCatching {
            SecurePreferences.encrypted(context, ENCRYPTED_PREFS_NAME)
        }.getOrNull()
        val legacyPrefs = SecurePreferences.legacy(context, LEGACY_PREFS_NAME)

        val password = encryptedPrefs?.getString(KEY_DB_PASSWORD, null)
            ?: migrateLegacyPassword(legacyPrefs, encryptedPrefs)
            ?: createNewPassword(encryptedPrefs)

        return password.toByteArray(Charsets.UTF_8)
    }

    private fun migrateLegacyPassword(
        legacyPrefs: android.content.SharedPreferences,
        encryptedPrefs: android.content.SharedPreferences?
    ): String? {
        val legacyPassword = legacyPrefs.getString(KEY_DB_PASSWORD, null)?.takeIf { it.isNotBlank() }
        if (legacyPassword != null && encryptedPrefs != null) {
            // Preserve the existing SQLCipher passphrase during migration so an installed app
            // can still open its current encrypted database after moving to Keystore-backed prefs.
            encryptedPrefs.edit {
                putString(KEY_DB_PASSWORD, legacyPassword)
            }
            legacyPrefs.edit {
                remove(KEY_DB_PASSWORD)
            }
        }
        return legacyPassword
    }

    private fun createNewPassword(encryptedPrefs: android.content.SharedPreferences?): String {
        checkNotNull(encryptedPrefs) {
            "Secure storage for SQLCipher passphrase is unavailable; refusing to generate a replacement passphrase."
        }

        val password = generateStrongPassword()
        encryptedPrefs.edit {
            putString(KEY_DB_PASSWORD, password)
        }
        return password
    }

    private fun generateStrongPassword(): String {
        val randomBytes = ByteArray(PASSWORD_BYTES)
        SecureRandom().nextBytes(randomBytes)
        return android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
    }

    fun clearForTests(context: Context) {
        SecurePreferences.clearForTests(context, LEGACY_PREFS_NAME)
        SecurePreferences.clearForTests(context, ENCRYPTED_PREFS_NAME)
    }
}
