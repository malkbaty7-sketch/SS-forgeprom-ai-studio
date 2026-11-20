package com.forgeprom.studio.core.settings

import android.content.Context

/**
 * لغة واجهة التطبيق. العربية افتراضية؛ الإنجليزية اختيارية وتُطبَّق عبر
 * إعادة إنشاء النشاط بسياق مُحلّى (انظر MainActivity.attachBaseContext).
 */
class LanguageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): String = prefs.getString(KEY, AR)?.takeIf { it == AR || it == EN } ?: AR

    fun set(language: String) {
        if (language != AR && language != EN) return
        prefs.edit().putString(KEY, language).apply()
    }

    companion object {
        const val AR = "ar"
        const val EN = "en"
        private const val PREFS = "ui_language"
        private const val KEY = "lang"
    }
}
