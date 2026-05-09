package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.MenuViewModel
import java.util.UUID

@Composable
fun MenuScreen(
    navController: NavController,
    viewModel: MenuViewModel = viewModel()
) {
    val dishes by viewModel.dishes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val allergens by viewModel.allergens.collectAsState()
    val selectedAllergenIds by viewModel.selectedAllergenIds.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredDishes = dishes.filter { dish ->
        dish.name.contains(searchQuery, ignoreCase = true)
    }.sortedBy { it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Szukaj dania...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = green,
                unfocusedBorderColor = lightGray,
                cursorColor = green
            ),
            singleLine = true
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    name = "Wszystkie",
                    isSelected = selectedCategoryId == null,
                    onClick = { viewModel.selectCategory(null) }
                )
            }
            items(categories) { category ->
                CategoryChip(
                    name = category.namePl,
                    isSelected = selectedCategoryId == category.id,
                    onClick = { viewModel.selectCategory(category.id) }
                )
            }
        }

        if (allergens.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allergens) { allergen ->
                    AllergenChip(
                        allergen = allergen,
                        isSelected = allergen.id in selectedAllergenIds,
                        onClick = { viewModel.toggleAllergen(allergen.id) }
                    )
                }
            }
        }

        if (filteredDishes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Brak dań", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDishes) { dish ->
                    DishListItem(dish = dish) {
                        navController.navigate("dish_detail/${dish.id}")
                    }
                }
            }
        }
    }
}

@Composable
fun AllergenChip(allergen: AllergenEntity, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFFD84315) else lightGray,
        contentColor = if (isSelected) Color.White else Color.Black
    ) {
        Text(
            text = allergen.namePl,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) green else lightGray,
        contentColor = if (isSelected) Color.White else Color.Black
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DishListItem(dish: DishEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = dish.imageUrl,
                contentDescription = dish.name,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(lightGray),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(android.R.drawable.ic_menu_report_image),
                error = painterResource(android.R.drawable.ic_menu_report_image)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${String.format("%.2f", dish.price / 100.0)} zł",
                    fontSize = 16.sp,
                    color = orange,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
