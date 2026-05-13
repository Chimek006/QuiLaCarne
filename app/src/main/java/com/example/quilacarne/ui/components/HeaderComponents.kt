package com.example.quilacarne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.MainActivity
import com.example.quilacarne.data.remote.network.ConnectionIssue
import com.example.quilacarne.ui.i18n.rememberAppLanguage
import com.example.quilacarne.ui.theme.*

@Composable
fun QuiLaCarneHeader(
    navController: NavController? = null,
    showBack: Boolean = false
) {
    val connectionIssue by MainActivity.networkMonitor.connectionIssue.collectAsState()
    val language = rememberAppLanguage()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(green)
        )

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
                        contentDescription = language.choose("Cofnij", "Back"),
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (connectionIssue != ConnectionIssue.None) {
                ConnectionIssueBadge(
                    issue = connectionIssue,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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
    issue: ConnectionIssue,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color(0xFFFFEBEE),
            border = BorderStroke(2.dp, Color(0xFFD32F2F))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val color = Color(0xFFD32F2F)
                    val stroke = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    when (issue) {
                        ConnectionIssue.NoInternet -> {
                            val centerX = size.width / 2f
                            val bottomY = size.height * 0.78f

                            drawArc(
                                color = color,
                                startAngle = 220f,
                                sweepAngle = 100f,
                                useCenter = false,
                                topLeft = Offset(3.dp.toPx(), 2.dp.toPx()),
                                size = Size(size.width - 6.dp.toPx(), size.height - 2.dp.toPx()),
                                style = stroke
                            )
                            drawArc(
                                color = color,
                                startAngle = 225f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(8.dp.toPx(), 9.dp.toPx()),
                                size = Size(size.width - 16.dp.toPx(), size.height - 12.dp.toPx()),
                                style = stroke
                            )
                            drawCircle(
                                color = color,
                                radius = 2.6.dp.toPx(),
                                center = Offset(centerX, bottomY)
                            )
                        }

                        ConnectionIssue.NoServer -> {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(5.dp.toPx(), 6.dp.toPx()),
                                size = Size(size.width - 10.dp.toPx(), 7.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = stroke
                            )
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(5.dp.toPx(), 16.dp.toPx()),
                                size = Size(size.width - 10.dp.toPx(), 7.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = stroke
                            )
                            drawCircle(
                                color = color,
                                radius = 1.2.dp.toPx(),
                                center = Offset(10.dp.toPx(), 9.5.dp.toPx())
                            )
                            drawCircle(
                                color = color,
                                radius = 1.2.dp.toPx(),
                                center = Offset(10.dp.toPx(), 19.5.dp.toPx())
                            )
                        }

                        ConnectionIssue.None -> Unit
                    }

                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.18f, size.height * 0.86f),
                        end = Offset(size.width * 0.86f, size.height * 0.18f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
