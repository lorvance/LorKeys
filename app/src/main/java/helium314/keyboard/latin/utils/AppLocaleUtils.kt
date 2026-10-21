// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

data class AppLanguageItem(
    val tag: String,
    val nativeName: String,
    val englishName: String
)

object AppLocaleUtils {
    const val PREF_APP_LANGUAGE = "pref_app_language"

    val SUPPORTED_APP_LANGUAGES = listOf(
        AppLanguageItem("", "", ""),
        AppLanguageItem("ru", "Русский", "Russian"),
        AppLanguageItem("en", "English", "English"),
        AppLanguageItem("uk", "Українська", "Ukrainian"),
        AppLanguageItem("es", "Español", "Spanish"),
        AppLanguageItem("de", "Deutsch", "German"),
        AppLanguageItem("fr", "Français", "French"),
        AppLanguageItem("it", "Italiano", "Italian"),
        AppLanguageItem("pt", "Português", "Portuguese"),
        AppLanguageItem("be", "Беларуская", "Belarusian"),
        AppLanguageItem("pl", "Polski", "Polish"),
        AppLanguageItem("tr", "Türkçe", "Turkish"),
        AppLanguageItem("kk", "Қазақ тілі", "Kazakh"),
        AppLanguageItem("vi", "Tiếng Việt", "Vietnamese"),
    )

    fun getSavedLanguageTag(context: Context): String {
        return context.prefs().getString(PREF_APP_LANGUAGE, "") ?: ""
    }

    fun getSavedLocale(context: Context): Locale {
        val tag = getSavedLanguageTag(context)
        return if (tag.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList.getDefault().get(0) ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                Locale.getDefault()
            }
        } else {
            Locale.forLanguageTag(tag)
        }
    }

    fun wrapContext(context: Context): Context {
        val tag = getSavedLanguageTag(context)
        if (tag.isEmpty()) return context
        val targetLocale = Locale.forLanguageTag(tag)
        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(targetLocale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        }
        return context.createConfigurationContext(config)
    }

    fun applyToContext(context: Context) {
        val tag = getSavedLanguageTag(context)
        if (tag.isEmpty()) return
        val targetLocale = Locale.forLanguageTag(tag)
        Locale.setDefault(targetLocale)
        try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(targetLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(targetLocale))
            }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } catch (e: Exception) {
            Log.e("AppLocaleUtils", "Error in applyToContext", e)
        }
    }

    fun setAppLanguage(context: Context, tag: String) {
        context.prefs().edit { putString(PREF_APP_LANGUAGE, tag) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
                if (localeManager != null) {
                    val localeList = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                    localeManager.applicationLocales = localeList
                }
            } catch (e: Exception) {
                Log.e("AppLocaleUtils", "Error setting applicationLocales", e)
            }
        }

        // Apply to current app process
        try {
            val targetLocale = if (tag.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    LocaleList.getDefault().get(0) ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    Locale.getDefault()
                }
            } else {
                Locale.forLanguageTag(tag)
            }
            Locale.setDefault(targetLocale)

            val app = context.applicationContext
            val config = Configuration(app.resources.configuration)
            config.setLocale(targetLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(targetLocale))
            }
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(config, app.resources.displayMetrics)
        } catch (e: Exception) {
            Log.e("AppLocaleUtils", "Error updating configuration", e)
        }
    }

    fun getDisplayNameForTag(tag: String, defaultName: String): String {
        if (tag.isEmpty()) return defaultName
        val item = SUPPORTED_APP_LANGUAGES.firstOrNull { it.tag == tag }
        return item?.nativeName ?: tag
    }
}
