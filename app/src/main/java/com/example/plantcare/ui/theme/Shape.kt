package com.example.plantcare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Shape scale — same values as @dimen/radius_* (res/values/dimens.xml)
 * and the ShapeAppearance.PlantCare.Corner.* styles used by XML widgets.
 */
val PlantCareShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(PlantCareRadius.xs),
    small = RoundedCornerShape(PlantCareRadius.sm),
    medium = RoundedCornerShape(PlantCareRadius.md),
    large = RoundedCornerShape(PlantCareRadius.lg),
    extraLarge = RoundedCornerShape(PlantCareRadius.xl)
)

/** Legacy Material (M2) shapes for the older `weekbar/` Compose screens. */
val LegacyShapes = androidx.compose.material.Shapes(
    small = RoundedCornerShape(PlantCareRadius.sm),
    medium = RoundedCornerShape(PlantCareRadius.lg),
    large = RoundedCornerShape(PlantCareRadius.xl)
)
