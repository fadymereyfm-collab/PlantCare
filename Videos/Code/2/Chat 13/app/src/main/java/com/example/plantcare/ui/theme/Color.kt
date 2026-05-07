package com.example.plantcare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.example.plantcare.R

/**
 * Sage Garden — Compose color accessors.
 *
 * All Compose code MUST go through these functions instead of writing
 * `Color(0xFF…)` literals. They read from `res/values/colors.xml` and
 * `res/values-night/colors.xml` so light/dark flips automatically and
 * a future palette change propagates everywhere.
 */
object PlantCareColors {

    // ── M3 roles ───────────────────────────────────────────────────
    val primary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_primary)
    val onPrimary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onPrimary)
    val primaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_primaryContainer)
    val onPrimaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onPrimaryContainer)

    val secondary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_secondary)
    val onSecondary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onSecondary)
    val secondaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_secondaryContainer)
    val onSecondaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onSecondaryContainer)

    val tertiary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_tertiary)
    val onTertiary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onTertiary)
    val tertiaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_tertiaryContainer)
    val onTertiaryContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onTertiaryContainer)

    val error @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_error)
    val onError @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onError)

    val background @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_background)
    val onBackground @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onBackground)

    val surface @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surface)
    val onSurface @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onSurface)
    val surfaceVariant @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceVariant)
    val onSurfaceVariant @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onSurfaceVariant)

    val surfaceContainerLowest @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceContainerLowest)
    val surfaceContainerLow @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceContainerLow)
    val surfaceContainer @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceContainer)
    val surfaceContainerHigh @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceContainerHigh)
    val surfaceContainerHighest @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_surfaceContainerHighest)

    val outline @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_outline)
    val outlineVariant @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_outlineVariant)

    val inverseSurface @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_inverseSurface)
    val inverseOnSurface @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_inverseOnSurface)
    val inversePrimary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_inversePrimary)

    // ── Semantic role aliases (legacy) ─────────────────────────────
    val onSurfaceSecondary @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_onSurfaceSecondary)
    val accent @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_accent)
    val accent2 @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_accent2)
    val success @Composable @ReadOnlyComposable get() = colorResource(R.color.pc_success)
}

// ── Compatibility aliases for older Compose call sites ─────────────
// These let existing imports keep compiling. New code should reach for
// PlantCareColors.X directly inside @Composable scope instead.
val GreenPrimary: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.primary
val GreenSecondary: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.secondary
val GreenAccent: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.success
val PinkAccent: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.error
val SoftPink: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.secondary
val NeutralBackground: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.background
val CardBackground: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.surface
val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.onSurface
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.onSurfaceVariant
val DividerColor: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.outlineVariant
val PrimaryDark: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.success
val PrimaryLight: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.secondary
val SurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.surfaceVariant
val Outline: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.outline
val Accent: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.accent2
val Success: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.success
val Error: Color
    @Composable @ReadOnlyComposable get() = PlantCareColors.error
