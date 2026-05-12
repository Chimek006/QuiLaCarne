package com.example.quilacarne.data.repository

import android.util.Log
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.models.LoginRequest
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.util.UUID

class AuthRepository(private val database: AppDatabase) {

    private val userDao = database.userDao()

    suspend fun loginOnline(username: String, password: String): Result<AuthTokens> {
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

                    Result.success(
                        AuthTokens(
                            accessToken = data.token,
                            refreshToken = data.refreshToken
                        )
                    )
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

    suspend fun loginOffline(username: String, password: String): Result<AuthTokens> {
        return try {
            val user = userDao.getUserByUsernameAndPassword(username.trim(), password)
            if (user != null) {
                Log.d("AUTH_REPO", "✓ Zalogowano offline")
                Result.success(
                    AuthTokens(
                        accessToken = "offline_token_${user.id}",
                        refreshToken = "offline_refresh"
                    )
                )
            } else {
                Result.failure(Exception("Brak użytkownika w bazie lokalnej"))
            }
        } catch (e: Exception) {
            Log.e("AUTH_REPO", "Błąd logowania offline: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginHybrid(
        username: String,
        password: String,
        isOnline: Boolean
    ): Result<AuthLoginResult> {
        if (!isOnline) {
            Log.w("AUTH_REPO", "Brak internetu, próbuję offline...")
            return loginOffline(username, password)
                .withSource(LoginSource.OfflineNoInternet)
        }

        val onlineResult = loginOnline(username, password)

        return if (onlineResult.isSuccess) {
            onlineResult.withSource(LoginSource.Online)
        } else {
            Log.w("AUTH_REPO", "API niedostępne, próbuję offline...")
            loginOffline(username, password)
                .withSource(LoginSource.OfflineServerUnavailable)
        }
    }

    private fun Result<AuthTokens>.withSource(source: LoginSource): Result<AuthLoginResult> {
        return fold(
            onSuccess = { tokens ->
                Result.success(
                    AuthLoginResult(
                        token = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        source = source
                    )
                )
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    suspend fun hasAnyLocalData(): Boolean {
        return userDao.getUsersCount() > 0
    }
}
