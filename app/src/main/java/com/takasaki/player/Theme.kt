package com.takasaki.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** アプリのメインカラー（濃い緑） */
val DeepGreen = Color(0xFF1B5E20)
private val DeepGreenLight = Color(0xFF4C8C4A)
private val DeepGreenContainer = Color(0xFFC8E6C9)
private val DeepGreenDarkContainer = Color(0xFF2E7D32)

val LightColors = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = DeepGreenContainer,
    onPrimaryContainer = Color(0xFF0B3D10),
    secondary = DeepGreenLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8D0),
    onSecondaryContainer = Color(0xFF12301A),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDCE8D9),
    onSurfaceVariant = Color(0xFF41493F),
    surfaceContainerHighest = Color(0xFFD9E7D6),
    surfaceContainerHigh = Color(0xFFE2EEDF),
    surfaceContainer = Color(0xFFE8F3E5),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF1A1C19),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF00390A),
    primaryContainer = DeepGreenDarkContainer,
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF0B3D10),
    secondaryContainer = Color(0xFF34473A),
    onSecondaryContainer = Color(0xFFC8E6C9),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF243026),
    onSurfaceVariant = Color(0xFFC0C9BD),
    surfaceContainerHighest = Color(0xFF2E3A2F),
    surfaceContainerHigh = Color(0xFF263127),
    surfaceContainer = Color(0xFF1E281F),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DE),
)
