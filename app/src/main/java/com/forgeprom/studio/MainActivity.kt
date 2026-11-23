package com.forgeprom.studio

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.forgeprom.studio.core.settings.AppSettings
import com.forgeprom.studio.core.settings.LanguageStore
import com.forgeprom.studio.ui.screens.AuthRoot
import com.forgeprom.studio.ui.theme.ForgepromTheme

class MainActivity : ComponentActivity() {

    /** يطبّق لغة الواجهة المختارة على الموارد قبل بناء الواجهة. */
    override fun attachBaseContext(newBase: Context) {
        val language = LanguageStore(newBase).current()
        // نفرض اللغة المختارة دائماً (لا نعتمد على لغة النظام) كي لا تسرّب
        // موارد values-en إلى مستخدم اختار العربية على جهاز إنجليزي.
        val locale = java.util.Locale.forLanguageTag(language)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ForgepromApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(
                initial = AppSettings()
            )
            ForgepromTheme(themeMode = settings.themeMode) {
                // الاتجاه يتبع لغة الواجهة: RTL للعربية وLTR لغيرها.
                val configuration = LocalConfiguration.current
                val isRtl = configuration.locales[0].language == LanguageStore.AR
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    AuthRoot(container = container)
                }
            }
        }
    }
}
