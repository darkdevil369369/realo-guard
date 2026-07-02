package com.realo.guard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RealoDark = darkColorScheme(
    primary = Color(0xFF2F6BFF),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFF080A0F),
    surface = Color(0xFF111520),
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF3F6FC),
    onSurface = Color(0xFFF3F6FC),
    error = Color(0xFFEF4444)
)

@Composable
fun RealoTheme(content: @Composable () -> Unit) {
    // Always dark — matches the REALO brand.
    MaterialTheme(colorScheme = RealoDark, content = content)
}
