package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.MenuViewModel
import java.util.UUID

@Composable
fun DishDetailScreen(
    dishId: UUID,
    navController: NavController,
    viewModel: MenuViewModel
) {
    val detailedDish by viewModel.getDishDetails(dishId).collectAsState(initial = null)

    val ingredients = detailedDish?.ingredients?.map { it.namePl } ?: emptyList()

    val allergens = listOf<String>()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            detailedDish?.dish?.let { dish ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = dish.imageUrl,
                            contentDescription = dish.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(android.R.drawable.ic_menu_report_image),
                            error = painterResource(android.R.drawable.ic_menu_report_image)
                        )

                        SmallFloatingActionButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.padding(16.dp),
                            containerColor = Color.White.copy(alpha = 0.8f),
                            contentColor = Color.Black,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Powrót")
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dish.name,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${String.format("%.2f", dish.price / 100.0)} zł",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = orange
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Składniki",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (ingredients.isEmpty()) {
                            Text("To danie nie ma określonych składników.", color = Color.Gray)
                        } else {
                            Text(
                                text = ingredients.joinToString(", "),
                                fontSize = 15.sp,
                                color = Color.DarkGray,
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (allergens.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFEBEE)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Alergeny",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD32F2F),
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = allergens.joinToString(", "),
                                            fontSize = 14.sp,
                                            color = Color(0xFFD32F2F).copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = green)
            }
        }
    }
}