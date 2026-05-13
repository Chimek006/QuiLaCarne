package com.example.quilacarne.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppLanguage(val code: String) {
    Polish("pl"),
    English("en");

    fun choose(pl: String, en: String): String = when (this) {
        Polish -> pl
        English -> en
    }
}

object AppLanguageStore {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "language"

    private val _language = MutableStateFlow(AppLanguage.Polish)
    val language: StateFlow<AppLanguage> = _language

    fun init(context: Context) {
        val code = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.Polish.code)

        _language.value = fromCode(code)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
        _language.value = language
    }

    private fun fromCode(code: String?): AppLanguage {
        return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.Polish
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
