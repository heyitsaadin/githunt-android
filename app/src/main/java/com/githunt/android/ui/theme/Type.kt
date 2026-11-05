package com.githunt.android.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The website's two typefaces (app/layout.js + tailwind.config.js in the web repo):
 *  - `font-sans` = Plus Jakarta Sans, used for all normal text.
 *  - `font-mono` = Inter (despite the class name), used for stats, times, topic chips,
 *    pill buttons and other small labels.
 *
 * The font files (res/font/plus_jakarta_sans.ttf, res/font/inter.ttf) are fetched by the
 * `fetchPlusJakartaSans` / `fetchInter` tasks in app/build.gradle.kts. If one isn't there the
 * app simply uses the system sans font for that face, so a missing download never breaks
 * the build or the app.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    context: Context,
    resName: String,
    weights: List<Int>,
): FontFamily {
    val resId = context.resources.getIdentifier(resName, "font", context.packageName)
    if (resId == 0) return FontFamily.SansSerif
    return FontFamily(
        weights.map { w ->
            Font(resId, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}

/** Plus Jakarta Sans: the website's `font-sans`. */
fun ghFontFamily(context: Context): FontFamily =
    variableFamily(context, "plus_jakarta_sans", listOf(400, 500, 600, 700, 800))

/** Inter: what the website's `font-mono` class renders. */
fun ghMonoFamily(context: Context): FontFamily =
    variableFamily(context, "inter", listOf(400, 500, 600))

/** The website's `font-mono` face (Inter). Provided by [GitHuntTheme]. */
val LocalGhMono = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

// The website never adds letter-spacing, while Material's defaults do (+0.5sp on labels,
// +0.25sp on body text, negative on the big sizes). Zero it so text is as wide as on the web.
private fun TextStyle.web(family: FontFamily): TextStyle =
    copy(fontFamily = family, letterSpacing = 0.sp)

// Calm and even: one family, regular-to-bold weights, no all-caps monospace.
fun ghTypography(family: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.web(family),
        displayMedium = d.displayMedium.web(family),
        displaySmall = d.displaySmall.web(family),
        headlineLarge = d.headlineLarge.web(family),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = d.headlineSmall.web(family),
        titleLarge = d.titleLarge.web(family),
        titleMedium = TextStyle(
            fontFamily = family,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = d.titleSmall.web(family),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodySmall = d.bodySmall.web(family),
        labelLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = d.labelMedium.web(family),
        labelSmall = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
        ),
    )
}
