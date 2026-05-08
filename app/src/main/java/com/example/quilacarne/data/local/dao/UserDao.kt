package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.UsersEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UsersEntity?

    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    suspend fun getFirstActiveUser(): UsersEntity?

    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun getUserByUsernameAndPassword(
        username: String,
        password: String
    ): UsersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UsersEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UsersEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUsersCount(): Int

    @Query("UPDATE users SET username = :newUsername, updated_at = :updatedAt WHERE username = :oldUsername")
    suspend fun updateUsername(oldUsername: String, newUsername: String, updatedAt: String)

    @Query("UPDATE users SET password = :newPassword, updated_at = :updatedAt WHERE username = :username")
    suspend fun updatePassword(username: String, newPassword: String, updatedAt: String)
}
