package com.forgeprom.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.forgeprom.studio.core.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = RoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE2FF),
    onSecondaryContainer = Color(0xFF00164E),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFC8),
    onTertiaryContainer = Color(0xFF2E2100),
    error = RedError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = Color(0xFF1C1B20),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A4455),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F4FD),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE9E1F3),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6BBFF),
    onPrimary = Color(0xFF3A0A78),
    primaryContainer = Color(0xFF52239B),
    onPrimaryContainer = Color(0xFFEEDDFF),
    secondary = Color(0xFFB8C4FF),
    onSecondary = Color(0xFF0E2364),
    secondaryContainer = Color(0xFF283C7E),
    onSecondaryContainer = Color(0xFFDDE2FF),
    tertiary = Color(0xFFFFD27A),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF5A4400),
    onTertiaryContainer = Color(0xFFFFEFC8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = Color(0xFFE8E1EE),
    surface = DarkSurface,
    onSurface = Color(0xFFE8E1EE),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCFC4DA),
    surfaceContainerLowest = Color(0xFF0E0A12),
    surfaceContainerLow = Color(0xFF1D1626),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF372D43),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

/** ألوان الحالة (نجاح/تحذير) تتبع سطوع الثيم الفعلي لا إعداد النظام. */
object ForgepromStatus {
    @Composable
    @ReadOnlyComposable
    fun success(): Color = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) SuccessLight else SuccessDark

    @Composable
    @ReadOnlyComposable
    fun successContainer(): Color =
        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) SuccessContainerLight else SuccessContainerDark

    @Composable
    @ReadOnlyComposable
    fun warning(): Color = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) WarningLight else WarningDark

    @Composable
    @ReadOnlyComposable
    fun warningContainer(): Color =
        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) WarningContainerLight else WarningContainerDark
}

@Composable
fun ForgepromTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ForgepromTypography,
        shapes = ForgepromShapes,
        content = content
    )
}
