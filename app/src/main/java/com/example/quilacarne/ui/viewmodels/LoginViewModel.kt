package com.example.quilacarne.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val authRepository = AuthRepository(db)
    private val tokenManager = TokenManager(application.applicationContext)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun hasBootstrapped(): Boolean {
        return tokenManager.isBootstrapped()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            val result = authRepository.loginHybrid(username, password)

            result.onSuccess { token ->
                tokenManager.saveTokens(
                    accessToken = token,
                    refreshToken = "offline_refresh"
                )
                _loginState.value = LoginState.Success
            }

            result.onFailure { error ->
                Log.e("LOGIN_VM", "Logowanie nie powiodło się: ${error.message}")
                _loginState.value = LoginState.Error(error.message ?: "Nieznany błąd")
            }
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}