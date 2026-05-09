package com.example.quilacarne.data.repository

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.models.ChangePasswordRequest
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsRepository(
    private val database: AppDatabase,
    private val tokenManager: TokenManager
) {
    private val userDao = database.userDao()
    private val userService = RetrofitClient.userService
    private val authService = RetrofitClient.authenticatedAuthService

    suspend fun getCurrentUser(): UsersEntity? {
        val currentUsername = tokenManager.getCurrentUsername()
        return currentUsername
            ?.let { userDao.getUserByUsername(it) }
            ?: userDao.getFirstActiveUser()
    }

    suspend fun updateUsername(newUsername: String): Result<Unit> {
        return try {
            val currentUser = getCurrentUser()
                ?: return Result.failure(Exception("Brak lokalnego użytkownika"))

            val response = userService.updateUsername(newUsername.trim())

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                userDao.updateUsername(
                    oldUsername = currentUser.username,
                    newUsername = newUsername.trim(),
                    updatedAt = now()
                )
                tokenManager.setCurrentUsername(newUsername.trim())
                Result.success(Unit)
            } else {
                Result.failure(Exception(apiErrorMessage(response.code(), response.body()?.message)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> {
        return try {
            val currentUser = getCurrentUser()
                ?: return Result.failure(Exception("Brak lokalnego użytkownika"))

            val response = userService.updatePassword(
                ChangePasswordRequest(
                    oldPassword = oldPassword,
                    password = newPassword,
                    confirmPassword = confirmPassword
                )
            )

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                userDao.updatePassword(
                    username = currentUser.username,
                    newPassword = newPassword,
                    updatedAt = now()
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception(apiErrorMessage(response.code(), response.body()?.message)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(canReachServer: Boolean): Result<Unit> {
        if (!canReachServer || tokenManager.getAccessToken()?.startsWith("offline_token_") == true) {
            tokenManager.clearTokens()
            return Result.success(Unit)
        }

        return try {
            val response = authService.logout()
            tokenManager.clearTokens()

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Wylogowano lokalnie, ale API zwróciło błąd ${response.code()}"))
            }
        } catch (e: Exception) {
            tokenManager.clearTokens()
            Result.failure(e)
        }
    }

    private fun now(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
    }

    private fun apiErrorMessage(code: Int, message: String?): String {
        return when (code) {
            403 -> "API odrzuciło zmianę konta dla tej roli użytkownika (403)"
            else -> message ?: "Błąd API ($code)"
        }
    }
}
