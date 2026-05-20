package com.example.quilacarne.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.quilacarne.utils.SecurePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenManagerTest {
    private lateinit var context: Context
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurePreferences.setEncryptedFactoryForTests { appContext, name ->
            appContext.getSharedPreferences("test_encrypted_$name", Context.MODE_PRIVATE)
        }
        TokenManager.clearForTests(context)
        tokenManager = TokenManager(context)
    }

    @After
    fun tearDown() {
        TokenManager.clearForTests(context)
        SecurePreferences.resetEncryptedFactoryForTests()
    }

    @Test
    fun saveTokensStoresAccessAndRefreshTokens() {
        tokenManager.saveTokens("access-token", "refresh-token")

        assertEquals("access-token", tokenManager.getAccessToken())
        assertEquals("refresh-token", tokenManager.getRefreshToken())
    }

    @Test
    fun clearTokensRemovesTokensAndCurrentUsernameButKeepsBootstrapFlag() {
        tokenManager.saveTokens("access-token", "refresh-token")
        tokenManager.setCurrentUsername("waiter")
        tokenManager.setBootstrapped(true)

        tokenManager.clearTokens()

        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
        assertNull(tokenManager.getCurrentUsername())
        assertTrue(tokenManager.isBootstrapped())
    }

    @Test
    fun bootstrappedFlagDefaultsFalseAndCanBeUpdated() {
        assertFalse(tokenManager.isBootstrapped())

        tokenManager.setBootstrapped(true)
        assertTrue(tokenManager.isBootstrapped())

        tokenManager.setBootstrapped(false)
        assertFalse(tokenManager.isBootstrapped())
    }

    @Test
    fun currentUsernameCanBeStoredAndRead() {
        tokenManager.setCurrentUsername("waiter_1")

        assertEquals("waiter_1", tokenManager.getCurrentUsername())
    }

    @Test
    fun legacyPreferencesAreMigratedToEncryptedStorage() {
        TokenManager.clearForTests(context)
        context.getSharedPreferences(TokenManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TokenManager.KEY_ACCESS_TOKEN, "legacy-access-token")
            .putString(TokenManager.KEY_REFRESH_TOKEN, "legacy-refresh-token")
            .putString(TokenManager.KEY_CURRENT_USERNAME, "legacy-waiter")
            .putBoolean(TokenManager.KEY_IS_BOOTSTRAPPED, true)
            .commit()

        tokenManager = TokenManager(context)

        assertEquals("legacy-access-token", tokenManager.getAccessToken())
        assertEquals("legacy-refresh-token", tokenManager.getRefreshToken())
        assertEquals("legacy-waiter", tokenManager.getCurrentUsername())
        assertTrue(tokenManager.isBootstrapped())
        assertFalse(
            context.getSharedPreferences(TokenManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .contains(TokenManager.KEY_ACCESS_TOKEN)
        )
    }
}
