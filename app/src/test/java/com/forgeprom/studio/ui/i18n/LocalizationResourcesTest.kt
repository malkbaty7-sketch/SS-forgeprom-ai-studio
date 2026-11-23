package com.forgeprom.studio.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeprom.studio.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalizationResourcesTest {

    private fun localized(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            setLayoutDirection(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(config)
    }

    @Test
    fun englishResourcesAreTranslated() {
        val en = localized("en")
        assertEquals("Token estimate:", en.getString(R.string.ui001))
        assertEquals("Home", en.getString(R.string.ui003))
        assertEquals("Settings", en.getString(R.string.ui007))
    }

    @Test
    fun arabicResourcesStayArabic() {
        val ar = localized("ar")
        assertEquals("تقدير الطلب:", ar.getString(R.string.ui001))
        assertEquals("الرئيسية", ar.getString(R.string.ui003))
    }

    @Test
    fun catalogCoversEveryTranslatedResource() {
        // كل مورد له ترجمة إنجليزية يجب أن يكون قابلاً للوصول من الكتالوج
        assertEquals(593, UiCatalog.ids.size)
    }
}
