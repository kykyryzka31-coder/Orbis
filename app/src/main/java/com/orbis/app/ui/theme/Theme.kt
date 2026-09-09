package com.orbis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrbisColors = darkColorScheme(
    primary = Color(0xFFB9FF66),
    onPrimary = Color(0xFF102000),
    surface = Color(0xFF15191C),
    surfaceContainer = Color(0xE61B2024),
    onSurface = Color(0xFFF1F4F5),
    onSurfaceVariant = Color(0xFFB8C0C5),
    outline = Color(0xFF59636A),
)

@Composable
fun OrbisTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSystemTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = OrbisColors,
        content = content,
    )
}
