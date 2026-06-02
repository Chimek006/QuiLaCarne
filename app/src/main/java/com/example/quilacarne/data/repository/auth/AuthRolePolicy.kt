package com.example.quilacarne.data.repository.auth

import java.util.Locale

internal object AuthRolePolicy {
    fun isWaiter(roles: List<String>): Boolean {
        return roles.any(::isWaiter)
    }

    fun isWaiter(role: String): Boolean {
        return role
            .split(',', ';', ' ')
            .map { it.trim().uppercase(Locale.US) }
            .any { token -> token == "ROLE_WAITER" || token == "WAITER" }
    }

    fun roleString(roles: List<String>): String {
        return roles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.uppercase(Locale.US) }
            .joinToString(",")
            .ifBlank { "UNKNOWN" }
    }
}
