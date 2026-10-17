package com.example.plantcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

/**
 * Sage Garden — Compose Material theme.
 *
 * Both light and dark palettes are derived **inside** the @Composable
 * scope so they pull live values from `res/values/colors.xml` and
 * `res/values-night/colors.xml`. This is the single source of truth
 * shared with the XML side: changing a `pc_*` color resource updates
 * Compose immediately, with no per-file Compose edits required.
 *
 * Note: kept on Material (M2) Compose to avoid touching ~50 Compose
 * widget call sites that currently import `androidx.compose.material.*`.
 * The same XML tokens additionally feed Material Design Components
 * (M2) used by XML layouts via Theme.PlantCareApp in themes.xml.
 */
@Composable
fun PlantCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors: Colors = if (darkTheme) {
        darkColors(
            primary = PlantCareColors.primary,
            primaryVariant = PlantCareColors.primaryContainer,
            secondary = PlantCareColors.tertiary,
            secondaryVariant = PlantCareColors.tertiaryContainer,
            background = PlantCareColors.background,
            surface = PlantCareColors.surface,
            error = PlantCareColors.error,
            onPrimary = PlantCareColors.onPrimary,
            onSecondary = PlantCareColors.onTertiary,
            onBackground = PlantCareColors.onBackground,
            onSurface = PlantCareColors.onSurface,
            onError = PlantCareColors.onError
        )
    } else {
        lightColors(
            primary = PlantCareColors.primary,
            primaryVariant = PlantCareColors.primaryContainer,
            secondary = PlantCareColors.tertiary,
            secondaryVariant = PlantCareColors.tertiaryContainer,
            background = PlantCareColors.background,
            surface = PlantCareColors.surface,
            error = PlantCareColors.error,
            onPrimary = PlantCareColors.onPrimary,
            onSecondary = PlantCareColors.onTertiary,
            onBackground = PlantCareColors.onBackground,
            onSurface = PlantCareColors.onSurface,
            onError = PlantCareColors.onError
        )
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
