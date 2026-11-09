package com.githunt.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// "primary" is plain white (dark) or near-black (light): no accent colour anywhere.
// Every Material role is spelled out in neutral greys, so no component can fall back to the
// baseline purple (containers, tonal buttons, switches, menus and so on).
private val DarkColors = darkColorScheme(
    primary = Bone,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A3A40),
    onPrimaryContainer = Bone,
    secondary = Color(0xFFC8C8D0),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2F2F34),
    onSecondaryContainer = Bone,
    tertiary = Color(0xFFC8C8D0),
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF2F2F34),
    onTertiaryContainer = Bone,
    background = Ink,
    onBackground = Bone,
    surface = Panel,
    onSurface = Bone,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Mist,
    outline = Line,
    outlineVariant = Line,
    inverseSurface = Bone,
    inverseOnSurface = Ink,
    inversePrimary = Carbon,
    error = Flare,
    onError = Ink,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Panel,
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = PanelHigh,
)

private val LightColors = lightColorScheme(
    primary = Carbon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5D5DB),
    onPrimaryContainer = Carbon,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = PaperHigh,
    onSecondaryContainer = Carbon,
    tertiary = Slate,
    onTertiary = Color.White,
    tertiaryContainer = PaperHigh,
    onTertiaryContainer = Carbon,
    background = Paper,
    onBackground = Carbon,
    surface = PaperPanel,
    onSurface = Carbon,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = Slate,
    outline = PaperLine,
    outlineVariant = PaperLine,
    inverseSurface = Carbon,
    inverseOnSurface = Paper,
    inversePrimary = Bone,
    error = Brick,
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = PaperPanel,
    surfaceContainerHigh = Color(0xFFEDEDF0),
    surfaceContainerHighest = PaperHigh,
)

// Pure black for OLED screens: the page itself is #000 so those pixels switch off. Cards, pills
// and sheets stay a hair lighter than the page so they still read as separate shapes.
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF0C0C0E),
    surfaceVariant = Color(0xFF18181B),
    outline = Color(0xFF26262A),
    outlineVariant = Color(0xFF26262A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080809),
    surfaceContainer = Color(0xFF0C0C0E),
    surfaceContainerHigh = Color(0xFF131316),
    surfaceContainerHighest = Color(0xFF18181B),
)

// Expressive shapes: rounder across the board than the old 10 / 14 / 20.
private val GhShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GitHuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val typography = remember(context) { ghTypography(ghFontFamily(context)) }
    val mono = remember(context) { ghMonoFamily(context) }
    CompositionLocalProvider(LocalGhMono provides mono) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = GhShapes,
            content = content,
        )
    }
}
