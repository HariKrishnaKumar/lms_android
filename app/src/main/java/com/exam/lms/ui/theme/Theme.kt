package com.exam.lms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LmsBluePrimary,
    onPrimary = LmsBlueOnPrimary,
    primaryContainer = LmsBlueContainer,
    onPrimaryContainer = LmsBlueOnContainer,
    tertiary = LmsGreenSuccess,
    error = LmsRedError,
    onError = Color.White,
    background = LmsBackgroundLight,
    surface = LmsSurfaceLight,
    onSurface = LmsOnSurfaceLight,
    onSurfaceVariant = LmsOnSurfaceVariantLight,
    outline = LmsOutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LmsBluePrimary,
    onPrimary = LmsBlueOnPrimary,
    primaryContainer = LmsBlueOnContainer,
    onPrimaryContainer = LmsBlueContainer,
    tertiary = LmsGreenSuccess,
    error = LmsRedError,
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF252526),
    onSurface = Color(0xFFCCCCCC),
    onSurfaceVariant = Color(0xFFA6A6A6),
    outline = Color(0xFF3C3C3C)
)

@Composable
fun LMSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
