package com.ritvik.passvault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The user's choice in Settings -> Appearance. The key is what is stored, so never rename one. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

// A fixed brand palette (no wallpaper-based dynamic colours) so the app looks the same on every phone.
// Every role Material 3 components read is set explicitly: anything left out falls back to Material's default
// purple-tinted baseline, which made dialogs and menus look lavender next to this blue-grey palette.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001A5C),
    inversePrimary = Color(0xFFB4C5FF),
    secondary = Color(0xFF5B6478),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE3F0),
    onSecondaryContainer = Color(0xFF171C2B),
    tertiary = Color(0xFF6B4EA0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF250059),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF1B1D24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D24),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF5A5F6E),
    surfaceTint = Color(0xFF2F5BEA),
    inverseSurface = Color(0xFF2F3038),
    inverseOnSurface = Color(0xFFF1F0F7),
    surfaceDim = Color(0xFFDCDDE4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF1F2F8),
    surfaceContainerHigh = Color(0xFFECEDF4),
    surfaceContainerHighest = Color(0xFFE6E8F0),
    outline = Color(0xFF7A7F90),
    outlineVariant = Color(0xFFD9DCE6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9CF0),
    onPrimary = Color(0xFF0D1B45),
    primaryContainer = Color(0xFF222B47),
    onPrimaryContainer = Color(0xFF9DB6F7),
    inversePrimary = Color(0xFF2F5BEA),
    secondary = Color(0xFFB4BAD0),
    onSecondary = Color(0xFF232838),
    secondaryContainer = Color(0xFF2A2E3C),
    onSecondaryContainer = Color(0xFFDCE2F8),
    tertiary = Color(0xFFD3BBFF),
    onTertiary = Color(0xFF3B1E70),
    tertiaryContainer = Color(0xFF523787),
    onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFE9EAEE),
    surface = Color(0xFF16171B),
    onSurface = Color(0xFFE9EAEE),
    surfaceVariant = Color(0xFF22242A),
    onSurfaceVariant = Color(0xFF8D919E),
    surfaceTint = Color(0xFF7C9CF0),
    inverseSurface = Color(0xFFE9EAEE),
    inverseOnSurface = Color(0xFF2A2B31),
    surfaceDim = Color(0xFF0E0F12),
    surfaceBright = Color(0xFF2E3037),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF131418),
    surfaceContainer = Color(0xFF1A1B20),
    surfaceContainerHigh = Color(0xFF212328),
    surfaceContainerHighest = Color(0xFF2A2C32),
    outline = Color(0xFF6F7380),
    outlineVariant = Color(0xFF26282E),
    error = Color(0xFFFF6B57),
    onError = Color(0xFF3A0A04),
    errorContainer = Color(0xFF3A1D1A),
    onErrorContainer = Color(0xFFFFB4A8),
    scrim = Color(0xFF000000),
)

/** Green for "good" and amber for "careful", each tuned to stay readable on its own background. */
@Immutable
class StatusColors(val success: Color, val warning: Color)

private val LightStatus = StatusColors(success = Color(0xFF1E7B45), warning = Color(0xFF8A5200))
private val DarkStatus = StatusColors(success = Color(0xFF6FD39B), warning = Color(0xFFFFC266))
private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Use these instead of hard-coded greens and ambers: they change with light and dark mode. */
val ColorScheme.success: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.success

val ColorScheme.warning: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.warning

/**
 * Every text style a little smaller than Material's defaults, so more fits on a screen and nothing looks oversized.
 * One factor in one place: change it here to make the whole app larger or smaller.
 */
private const val TEXT_SCALE = 0.92f

private fun TextStyle.scaled() = copy(fontSize = fontSize * TEXT_SCALE, lineHeight = lineHeight * TEXT_SCALE)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.scaled(), displayMedium = t.displayMedium.scaled(), displaySmall = t.displaySmall.scaled(),
        headlineLarge = t.headlineLarge.scaled(), headlineMedium = t.headlineMedium.scaled(), headlineSmall = t.headlineSmall.scaled(),
        titleLarge = t.titleLarge.scaled(), titleMedium = t.titleMedium.scaled(), titleSmall = t.titleSmall.scaled(),
        bodyLarge = t.bodyLarge.scaled(), bodyMedium = t.bodyMedium.scaled(), bodySmall = t.bodySmall.scaled(),
        labelLarge = t.labelLarge.scaled(), labelMedium = t.labelMedium.scaled(), labelSmall = t.labelSmall.scaled(),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Whether the app is drawn dark, given the user's choice and the phone's own setting. */
@Composable
@ReadOnlyComposable
fun isAppDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PassVaultTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isAppDark(mode)
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
