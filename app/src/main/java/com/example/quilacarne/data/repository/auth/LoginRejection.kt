package com.example.quilacarne.data.repository.auth

internal data class LoginRejection(
    val message: String,
    val disableLocalLogin: Boolean
)
