package com.example.quilacarne.ui.state

data class MainUiState(
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val message: String? = null
)
