package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.MainActivity
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.network.ConnectionIssue
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.ui.state.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application.applicationContext)
    private val authService = RetrofitClient.authenticatedAuthService

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, message = null) }

            val canReachServer =
                MainActivity.networkMonitor.connectionIssue.value == ConnectionIssue.None
            val hasOfflineToken =
                tokenManager.getAccessToken()?.startsWith("offline_token_") == true

            if (!canReachServer || hasOfflineToken) {
                tokenManager.clearTokens()
                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        isLoggedOut = true,
                        message = "Wylogowano"
                    )
                }
                return@launch
            }

            runCatching {
                authService.logout()
            }.onFailure {
                tokenManager.clearTokens()
                _uiState.update { state ->
                    state.copy(
                        isLoggingOut = false,
                        isLoggedOut = true,
                        message = "Wylogowano lokalnie"
                    )
                }
            }.onSuccess { response ->
                tokenManager.clearTokens()
                _uiState.update { state ->
                    state.copy(
                        isLoggingOut = false,
                        isLoggedOut = true,
                        message = response.body()?.message ?: "Wylogowano"
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
