package com.example.quilacarne.ui.i18n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
