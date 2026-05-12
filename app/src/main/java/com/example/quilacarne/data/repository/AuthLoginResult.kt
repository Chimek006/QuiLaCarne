package com.example.quilacarne.data.repository

data class AuthLoginResult(
    val token: String,
    val refreshToken: String,
    val source: LoginSource
)
