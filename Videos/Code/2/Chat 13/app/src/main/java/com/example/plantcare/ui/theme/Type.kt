package com.example.plantcare.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Sage Garden — Compose typography.
 *
 * Colors are intentionally NOT set here so each Text inherits the
 * appropriate `onSurface` / `onPrimary` from the surrounding
 * MaterialTheme + Surface, which flips correctly with dark mode.
 * If a specific surface needs a custom color, set it on the Text
 * directly via `color = …`.
 */
val Typography = Typography(
    h5 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp
    ),
    body1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp
    )
)
