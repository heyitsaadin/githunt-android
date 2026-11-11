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

/**
 * How translations are fetched. Both exist so they can be compared side by side; [statsKey]
 * matches the keys in TranslateStats.
 */
enum class TranslationEngine(val label: String, val detail: String, val statsKey: String) {
    Server(
        label = "Website (current)",
        detail = "Through your site's /api/translate: Google, then MyMemory.",
        statsKey = "server",
    ),
    Direct(
        label = "Direct Google + on-device",
        detail = "The phone asks Google itself. If that fails, ML Kit translates on the phone.",
        statsKey = "direct",
    ),
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
class AppPreferences(context: Context) {
    private val store = context.getSharedPreferences("githunt_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private val _engine = MutableStateFlow(readEngine())
    val engine: StateFlow<TranslationEngine> = _engine.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        store.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setLanguage(code: String) {
        if (TranslationLanguages.all.none { it.code == code }) return
        store.edit().putString(KEY_LANGUAGE, code).apply()
        _language.value = code
    }

    fun setEngine(engine: TranslationEngine) {
        store.edit().putString(KEY_ENGINE, engine.name).apply()
        _engine.value = engine
    }

    private fun readTheme(): ThemeMode {
        val saved = store.getString(KEY_THEME, null)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    private fun readLanguage(): String {
        val saved = store.getString(KEY_LANGUAGE, null)
        return if (saved != null && TranslationLanguages.all.any { it.code == saved }) saved else "en"
    }

    private fun readEngine(): TranslationEngine {
        val saved = store.getString(KEY_ENGINE, null)
        return TranslationEngine.values().firstOrNull { it.name == saved } ?: TranslationEngine.Server
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "gh-language"
        const val KEY_ENGINE = "translation_engine"
    }
}
