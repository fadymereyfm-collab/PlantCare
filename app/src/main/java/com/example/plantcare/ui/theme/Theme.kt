package com.example.plantcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Sage Garden — Compose theme (Material 3).
 *
 * The color scheme is built **inside** the @Composable scope from
 * `res/values/colors.xml` / `res/values-night/colors.xml`, the single
 * source of truth shared with the XML theme (Theme.PlantCareApp).
 *
 * New screens use `androidx.compose.material3.*` only. The older
 * `weekbar/` screens still import `androidx.compose.material.*` (M2), so
 * a legacy M2 MaterialTheme is provided underneath with the same tokens —
 * both generations render with identical colors, fonts and shapes until
 * those screens are migrated.
 */
@Composable
fun PlantCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = plantCareColorScheme(darkTheme),
        typography = PlantCareTypography,
        shapes = PlantCareShapes
    ) {
        androidx.compose.material.MaterialTheme(
            colors = legacyColors(darkTheme),
            typography = LegacyTypography,
            shapes = LegacyShapes,
            content = content
        )
    }
}

/**
 * M3 color scheme from the pc_* resources. `surface` is the warm page
 * background (top bars, sheets blend into the screen); cards sit on
 * `surfaceContainerLowest` (pure white in light mode).
 */
@Composable
private fun plantCareColorScheme(darkTheme: Boolean): ColorScheme {
    val c = PlantCareColors
    return if (darkTheme) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            inversePrimary = c.inversePrimary,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.background,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceTint = c.primary,
            inverseSurface = c.inverseSurface,
            inverseOnSurface = c.inverseOnSurface,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            scrim = c.scrim,
            surfaceBright = c.surfaceBright,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            surfaceContainerLow = c.surfaceContainerLow,
            surfaceContainerLowest = c.surfaceContainerLowest,
            surfaceDim = c.surfaceDim
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            inversePrimary = c.inversePrimary,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.background,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceTint = c.primary,
            inverseSurface = c.inverseSurface,
            inverseOnSurface = c.inverseOnSurface,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            scrim = c.scrim,
            surfaceBright = c.surfaceBright,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            surfaceContainerLow = c.surfaceContainerLow,
            surfaceContainerLowest = c.surfaceContainerLowest,
            surfaceDim = c.surfaceDim
        )
    }
}

/** M2 palette for the legacy `weekbar/` screens (same tokens). */
@Composable
private fun legacyColors(darkTheme: Boolean): androidx.compose.material.Colors {
    val c = PlantCareColors
    return if (darkTheme) {
        androidx.compose.material.darkColors(
            primary = c.primary,
            primaryVariant = c.primaryContainer,
            secondary = c.tertiary,
            secondaryVariant = c.tertiaryContainer,
            background = c.background,
            surface = c.surface,
            error = c.error,
            onPrimary = c.onPrimary,
            onSecondary = c.onTertiary,
            onBackground = c.onBackground,
            onSurface = c.onSurface,
            onError = c.onError
        )
    } else {
        androidx.compose.material.lightColors(
            primary = c.primary,
            primaryVariant = c.primaryContainer,
            secondary = c.tertiary,
            secondaryVariant = c.tertiaryContainer,
            background = c.background,
            surface = c.surface,
            error = c.error,
            onPrimary = c.onPrimary,
            onSecondary = c.onTertiary,
            onBackground = c.onBackground,
            onSurface = c.onSurface,
            onError = c.onError
        )
    }
}
