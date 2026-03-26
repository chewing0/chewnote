package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Sand,
    secondary = Mist,
    tertiary = Copper,
    background = Charcoal,
    surface = Color(0xFF29313A),
    onPrimary = Charcoal,
    onSecondary = Charcoal,
    onTertiary = Charcoal,
    onBackground = Paper,
    onSurface = Paper
)

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    secondary = Mist,
    tertiary = Copper,
    background = Paper,
    surface = Color(0xFFFFFAF2),
    onPrimary = Paper,
    onSecondary = Ink,
    onTertiary = Paper,
    onBackground = Ink,
    onSurface = Ink
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}