package com.example.quilacarne.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    @Volatile
    private var encryptedFactoryOverride: ((Context, String) -> SharedPreferences)? = null

    fun encrypted(context: Context, name: String): SharedPreferences {
        encryptedFactoryOverride?.let { factory ->
            return factory(context.applicationContext, name)
        }

        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun legacy(context: Context, name: String): SharedPreferences {
        return context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun clearForTests(context: Context, name: String) {
        runCatching {
            encrypted(context, name).edit().clear().commit()
        }
        legacy(context, name).edit().clear().commit()
    }

    fun setEncryptedFactoryForTests(factory: (Context, String) -> SharedPreferences) {
        encryptedFactoryOverride = factory
    }

    fun resetEncryptedFactoryForTests() {
        encryptedFactoryOverride = null
    }
}
