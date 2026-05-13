package com.example.quilacarne.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.quilacarne.ui.components.QuiLaCarneHeader
import com.example.quilacarne.ui.i18n.localizedName
import com.example.quilacarne.ui.i18n.rememberAppLanguage
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
    val language = rememberAppLanguage()

    val ingredients = detailedDish?.ingredientsWithAllergens?.map {
        it.ingredient.localizedName(language)
    } ?: emptyList()

    val allergens = detailedDish?.ingredientsWithAllergens
        ?.flatMap { it.allergens }
        ?.map { it.localizedName(language) }
        ?.distinct() ?: emptyList()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            QuiLaCarneHeader(navController = navController, showBack = true)

            Box(modifier = Modifier.fillMaxSize()) {
                detailedDish?.dish?.let { dish ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp)
                    ) {
                        AsyncImage(
                            model = dish.imageUrl,
                            contentDescription = dish.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(android.R.drawable.ic_menu_report_image),
                            error = painterResource(android.R.drawable.ic_menu_report_image)
                        )

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
                                    text = "${String.format(java.util.Locale.US, "%.2f", dish.price / 100.0)} ${language.choose("zl", "PLN")}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = orange
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = language.choose("Skladniki", "Ingredients"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (ingredients.isEmpty()) {
                                Text(language.choose("To danie nie ma okreslonych skladnikow.", "This dish has no listed ingredients."), color = Color.Gray)
                            } else {
                                Text(
                                    text = ingredients.joinToString(", "),
                                    fontSize = 15.sp,
                                    color = Color.DarkGray,
                                    lineHeight = 22.sp
                                )
                            }

                            if (allergens.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(32.dp))
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
                                                text = language.choose("Alergeny", "Allergens"),
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
}
