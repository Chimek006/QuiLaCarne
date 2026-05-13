package com.example.quilacarne.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        tokenManager = TokenManager(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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
}
