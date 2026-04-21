package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CanvasWarm,
    secondary = AccentMoss,
    tertiary = AccentVermilion,
    background = Charcoal,
    surface = Color(0xFF2A313D),
    onPrimary = Charcoal,
    onSecondary = CanvasIvory,
    onTertiary = CanvasIvory,
    onBackground = CanvasIvory,
    onSurface = CanvasIvory
)

private val LightColorScheme = lightColorScheme(
    primary = InkDeep,
    secondary = AccentMoss,
    tertiary = AccentVermilion,
    background = CanvasIvory,
    surface = PaperCard,
    onPrimary = CanvasIvory,
    onSecondary = CanvasIvory,
    onTertiary = CanvasIvory,
    onBackground = InkDeep,
    onSurface = InkDeep,
    outline = LineSoft
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