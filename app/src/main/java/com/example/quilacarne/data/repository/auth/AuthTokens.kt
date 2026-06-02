package com.example.quilacarne.data.repository.auth

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val username: String
)
