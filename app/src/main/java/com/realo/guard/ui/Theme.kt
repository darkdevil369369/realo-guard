package com.realo.guard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RealoDark = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0B0D17),
    surface = Color(0xFF141728),
    onPrimary = Color(0xFF08101F),
    onBackground = Color(0xFFEEF1FF),
    onSurface = Color(0xFFEEF1FF),
    error = Color(0xFFFF4D6D)
)

@Composable
fun RealoTheme(content: @Composable () -> Unit) {
    // Always dark — matches the REALO brand.
    MaterialTheme(colorScheme = RealoDark, content = content)
}
