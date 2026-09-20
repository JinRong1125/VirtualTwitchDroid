package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Guards the design tokens against drift so the theme stays cohesive: the [Spacing], [IconSize], and
 * [Sizing] scales must stay ordered, [IconSize.md] must stay the 24dp Material baseline, and
 * [TwitchShapes] must stay on the Material 3 corner scale that screens reference via
 * `MaterialTheme.shapes.*`.
 */
class ThemeTokensTest {

    @Test
    fun spacingScale_isStrictlyIncreasing() {
        val steps = listOf(
            Spacing.none,
            Spacing.xxs,
            Spacing.xs,
            Spacing.sm,
            Spacing.md,
            Spacing.lg,
            Spacing.xl,
            Spacing.xxl,
        )
        steps.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "spacing steps must strictly increase: $smaller !< $larger")
        }
    }

    @Test
    fun spacingBaseUnit_isEightDp() {
        assertEquals(8.dp, Spacing.sm, "the base grid unit (Spacing.sm) must be 8dp")
    }

    @Test
    fun iconSizeScale_isStrictlyIncreasing() {
        val steps = listOf(
            IconSize.xxs,
            IconSize.xs,
            IconSize.sm,
            IconSize.md,
            IconSize.lg,
            IconSize.xl,
            IconSize.xxl,
        )
        steps.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "icon sizes must strictly increase: $smaller !< $larger")
        }
    }

    @Test
    fun iconSizeBaseline_isMaterial24Dp() {
        assertEquals(24.dp, IconSize.md, "the default icon size (IconSize.md) must be the 24dp Material baseline")
    }

    @Test
    fun sizingScale_isStrictlyIncreasing() {
        val steps = listOf(Sizing.sm, Sizing.md, Sizing.lg, Sizing.xl, Sizing.xxl)
        steps.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "component sizes must strictly increase: $smaller !< $larger")
        }
    }

    @Test
    fun elevationScale_isStrictlyIncreasing() {
        listOf(Elevation.none, Elevation.low, Elevation.high).zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "elevation steps must strictly increase: $smaller !< $larger")
        }
    }

    @Test
    fun motionScale_isStrictlyIncreasing() {
        listOf(Motion.short, Motion.medium, Motion.long).zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "motion durations must strictly increase: $smaller !< $larger")
        }
    }

    @Test
    fun shapes_followMaterial3Scale() {
        assertEquals(RoundedCornerShape(4.dp), TwitchShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), TwitchShapes.small)
        assertEquals(RoundedCornerShape(12.dp), TwitchShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), TwitchShapes.large)
        assertEquals(RoundedCornerShape(28.dp), TwitchShapes.extraLarge)
    }

    @Test
    fun typography_scaleSizesAndWeights() {
        assertEquals(28.sp, TwitchTypography.headlineMedium.fontSize)
        assertEquals(FontWeight.ExtraBold, TwitchTypography.headlineMedium.fontWeight)
        assertEquals(22.sp, TwitchTypography.titleLarge.fontSize)
        assertEquals(14.sp, TwitchTypography.titleSmall.fontSize)
        assertEquals(14.sp, TwitchTypography.bodyMedium.fontSize)
        assertEquals(12.sp, TwitchTypography.bodySmall.fontSize)
        assertEquals(11.sp, TwitchTypography.labelSmall.fontSize)
    }

    @Test
    fun bodyAndLabelText_stayLegibleForModernUi() {
        // No running/caption text below 11sp — the modern-UI floor this pass enforces.
        listOf(
            TwitchTypography.bodyLarge,
            TwitchTypography.bodyMedium,
            TwitchTypography.bodySmall,
            TwitchTypography.labelLarge,
            TwitchTypography.labelMedium,
            TwitchTypography.labelSmall,
        ).forEach { style ->
            assertTrue(style.fontSize.value >= 11f, "text style too small for modern UI: ${style.fontSize}")
        }
    }
}
