package com.example.quilacarne.data.repository

import android.util.Log
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.dto.request.LoginRequest
import com.example.quilacarne.data.remote.dto.response.UserProfileData
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.util.Locale
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
                    val loginUsername = username.trim()
                    val profile = fetchCurrentUserProfile(data.token)
                    val apiUsername = data.username.trim().takeIf { it.isNotBlank() }
                    val profileUsername = profile.username.trim().takeIf { it.isNotBlank() }
                    val profileRoles = profile.roles.orEmpty()
                    val role = AuthRolePolicy.roleString(profileRoles)
                    val now = System.currentTimeMillis().toString()
                    val localUsernames = buildList {
                        add(loginUsername)
                        apiUsername?.let { add(it) }
                        profileUsername?.let { add(it) }
                    }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase(Locale.US) }

                    if (!AuthRolePolicy.isWaiter(profileRoles)) {
                        markRejectedOnlineUsers(localUsernames, role, now)
                        return Result.failure(UnsupportedUserRoleException())
                    }

                    val localUsers = localUsernames
                        .map { localUsername ->
                            val existing = userDao.getUserByUsername(localUsername)
                            UsersEntity(
                                id = existing?.id ?: UUID.randomUUID(),
                                username = localUsername,
                                password = password,
                                isActive = existing?.isActive ?: true,
                                role = role,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now
                            )
                        }

                    userDao.insertUsers(localUsers)
                    Log.d("AUTH_REPO", "✓ Zalogowano i zapisano offline")

                    Result.success(
                        AuthTokens(
                            accessToken = data.token,
                            refreshToken = data.refreshToken,
                            username = profileUsername ?: apiUsername ?: loginUsername
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
                if (!AuthRolePolicy.isWaiter(user.role)) {
                    return Result.failure(UnsupportedUserRoleException())
                }

                Log.d("AUTH_REPO", "✓ Zalogowano offline")
                Result.success(
                    AuthTokens(
                        accessToken = "offline_token_${user.id}",
                        refreshToken = "offline_refresh",
                        username = user.username
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
        } else if (onlineResult.exceptionOrNull() is UnsupportedUserRoleException ||
            onlineResult.exceptionOrNull() is UserProfileVerificationException
        ) {
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
                        username = tokens.username,
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

    private suspend fun fetchCurrentUserProfile(accessToken: String): UserProfileData {
        val response = RetrofitClient.authService.me("Bearer $accessToken")
        if (response.isSuccessful && response.body()?.isSuccess == true) {
            return response.body()?.data
                ?: throw UserProfileVerificationException("Brak danych profilu uzytkownika")
        }

        throw UserProfileVerificationException(response.body()?.message ?: "Nie udalo sie pobrac profilu uzytkownika")
    }

    private suspend fun markRejectedOnlineUsers(
        usernames: List<String>,
        role: String,
        now: String
    ) {
        val users = usernames.map { localUsername ->
            val existing = userDao.getUserByUsername(localUsername)
            UsersEntity(
                id = existing?.id ?: UUID.randomUUID(),
                username = localUsername,
                password = "",
                isActive = false,
                role = role,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }

        if (users.isNotEmpty()) {
            userDao.insertUsers(users)
        }
    }
}

class UnsupportedUserRoleException : Exception(
    "Do aplikacji moga logowac sie tylko kelnerzy."
)

class UserProfileVerificationException(message: String) : Exception(message)

internal object AuthRolePolicy {
    fun isWaiter(roles: List<String>): Boolean {
        return roles.any(::isWaiter)
    }

    fun isWaiter(role: String): Boolean {
        return role
            .split(',', ';', ' ')
            .map { it.trim().uppercase(Locale.US) }
            .any { token -> token == "ROLE_WAITER" || token == "WAITER" }
    }

    fun roleString(roles: List<String>): String {
        return roles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.uppercase(Locale.US) }
            .joinToString(",")
            .ifBlank { "UNKNOWN" }
    }
}
