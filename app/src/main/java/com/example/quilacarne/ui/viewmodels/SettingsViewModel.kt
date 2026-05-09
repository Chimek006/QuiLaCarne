package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.MainActivity
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.network.ConnectionIssue
import com.example.quilacarne.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(
        database = AppDatabase.getDatabase(application),
        tokenManager = TokenManager(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadCurrentUser()
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            val username = repository.getCurrentUser()?.username.orEmpty()
            _uiState.update { it.copy(username = username) }
        }
    }

    fun updateUsername(username: String) {
        if (username.isBlank()) {
            setMessage("Nazwa użytkownika nie może być pusta")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingUsername = true, message = null) }

            repository.updateUsername(username).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            username = username.trim(),
                            isSavingUsername = false,
                            message = "Nazwa użytkownika została zmieniona"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSavingUsername = false,
                            message = error.message ?: "Nie udało się zmienić nazwy użytkownika"
                        )
                    }
                }
            )
        }
    }

    fun updatePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            setMessage("Uzupełnij wszystkie pola hasła")
            return
        }

        if (newPassword != confirmPassword) {
            setMessage("Nowe hasła nie są takie same")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, message = null) }

            repository.updatePassword(oldPassword, newPassword, confirmPassword).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            message = "Hasło zostało zmienione"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            message = error.message ?: "Nie udało się zmienić hasła"
                        )
                    }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, message = null) }

            val canReachServer = MainActivity.networkMonitor.connectionIssue.value == ConnectionIssue.None

            repository.logout(canReachServer).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            isLoggedOut = true,
                            message = "Wylogowano"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            isLoggedOut = true,
                            message = error.message ?: "Wylogowano lokalnie"
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }
}

data class SettingsUiState(
    val username: String = "",
    val isSavingUsername: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val message: String? = null
)
