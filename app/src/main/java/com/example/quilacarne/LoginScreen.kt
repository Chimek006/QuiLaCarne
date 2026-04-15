package com.example.quilacarne

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.models.LoginRequest
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.theme.green
import com.example.quilacarne.ui.theme.lightGray
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context.applicationContext) }

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QuiLaCarneHeader(showBack = false)
                Spacer(modifier = Modifier.height(26.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)
                    .align(Alignment.Center)
                    .wrapContentHeight()
                    .offset(y = (-8).dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                            onValueChange = { login = it }
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        LargeUnderlinedField(
                            valueHint = "Hasło",
                            value = password,
                            onValueChange = { password = it },
                            isPassword = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "O rejestrację lub w przypadku utraty hasła poproś o pomoc szefa sali",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
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

                        isLoading = true
                        scope.launch {
                            try {
                                val request = LoginRequest(
                                    username = login.trim(),
                                    password = password
                                )

                                val response = RetrofitClient.authService.login(request)

                                if (response.isSuccessful && response.body()?.isSuccess == true) {
                                    val data = response.body()?.data

                                    if (data != null) {
                                        tokenManager.saveTokens(
                                            accessToken = data.token,
                                            refreshToken = data.refreshToken
                                        )

                                        navController.navigate("main") {
                                            popUpTo("login") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Brak tokenów w odpowiedzi serwera")
                                    }
                                } else {
                                    val errorMsg =
                                        response.body()?.message
                                            ?: response.errorBody()?.string()
                                            ?: "Błędne dane logowania"

                                    snackbarHostState.showSnackbar(errorMsg)
                                }
                            } catch (e: Exception) {
                                Log.e("LoginError", "Błąd sieci: ${e.message}", e)
                                snackbarHostState.showSnackbar("Błąd połączenia z serwerem")
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 6.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.height(24.dp)
                        )
                    } else {
                        Text(
                            "Zaloguj się",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
    isPassword: Boolean = false
) {
    val textStyle = TextStyle(
        fontSize = 26.sp,
        textAlign = TextAlign.Center
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
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
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
                        color = Color.Black,
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