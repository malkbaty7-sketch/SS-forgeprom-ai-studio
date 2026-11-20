package com.forgeprom.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.forgeprom.studio.core.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    secondary = RoyalBlue,
    tertiary = Color(0xFF745700),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9BAFF),
    onPrimary = Color(0xFF3D135F),
    secondary = Color(0xFFBCC5FF),
    onSecondary = Color(0xFF202A61),
    tertiary = Color(0xFFF4CF72),
    onTertiary = Color(0xFF3D2E00)
)

@Composable
fun ForgepromTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
