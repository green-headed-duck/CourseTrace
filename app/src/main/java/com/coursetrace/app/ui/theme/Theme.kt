package com.coursetrace.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

@Composable
fun CourseTraceTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
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
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
