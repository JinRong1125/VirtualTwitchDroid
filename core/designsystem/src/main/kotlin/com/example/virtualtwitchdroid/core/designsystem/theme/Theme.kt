package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TwitchPurple,
    onPrimary = Color.White,
    secondary = TwitchPurple,
    onSecondary = Color.White,
    error = LiveRed,
    onError = Color.White,
    background = DarkAppBackground,
    onBackground = TextHighEmphasisDark,
    surface = DarkBarsBackground,
    onSurface = TextHighEmphasisDark,
    surfaceVariant = DarkInputBackground,
    onSurfaceVariant = TextLowEmphasis,
    outline = DarkBorders,
)

private val LightColorScheme = lightColorScheme(
    primary = TwitchPurple,
    onPrimary = Color.White,
    secondary = TwitchPurple,
    onSecondary = Color.White,
    error = LiveRed,
    onError = Color.White,
    background = LightAppBackground,
    onBackground = TextHighEmphasisLight,
    surface = LightBarsBackground,
    onSurface = TextHighEmphasisLight,
    surfaceVariant = LightInputBackground,
    onSurfaceVariant = TextLowEmphasis,
    outline = LightBorders,
)

/**
 * VirtualTwitchDroid theme, mirroring skydoves/twitch-clone-compose (Stream SDK defaults with a
 * Twitch-purple `primaryAccent`). Dark by default, following the system.
 */
@Composable
fun TwitchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = TwitchTypography,
        shapes = TwitchShapes,
        content = content,
    )
}
