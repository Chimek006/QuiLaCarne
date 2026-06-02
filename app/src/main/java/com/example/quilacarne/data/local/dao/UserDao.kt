package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.UsersEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username)) LIMIT 1")
    suspend fun getUserByUsername(username: String): UsersEntity?

    @Query("SELECT * FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username)) LIMIT 1")
    fun getUserByUsernameFlow(username: String): Flow<UsersEntity?>

    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    suspend fun getFirstActiveUser(): UsersEntity?

    @Query("SELECT * FROM users WHERE deleted_at IS NULL ORDER BY username")
    fun getAllUsersFlow(): Flow<List<UsersEntity>>

    @Query("SELECT * FROM users WHERE deleted_at IS NULL ORDER BY username")
    suspend fun getAllUsersOnce(): List<UsersEntity>

    @Query("SELECT * FROM users WHERE deleted_at IS NULL AND LOWER(role) LIKE '%waiter%' ORDER BY username")
    fun getWaitersFlow(): Flow<List<UsersEntity>>

    @Query(
        """
        SELECT * FROM users
        WHERE deleted_at IS NULL
            AND is_active = 1
            AND LOWER(TRIM(username)) = LOWER(TRIM(:username))
            AND password = :password
        LIMIT 1
        """
    )
    suspend fun getUserByUsernameAndPassword(
        username: String,
        password: String
    ): UsersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UsersEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UsersEntity)

    @Query("UPDATE users SET deleted_at = :deletedAt, updated_at = :deletedAt, is_active = 0 WHERE id = :userId")
    suspend fun markUserDeleted(userId: UUID, deletedAt: String)

    @Query(
        """
        UPDATE users
        SET deleted_at = COALESCE(deleted_at, :deletedAt),
            updated_at = :deletedAt,
            is_active = 0,
            password = ''
        WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username))
        """
    )
    suspend fun disableLocalLoginForUsername(username: String, deletedAt: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUsersCount(): Int
}
