package com.eeter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFF05C3DE),
    secondary = Color(0xFF05C3DE),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF1A1A1E),
)

@Composable
fun EeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
