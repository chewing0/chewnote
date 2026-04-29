package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
    primary = AccentVermilion,
    secondary = AccentMoss,
    tertiary = Charcoal,
    background = CanvasIvory,
    surface = PaperCard,
    onPrimary = CanvasIvory,
    onSecondary = CanvasIvory,
    onTertiary = CanvasIvory,
    onBackground = InkDeep,
    onSurface = InkDeep,
    outline = LineSoft,
    outlineVariant = Color(0xFFE8DDCB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
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
        shapes = AppShapes,
        content = content
    )
}
