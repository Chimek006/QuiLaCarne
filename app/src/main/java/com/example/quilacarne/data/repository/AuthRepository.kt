package com.example.quilacarne.data.repository

import android.util.Log
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.models.LoginRequest
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.util.UUID

class AuthRepository(private val database: AppDatabase) {

    private val userDao = database.userDao()

    suspend fun loginOnline(username: String, password: String): Result<String> {
        return try {
            val request = LoginRequest(username = username.trim(), password = password)
            val response = RetrofitClient.authService.login(request)

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val data = response.body()?.data
                if (data != null) {
                    val userEntity = UsersEntity(
                        id = UUID.randomUUID(),
                        username = username.trim(),
                        password = password,
                        isActive = true,
                        role = "waiter",
                        createdAt = System.currentTimeMillis().toString(),
                        updatedAt = System.currentTimeMillis().toString()
                    )
                    userDao.insertUser(userEntity)
                    Log.d("AUTH_REPO", "✓ Zalogowano i zapisano offline")

                    Result.success(data.token)
                } else {
                    Result.failure(Exception("Brak tokenów w odpowiedzi"))
                }
            } else {
                Result.failure(Exception("Błędne dane logowania"))
            }
        } catch (e: Exception) {
            Log.e("AUTH_REPO", "Błąd sieci (spróbujemy offline): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginOffline(username: String, password: String): Result<String> {
        return try {
            val user = userDao.getUserByUsernameAndPassword(username.trim(), password)
            if (user != null) {
                Log.d("AUTH_REPO", "✓ Zalogowano offline")
                Result.success("offline_token_${user.id}")
            } else {
                Result.failure(Exception("Brak użytkownika w bazie lokalnej"))
            }
        } catch (e: Exception) {
            Log.e("AUTH_REPO", "Błąd logowania offline: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginHybrid(username: String, password: String): Result<String> {
        val onlineResult = loginOnline(username, password)

        return if (onlineResult.isSuccess) {
            onlineResult
        } else {
            Log.w("AUTH_REPO", "API niedostępne, próbuję offline...")
            loginOffline(username, password)
        }
    }
}