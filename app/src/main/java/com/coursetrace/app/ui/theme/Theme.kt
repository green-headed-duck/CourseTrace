package com.coursetrace.app.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.coursetrace.app.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E1FF),
    onPrimaryContainer = Color(0xFF17105C),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFB8F2E9),
    tertiary = Color(0xFF9A3412),
    background = Color(0xFFF8F9FE),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFE7E7F0),
    outline = Color(0xFF777681),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C0FF),
    onPrimary = Color(0xFF282078),
    primaryContainer = Color(0xFF38308E),
    secondary = Color(0xFF83D5CB),
    secondaryContainer = Color(0xFF005049),
    tertiary = Color(0xFFFFB69D),
    background = Color(0xFF11131A),
    surface = Color(0xFF181A22),
    surfaceVariant = Color(0xFF30313B),
    outline = Color(0xFF918F9B),
)

private fun seedTone(
    seedArgb: Long,
    hueShift: Float = 0f,
    saturationMultiplier: Float = 1f,
    value: Float,
): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seedArgb.toInt(), hsv)
    hsv[0] = (hsv[0] + hueShift + 360f) % 360f
    hsv[1] = (maxOf(hsv[1], 0.45f) * saturationMultiplier).coerceIn(0.08f, 0.95f)
    hsv[2] = value.coerceIn(0.08f, 0.98f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun readableOn(color: Color): Color = if (color.luminance() > 0.46f) Color.Black else Color.White

private fun customLightColors(seedArgb: Long) = run {
    val primary = seedTone(seedArgb, value = 0.68f)
    val secondary = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.78f, value = 0.62f)
    val tertiary = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.82f, value = 0.68f)
    LightColors.copy(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = seedTone(seedArgb, saturationMultiplier = 0.28f, value = 0.97f),
        onPrimaryContainer = seedTone(seedArgb, saturationMultiplier = 0.92f, value = 0.23f),
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.25f, value = 0.95f),
        onSecondaryContainer = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.85f, value = 0.22f),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        tertiaryContainer = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.24f, value = 0.95f),
        onTertiaryContainer = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.9f, value = 0.22f),
        surfaceTint = primary,
    )
}

private fun customDarkColors(seedArgb: Long) = run {
    val primary = seedTone(seedArgb, saturationMultiplier = 0.62f, value = 0.94f)
    val secondary = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.5f, value = 0.9f)
    val tertiary = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.52f, value = 0.92f)
    DarkColors.copy(
        primary = primary,
        onPrimary = seedTone(seedArgb, saturationMultiplier = 0.9f, value = 0.18f),
        primaryContainer = seedTone(seedArgb, saturationMultiplier = 0.82f, value = 0.4f),
        onPrimaryContainer = seedTone(seedArgb, saturationMultiplier = 0.38f, value = 0.96f),
        secondary = secondary,
        onSecondary = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.85f, value = 0.16f),
        secondaryContainer = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.72f, value = 0.36f),
        onSecondaryContainer = seedTone(seedArgb, hueShift = 35f, saturationMultiplier = 0.3f, value = 0.95f),
        tertiary = tertiary,
        onTertiary = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.85f, value = 0.17f),
        tertiaryContainer = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.72f, value = 0.37f),
        onTertiaryContainer = seedTone(seedArgb, hueShift = -50f, saturationMultiplier = 0.3f, value = 0.95f),
        surfaceTint = primary,
    )
}

@Composable
fun CourseTraceTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    seedArgb: Long?,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        seedArgb != null && dark -> customDarkColors(seedArgb)
        seedArgb != null -> customLightColors(seedArgb)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
