package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.UsersEntity
import java.util.UUID

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UsersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UsersEntity>)
}