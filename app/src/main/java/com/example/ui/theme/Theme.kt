package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.launcher.model.LauncherFontFamily
import com.example.launcher.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PixelBlueDark,
    onPrimary = PixelOnBlueDark,
    primaryContainer = PixelBlueContainerDark,
    onPrimaryContainer = PixelOnBlueContainerDark,
    secondary = PixelSecondaryDark,
    onSecondary = PixelOnSecondaryDark,
    secondaryContainer = PixelSecondaryContainerDark,
    onSecondaryContainer = PixelOnSecondaryContainerDark,
    tertiary = PixelTertiaryDark,
    onTertiary = PixelOnTertiaryDark,
    tertiaryContainer = PixelTertiaryContainerDark,
    onTertiaryContainer = PixelOnTertiaryContainerDark,
    background = PixelSurfaceDark,
    surface = PixelSurfaceDark,
    surfaceContainer = PixelSurfaceContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = PixelBlueLight,
    onPrimary = PixelOnBlueLight,
    primaryContainer = PixelBlueContainerLight,
    onPrimaryContainer = PixelOnBlueContainerLight,
    secondary = PixelSecondaryLight,
    onSecondary = PixelOnSecondaryLight,
    secondaryContainer = PixelSecondaryContainerLight,
    onSecondaryContainer = PixelOnSecondaryContainerLight,
    tertiary = PixelTertiaryLight,
    onTertiary = PixelOnTertiaryLight,
    tertiaryContainer = PixelTertiaryContainerLight,
    onTertiaryContainer = PixelOnTertiaryContainerLight,
    background = PixelSurfaceLight,
    surface = PixelSurfaceLight,
    surfaceContainer = PixelSurfaceContainerLight
)

fun parseHexColorSafe(hex: String, fallback: Color = PixelBlueDark): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(fallback)
}

@Composable
fun PixelHomeTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColor: Boolean = true,
    accentColorHex: String = "#7AB8FF",
    fontFamily: LauncherFontFamily = LauncherFontFamily.ROBOTO_FLEX,
    cornerRadiusScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (!dynamicColor) {
        val customAccent = parseHexColorSafe(accentColorHex, baseScheme.primary)
        baseScheme.copy(
            primary = customAccent,
            primaryContainer = customAccent.copy(alpha = if (darkTheme) 0.30f else 0.22f)
        )
    } else {
        baseScheme
    }

    val scale = cornerRadiusScale.coerceIn(0.5f, 1.5f)
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((8 * scale).dp),
        small = RoundedCornerShape((12 * scale).dp),
        medium = RoundedCornerShape((20 * scale).dp),
        large = RoundedCornerShape((28 * scale).dp),
        extraLarge = RoundedCornerShape((36 * scale).dp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildLauncherTypography(fontFamily),
        shapes = shapes,
        content = content
    )
}
