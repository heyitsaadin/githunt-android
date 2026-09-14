package com.githunt.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// GitHunt's web app is a deliberately minimal black-and-white feed (see
// README: "A minimal, black-and-white feed..."). This mirrors that palette
// rather than introducing a generic Material color scheme, plus the
// UI font choice documented in build-logs (Inter).

val Ink = Color(0xFF0A0A0A)
val Paper = Color(0xFFFFFFFF)
val PaperDark = Color(0xFF0A0A0A)
val InkDark = Color(0xFFF2F2F2)
val Muted = Color(0xFF6B6B6B)
val MutedDark = Color(0xFF9A9A9A)
val Divider = Color(0xFFE5E5E5)
val DividerDark = Color(0xFF262626)
val AccentGreen = Color(0xFF2FBF71) // "Verified" badge

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF6F6F6),
    onSurfaceVariant = Muted,
    outline = Divider,
    error = Color(0xFFD64545),
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = PaperDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = MutedDark,
    outline = DividerDark,
    error = Color(0xFFE57373),
)

val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFamily = FontFamily.Default),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFamily = FontFamily.Default),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, fontFamily = FontFamily.Default),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, fontFamily = FontFamily.Default),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = FontFamily.Default),
)

@Composable
fun GitHuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
