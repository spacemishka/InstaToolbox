package com.spacemishka.app.instatoolbox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CreatorDarkColorScheme = darkColorScheme(
    primary = CreatorSunsetPink,
    secondary = AccentCyan,
    tertiary = CreatorSunsetOrange,
    background = ObsidianBlack,
    surface = GlassySurface,
    onPrimary = Color.White,
    onSecondary = ObsidianBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun InstaToolboxTheme(
    darkTheme: Boolean = true, // Force dark mode for premium creator theme
    dynamicColor: Boolean = false, // Disable system dynamic color to preserve signature branding
    content: @Composable () -> Unit
) {
    val colorScheme = CreatorDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}