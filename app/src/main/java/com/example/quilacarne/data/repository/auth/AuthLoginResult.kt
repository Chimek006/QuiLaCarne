package com.example.quilacarne.data.repository.auth

data class AuthLoginResult(
    val token: String,
    val refreshToken: String,
    val username: String,
    val source: LoginSource
)
