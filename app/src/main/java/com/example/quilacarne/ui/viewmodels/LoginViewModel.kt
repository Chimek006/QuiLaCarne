package com.example.quilacarne.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.repository.auth.AuthRepository
import com.example.quilacarne.ui.state.LoginState
import com.example.quilacarne.ui.state.LoginStateError
import com.example.quilacarne.ui.state.LoginStateIdle
import com.example.quilacarne.ui.state.LoginStateLoading
import com.example.quilacarne.ui.state.LoginStateSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val authRepository = AuthRepository(db)
    private val tokenManager = TokenManager(application.applicationContext)

    private val _loginState = MutableStateFlow<LoginState>(LoginStateIdle)
    val loginState: StateFlow<LoginState> = _loginState

    fun hasBootstrapped(): Boolean {
        return tokenManager.isBootstrapped()
    }

    fun login(username: String, password: String, isOnline: Boolean) {
        viewModelScope.launch {
            _loginState.value = LoginStateLoading

            val result = authRepository.loginHybrid(username, password, isOnline)

            result.onSuccess { loginResult ->
                tokenManager.saveTokens(
                    accessToken = loginResult.token,
                    refreshToken = loginResult.refreshToken
                )
                tokenManager.setCurrentUsername(loginResult.username)
                _loginState.value = LoginStateSuccess(loginResult.source)
            }

            result.onFailure { error ->
                Log.e("LOGIN_VM", "Logowanie nie powiodlo sie: ${error.message}")
                _loginState.value = LoginStateError(error.message ?: "Nieznany blad")
            }
        }
    }
}
