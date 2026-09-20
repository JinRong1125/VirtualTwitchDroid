package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * VirtualTwitchDroid's shape scale — the third Material 3 subsystem, which the theme previously left unset
 * (so components fell back to M3 defaults and hand-rolled one-off `RoundedCornerShape(n.dp)` radii).
 *
 * The values follow the Material 3 default scale, so wiring this into [TwitchTheme] does not alter
 * stock M3 components; its purpose is to give the app named, consistent corner radii to replace the
 * scattered literals with `MaterialTheme.shapes.*`:
 *  - [Shapes.extraSmall] 4dp — badges / tiny chips
 *  - [Shapes.small] 8dp — cards, thumbnails, pills, previews (the app's default rounding)
 *  - [Shapes.medium] 12dp — floating mini-window, larger tiles
 *  - [Shapes.large] 16dp — tag chips, reward cards, bottom sheets
 *  - [Shapes.extraLarge] 28dp — hero surfaces
 */
val TwitchShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
