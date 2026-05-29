package com.example.quilacarne.data.repository

data class AuthLoginResult(
    val token: String,
    val refreshToken: String,
    val username: String,
    val source: LoginSource
)
