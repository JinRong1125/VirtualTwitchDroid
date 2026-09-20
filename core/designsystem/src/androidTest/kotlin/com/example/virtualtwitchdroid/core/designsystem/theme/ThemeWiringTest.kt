package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test

class ThemeWiringTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * [TwitchTheme] must install [TwitchShapes] as the Material 3 shape subsystem so components can
     * rely on `MaterialTheme.shapes.*`. Asserting identity (not just equality) fails if the theme
     * stops passing `shapes = TwitchShapes` and falls back to the default (structurally similar) set.
     */
    @Test
    fun twitchTheme_installsTwitchShapes() {
        lateinit var shapes: Shapes
        composeTestRule.setContent {
            TwitchTheme { shapes = MaterialTheme.shapes }
        }
        assertSame(TwitchShapes, shapes)
    }
}
