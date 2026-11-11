package com.githunt.android.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

data class TranslationLanguage(val code: String, val label: String)

/** Same list as the website's LanguageProvider. */
object TranslationLanguages {
    val all: List<TranslationLanguage> = listOf(
        TranslationLanguage("off", "Off (show original)"),
        TranslationLanguage("en", "English"),
        TranslationLanguage("es", "Spanish"),
        TranslationLanguage("fr", "French"),
        TranslationLanguage("de", "German"),
        TranslationLanguage("pt", "Portuguese"),
        TranslationLanguage("it", "Italian"),
        TranslationLanguage("ru", "Russian"),
        TranslationLanguage("ja", "Japanese"),
        TranslationLanguage("ko", "Korean"),
        TranslationLanguage("zh-CN", "Chinese (Simplified)"),
        TranslationLanguage("zh-TW", "Chinese (Traditional)"),
        TranslationLanguage("ar", "Arabic"),
        TranslationLanguage("hi", "Hindi"),
        TranslationLanguage("vi", "Vietnamese"),
        TranslationLanguage("id", "Indonesian"),
        TranslationLanguage("tr", "Turkish"),
        TranslationLanguage("nl", "Dutch"),
        TranslationLanguage("pl", "Polish"),
        TranslationLanguage("th", "Thai"),
    )

    fun label(code: String): String = all.firstOrNull { it.code == code }?.label ?: "English"
}

/** Device-only settings (the website keeps these in localStorage). */
class AppPreferences(context: Context, private val activityLog: AppActivityLog) {
    private val store = context.getSharedPreferences("githunt_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private val _developerOptions = MutableStateFlow(store.getBoolean(KEY_DEVELOPER_OPTIONS, false))
    val developerOptions: StateFlow<Boolean> = _developerOptions.asStateFlow()

    fun setDeveloperOptions(enabled: Boolean) {
        if (_developerOptions.value == enabled) return
        store.edit().putBoolean(KEY_DEVELOPER_OPTIONS, enabled).apply()
        _developerOptions.value = enabled
        activityLog.record("Settings", "Developer options ${if (enabled) "enabled" else "disabled"}", status = "success")
    }

    fun setTheme(mode: ThemeMode) {
        if (_theme.value != mode) activityLog.record("Settings", "Theme changed", mode.label, "success")
        store.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setLanguage(code: String) {
        if (TranslationLanguages.all.none { it.code == code }) return
        if (_language.value != code) activityLog.record("Settings", "Translation language changed", TranslationLanguages.label(code), "success")
        store.edit().putString(KEY_LANGUAGE, code).apply()
        _language.value = code
    }

    private fun readTheme(): ThemeMode {
        val saved = store.getString(KEY_THEME, null)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    private fun readLanguage(): String {
        val saved = store.getString(KEY_LANGUAGE, null)
        return if (saved != null && TranslationLanguages.all.any { it.code == saved }) saved else "en"
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "gh-language"
        const val KEY_DEVELOPER_OPTIONS = "developer_options_enabled"
    }
}
