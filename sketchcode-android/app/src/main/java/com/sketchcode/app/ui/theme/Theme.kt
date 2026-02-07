package com.sketchcode.app.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0E639C),
    secondary = Color(0xFF1177BB),
    tertiary = Color(0xFF22C55E),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF252526),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFD4D4D4),
    onSurface = Color(0xFFD4D4D4),
    error = Color(0xFFEF4444)
)

@Composable
fun SketchCodeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF252526).toArgb()
            window.navigationBarColor = Color(0xFF252526).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
