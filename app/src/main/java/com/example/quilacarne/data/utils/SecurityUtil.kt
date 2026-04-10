package com.example.quilacarne.data.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import androidx.core.content.edit

object SecurityUtil {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_DB_PASSWORD = "db_password"

    fun getDatabasePassword(context: Context): ByteArray {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var password = prefs.getString(KEY_DB_PASSWORD, null)

        if (password == null) {
            password = UUID.randomUUID().toString()
            prefs.edit {
                putString(KEY_DB_PASSWORD, password)
            }
        }

        return password.toByteArray()
    }
}