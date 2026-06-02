package com.example.quilacarne.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity

enum class AppLanguage(val code: String) {
    Polish("pl"),
    English("en");

    fun choose(pl: String, en: String): String = when (this) {
        Polish -> pl
        English -> en
    }
}

@Composable
fun rememberAppLanguage(): AppLanguage {
    val context = LocalContext.current

    LaunchedEffect(context) {
        AppLanguageStore.init(context.applicationContext)
    }

    val language by AppLanguageStore.language.collectAsState()
    return language
}

fun DishCategoryEntity.localizedName(language: AppLanguage): String {
    return language.choose(namePl, nameEn).ifBlank { namePl }
}

fun AllergenEntity.localizedName(language: AppLanguage): String {
    return language.choose(namePl, nameEn).ifBlank { namePl }
}

fun IngredientEntity.localizedName(language: AppLanguage): String {
    return language.choose(namePl, nameEn).ifBlank { namePl }
}

fun TableStatusEntity.localizedName(language: AppLanguage): String {
    return language.choose(namePl, nameEn).ifBlank { namePl }
}
