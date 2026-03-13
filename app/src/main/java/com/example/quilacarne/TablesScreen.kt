package com.example.quilacarne

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

@Composable
fun TablesScreen() {

    val tables = listOf("Stolik 1", "Stolik 2", "Stolik 3", "Stolik 4")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "Lista stolików",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn {

            items(tables) { table ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { },
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {

                    Text(
                        text = table,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(20.dp)
                    )

                }

            }

        }

    }
}
