package com.example.quilacarne

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader

@Composable
fun LoginScreen(navController: NavController) {
    val loginState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
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
                    modifier = Modifier.wrapContentHeight().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LargeUnderlinedField(valueHint = "Login/email", value = loginState)
                    Spacer(modifier = Modifier.height(28.dp))
                    LargeUnderlinedField(valueHint = "Hasło", value = passwordState, isPassword = true)
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
                onClick = { navController.navigate("main") },
                colors = ButtonDefaults.buttonColors(containerColor = green),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
            ) {
                Text("Zaloguj się", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LargeUnderlinedField(valueHint: String, value: MutableState<String>, isPassword: Boolean = false) {
    val textStyle = TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value.value,
            onValueChange = { value.value = it },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(Color.Black),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        ) { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.value.isEmpty()) {
                    Text(text = valueHint, fontSize = 26.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                }
                innerTextField()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 3.dp, color = Color.Black, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    }
}