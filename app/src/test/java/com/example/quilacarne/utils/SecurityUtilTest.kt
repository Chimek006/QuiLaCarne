package com.example.quilacarne.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityUtilTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurePreferences.setEncryptedFactoryForTests { appContext, name ->
            appContext.getSharedPreferences("test_encrypted_$name", Context.MODE_PRIVATE)
        }
        SecurityUtil.clearForTests(context)
    }

    @After
    fun tearDown() {
        SecurityUtil.clearForTests(context)
        SecurePreferences.resetEncryptedFactoryForTests()
    }

    @Test
    fun generatedDatabasePasswordIsStableAcrossReads() {
        val first = SecurityUtil.getDatabasePassword(context).toString(Charsets.UTF_8)
        val second = SecurityUtil.getDatabasePassword(context).toString(Charsets.UTF_8)

        assertEquals(first, second)
        assertTrue(first.length >= MIN_GENERATED_PASSWORD_LENGTH)
    }

    @Test
    fun legacyDatabasePasswordIsMigratedAndPreserved() {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DB_PASSWORD, LEGACY_PASSWORD)
            .commit()

        val migrated = SecurityUtil.getDatabasePassword(context).toString(Charsets.UTF_8)
        val secondRead = SecurityUtil.getDatabasePassword(context).toString(Charsets.UTF_8)

        assertEquals(LEGACY_PASSWORD, migrated)
        assertEquals(LEGACY_PASSWORD, secondRead)
        assertFalse(
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .contains(KEY_DB_PASSWORD)
        )
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "secure_prefs"
        const val KEY_DB_PASSWORD = "db_password"
        const val LEGACY_PASSWORD = "legacy-db-password"
        const val MIN_GENERATED_PASSWORD_LENGTH = 32
    }
}
