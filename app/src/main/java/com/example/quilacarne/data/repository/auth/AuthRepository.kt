package com.example.quilacarne.data.repository.auth

import android.util.Log
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.dto.request.LoginRequest
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.remote.dto.response.LoginData
import com.example.quilacarne.data.remote.dto.response.UserProfileData
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.io.IOException
import java.util.Locale
import java.util.UUID

class AuthRepository(private val database: AppDatabase) {

    private val userDao = database.userDao()

    suspend fun loginOnline(username: String, password: String): Result<AuthTokens> {
        val loginUsername = username.trim()

        return try {
            val request = LoginRequest(username = loginUsername, password = password)
            val response = RetrofitClient.authService.login(request)

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val data = response.body()?.data
                if (data != null) {
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
                    Log.d("AUTH_REPO", "Zalogowano i zapisano offline")

                    Result.success(
                        AuthTokens(
                            accessToken = data.token,
                            refreshToken = data.refreshToken,
                            username = profileUsername ?: apiUsername ?: loginUsername
                        )
                    )
                } else {
                    Result.failure(Exception("Brak tokenow w odpowiedzi"))
                }
            } else {
                val rejection = response.loginRejection()
                if (rejection.disableLocalLogin) {
                    userDao.disableLocalLoginForUsername(loginUsername, System.currentTimeMillis().toString())
                }
                Result.failure(RemoteLoginRejectedException(rejection.message))
            }
        } catch (e: IOException) {
            Log.e("AUTH_REPO", "Blad sieci (sprobujemy offline): ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AUTH_REPO", "Blad logowania online: ${e.message}")
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

                Log.d("AUTH_REPO", "Zalogowano offline")
                Result.success(
                    AuthTokens(
                        accessToken = "offline_token_${user.id}",
                        refreshToken = "offline_refresh",
                        username = user.username
                    )
                )
            } else {
                Result.failure(Exception("Brak uzytkownika w bazie lokalnej"))
            }
        } catch (e: Exception) {
            Log.e("AUTH_REPO", "Blad logowania offline: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginHybrid(
        username: String,
        password: String,
        isOnline: Boolean
    ): Result<AuthLoginResult> {
        if (!isOnline) {
            Log.w("AUTH_REPO", "Brak internetu, probuje offline...")
            return loginOffline(username, password)
                .withSource(LoginSource.OfflineNoInternet)
        }

        val onlineResult = loginOnline(username, password)
        val onlineError = onlineResult.exceptionOrNull()

        return if (onlineResult.isSuccess) {
            onlineResult.withSource(LoginSource.Online)
        } else if (onlineError is UnsupportedUserRoleException ||
            onlineError is UserProfileVerificationException ||
            onlineError is RemoteLoginRejectedException
        ) {
            onlineResult.withSource(LoginSource.Online)
        } else {
            Log.w("AUTH_REPO", "API niedostepne, probuje offline...")
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

    private fun retrofit2.Response<ApiResponse<LoginData>>.loginRejection(): LoginRejection {
        val rawMessage = body()?.errorMessages?.joinToString("; ")
            ?: body()?.message
            ?: errorBody()?.string()
            ?: "Bledne dane logowania"
        val normalized = rawMessage.lowercase(Locale.US)
        val accountUnavailable = code() == 404 ||
            listOf(
                "deleted",
                "not found",
                "inactive",
                "disabled",
                "blocked",
                "banned",
                "ban",
                "usun",
                "zablok"
            ).any { marker -> marker in normalized }

        return if (accountUnavailable) {
            LoginRejection(
                message = "Uzytkownik zostal usuniety z bazy danych lub zablokowany.",
                disableLocalLogin = true
            )
        } else {
            LoginRejection(
                message = rawMessage.ifBlank { "Bledne dane logowania" },
                disableLocalLogin = false
            )
        }
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
