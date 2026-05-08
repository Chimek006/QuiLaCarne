package com.example.quilacarne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.MainActivity
import com.example.quilacarne.data.remote.network.ConnectionIssue
import com.example.quilacarne.ui.theme.*

@Composable
fun QuiLaCarneHeader(
    navController: NavController? = null,
    showBack: Boolean = false
) {
    val connectionIssue by MainActivity.networkMonitor.connectionIssue.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(green),
            contentAlignment = Alignment.CenterEnd
        ) {
            when (connectionIssue) {
                ConnectionIssue.NoInternet -> {
                    ConnectionIssueBadge(
                        label = "WiFi",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }

                ConnectionIssue.NoServer -> {
                    ConnectionIssueBadge(
                        label = "API",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }

                ConnectionIssue.None -> Unit
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showBack && navController != null) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Cofnij",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Qui", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = red)
                Spacer(modifier = Modifier.width(6.dp))
                Text("La", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = green)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Carne", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = green)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(green, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
            )
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .background(Color(0xFFF3F3F3))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(red, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
            )
        }
    }
}

@Composable
private fun ConnectionIssueBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color(0xFFFFEBEE),
            border = BorderStroke(2.dp, Color(0xFFD32F2F))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = Color(0xFFD32F2F),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .rotate(-35f)
                .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp))
        )
    }
}
