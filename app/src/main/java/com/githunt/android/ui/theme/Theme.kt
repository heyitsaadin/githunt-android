package com.githunt.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// "primary" is plain white (dark) or near-black (light): no accent colour anywhere.
private val DarkColors = darkColorScheme(
    primary = Bone,
    onPrimary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Panel,
    onSurface = Bone,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Mist,
    outline = Line,
    outlineVariant = Line,
    error = Flare,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = Carbon,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Carbon,
    surface = PaperPanel,
    onSurface = Carbon,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = Slate,
    outline = PaperLine,
    outlineVariant = PaperLine,
    error = Brick,
    onError = Color.White,
)

private val GhShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun GitHuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val typography = remember(context) { ghTypography(ghFontFamily(context)) }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography,
        shapes = GhShapes,
        content = content,
    )
}
