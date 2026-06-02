package com.example.quilacarne.ui.state

import com.example.quilacarne.data.repository.auth.LoginSource

data class LoginStateSuccess(val source: LoginSource) : LoginState()
