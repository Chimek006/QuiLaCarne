package com.example.quilacarne.data.repository.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.local.dao.TestDatabaseFactory
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.utils.SecurePreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRepositoryWebSocketSessionTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tokenManager: TokenManager
    private lateinit var remoteTokenStore: RemoteTokenStore
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurePreferences.setEncryptedFactoryForTests { appContext, name ->
            appContext.getSharedPreferences("test_encrypted_$name", Context.MODE_PRIVATE)
        }
        TokenManager.clearForTests(context)
        RemoteTokenStore.clearForTests(context)
        RetrofitClient.init(context)
        db = TestDatabaseFactory.create()
        tokenManager = TokenManager(context)
        remoteTokenStore = RemoteTokenStore(context)
        repository = SyncRepository(db, context)
    }

    @After
    fun tearDown() {
        db.close()
        TokenManager.clearForTests(context)
        RemoteTokenStore.clearForTests(context)
        SecurePreferences.resetEncryptedFactoryForTests()
    }

    @Test
    fun clearsSessionForCurrentUserPersonnelUpdate() = runTest {
        val user = insertCurrentUser(remoteToken = "remote-user-token")

        val cleared = repository.clearCurrentSessionForPersonnelUpdateIfNeeded(
            topic = PERSONNEL_TOPIC,
            rawMessage = personnelEvent(token = "  remote-user-token  ", username = user.username)
        )

        assertTrue(cleared)
        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
        assertNull(tokenManager.getCurrentUsername())
    }

    @Test
    fun keepsSessionForDifferentPersonnelUpdate() = runTest {
        insertCurrentUser(remoteToken = "current-user-token")

        val cleared = repository.clearCurrentSessionForPersonnelUpdateIfNeeded(
            topic = PERSONNEL_TOPIC,
            rawMessage = personnelEvent(token = "other-user-token", username = "other")
        )

        assertFalse(cleared)
        assertEquals("access-token", tokenManager.getAccessToken())
        assertEquals("refresh-token", tokenManager.getRefreshToken())
        assertEquals("waiter@example.com", tokenManager.getCurrentUsername())
    }

    @Test
    fun ignoresDeletedPersonnelEventForCurrentUser() = runTest {
        val user = insertCurrentUser(remoteToken = "remote-user-token")

        val cleared = repository.clearCurrentSessionForPersonnelUpdateIfNeeded(
            topic = PERSONNEL_TOPIC,
            rawMessage = personnelEvent(
                eventType = "DELETED",
                token = "remote-user-token",
                username = user.username
            )
        )

        assertFalse(cleared)
        assertEquals("access-token", tokenManager.getAccessToken())
        assertEquals("refresh-token", tokenManager.getRefreshToken())
    }

    @Test
    fun canMatchCurrentUserByPayloadToken() = runTest {
        val user = insertCurrentUser(remoteToken = "remote-user-token")

        val cleared = repository.clearCurrentSessionForPersonnelUpdateIfNeeded(
            topic = PERSONNEL_TOPIC,
            rawMessage = personnelEvent(token = "", payloadToken = "remote-user-token", username = user.username)
        )

        assertTrue(cleared)
        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
    }

    @Test
    fun ignoresCurrentUserEventFromOtherTopic() = runTest {
        val user = insertCurrentUser(remoteToken = "remote-user-token")

        val cleared = repository.clearCurrentSessionForPersonnelUpdateIfNeeded(
            topic = "/topic/orders/updates",
            rawMessage = personnelEvent(token = "remote-user-token", username = user.username)
        )

        assertFalse(cleared)
        assertEquals("access-token", tokenManager.getAccessToken())
        assertEquals("refresh-token", tokenManager.getRefreshToken())
    }

    private suspend fun insertCurrentUser(remoteToken: String): UsersEntity {
        val user = UsersEntity(
            id = UUID.nameUUIDFromBytes(remoteToken.toByteArray()),
            username = "waiter@example.com",
            password = "secret",
            isActive = true,
            role = "ROLE_WAITER",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        db.userDao().insertUser(user)
        remoteTokenStore.saveToken("user", user.id, remoteToken)
        tokenManager.saveTokens("access-token", "refresh-token")
        tokenManager.setCurrentUsername(user.username)
        return user
    }

    private fun personnelEvent(
        eventType: String = "UPDATED",
        token: String,
        payloadToken: String = token,
        username: String
    ): String {
        return """
            {
              "eventType": "$eventType",
              "entityType": "EMPLOYEE",
              "token": "$token",
              "payload": {
                "token": "$payloadToken",
                "username": "$username",
                "email": "$username",
                "isActive": true,
                "isStaff": true,
                "roleTokens": [],
                "createdAt": "2026-01-01T00:00:00Z",
                "updatedAt": "2026-01-01T00:00:00Z"
              },
              "timestamp": "2026-01-01T00:00:01Z"
            }
        """.trimIndent()
    }

    private companion object {
        const val PERSONNEL_TOPIC = "/topic/personnel/updates"
    }
}
