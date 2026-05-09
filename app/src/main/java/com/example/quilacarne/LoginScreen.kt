package com.example.quilacarne

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quilacarne.data.repository.LoginSource
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.theme.green
import com.example.quilacarne.ui.theme.lightGray
import com.example.quilacarne.ui.viewmodels.LoginViewModel
import com.example.quilacarne.ui.viewmodels.LoginState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val loginViewModel: LoginViewModel = viewModel()

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keepLoginButtonLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loginState by loginViewModel.loginState.collectAsState()
    val isLoginBusy = loginState is LoginState.Loading || keepLoginButtonLoading

    LaunchedEffect(loginState) {
        when (loginState) {
            is LoginState.Loading -> {
                keepLoginButtonLoading = true
            }

            is LoginState.Success -> {
                val successState = loginState as LoginState.Success
                val hasBootstrapped = loginViewModel.hasBootstrapped()

                if (hasBootstrapped) {
                    when (successState.source) {
                        LoginSource.Online -> {
                            navController.navigate("sync") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }

                        LoginSource.OfflineNoInternet -> {
                            snackbarHostState.showSnackbar(
                                "Brak internetu - uruchomiono tryb offline"
                            )
                            navController.navigate("main") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }

                        LoginSource.OfflineServerUnavailable -> {
                            snackbarHostState.showSnackbar(
                                "Brak dostępu do serwera - uruchomiono tryb offline"
                            )
                            navController.navigate("main") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }
                    }
                } else {
                    when (successState.source) {
                        LoginSource.Online -> {
                            navController.navigate("sync") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }

                        LoginSource.OfflineNoInternet -> {
                            snackbarHostState.showSnackbar(
                                "Pierwsze logowanie wymaga połączenia z internetem do pobrania danych!"
                            )
                            keepLoginButtonLoading = false
                        }

                        LoginSource.OfflineServerUnavailable -> {
                            snackbarHostState.showSnackbar(
                                "Brak dostępu do serwera - pierwsza synchronizacja nie może zostać wykonana"
                            )
                            keepLoginButtonLoading = false
                        }
                    }
                }
            }

            is LoginState.Error -> {
                keepLoginButtonLoading = false
                snackbarHostState.showSnackbar(
                    (loginState as LoginState.Error).message
                )
            }

            else -> {
                keepLoginButtonLoading = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QuiLaCarneHeader(showBack = false)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .shadow(30.dp, RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = lightGray),
                        elevation = CardDefaults.cardElevation(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .wrapContentHeight()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            LargeUnderlinedField(
                                valueHint = "Login/email",
                                value = login,
                                onValueChange = { login = it },
                                enabled = !isLoginBusy
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            LargeUnderlinedField(
                                valueHint = "Hasło",
                                value = password,
                                onValueChange = { password = it },
                                isPassword = true,
                                enabled = !isLoginBusy
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "O rejestrację, lub w przypadku utraty hasła poproś szefa kuchni o pomoc",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            if (login.isBlank() || password.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Uzupełnij login i hasło")
                                }
                                return@Button
                            }

                            MainActivity.networkMonitor.refresh()
                            val isOnline = MainActivity.networkMonitor.isOnline.value
                            keepLoginButtonLoading = true
                            loginViewModel.login(login, password, isOnline)
                        },
                        enabled = !isLoginBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = green,
                            disabledContainerColor = green
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(6.dp, RoundedCornerShape(14.dp))
                    ) {
                        if (isLoginBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Zaloguj się", color = Color.White, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LargeUnderlinedField(
    valueHint: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    val textStyle = TextStyle(
        fontSize = 26.sp,
        textAlign = TextAlign.Center,
        color = Color.Black
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(Color.Black),
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = valueHint,
                        fontSize = 26.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                innerTextField()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider(
            thickness = 3.dp,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}
