package com.example.quilacarne.data.local.dao

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.UsersEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = TestDatabaseFactory.create()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findsUserByUsernameAndPasswordCaseInsensitively() = runTest {
        val user = UsersEntity(
            id = UUID.randomUUID(),
            username = "Waiter@example.com",
            password = "secret",
            isActive = true,
            role = "waiter",
            createdAt = "created",
            updatedAt = "updated"
        )

        db.userDao().insertUser(user)

        assertEquals(user.id, db.userDao().getUserByUsername("waiter@example.com")?.id)
        assertNotNull(db.userDao().getUserByUsernameAndPassword("WAITER@example.com", "secret"))
        assertNull(db.userDao().getUserByUsernameAndPassword("WAITER@example.com", "wrong"))
    }
}
