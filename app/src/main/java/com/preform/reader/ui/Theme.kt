package com.preform.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.preform.reader.settings.AppTheme

// The warm, paper-and-ink palette the app has used since the first prototype.
private val Dark = darkColorScheme(
    primary = Color(0xFFD6A15C),
    onPrimary = Color(0xFF1E140A),
    secondary = Color(0xFF9C9083),
    background = Color(0xFF12100E),
    onBackground = Color(0xFFDDD3C6),
    surface = Color(0xFF12100E),
    onSurface = Color(0xFFDDD3C6),
    surfaceVariant = Color(0xFF221E1A),
    onSurfaceVariant = Color(0xFF9C9083),
    surfaceContainer = Color(0xFF1A1714),
    surfaceContainerHigh = Color(0xFF221E1A),
    surfaceContainerHighest = Color(0xFF2A2520),
    outline = Color(0xFF4A423A),
    outlineVariant = Color(0xFF2E2924),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A22),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6E6257),
    background = Color(0xFFF7F3EE),
    onBackground = Color(0xFF1E1A16),
    surface = Color(0xFFF7F3EE),
    onSurface = Color(0xFF1E1A16),
    surfaceVariant = Color(0xFFEDE6DD),
    onSurfaceVariant = Color(0xFF6E6257),
    surfaceContainer = Color(0xFFF1EBE3),
    surfaceContainerHigh = Color(0xFFEBE4DA),
    surfaceContainerHighest = Color(0xFFE4DCD1),
    outline = Color(0xFFB5A99B),
    outlineVariant = Color(0xFFDCD2C6),
)

@Composable
fun PreformTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
