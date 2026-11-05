package com.githunt.android.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Figtree: a friendly, clean sans. The font file (res/font/figtree.ttf) is fetched by the
 * `fetchFigtree` task in app/build.gradle.kts. If it isn't there the app simply uses the
 * system sans font, so a missing download never breaks the build or the app.
 */
@OptIn(ExperimentalTextApi::class)
fun ghFontFamily(context: Context): FontFamily {
    val resId = context.resources.getIdentifier("figtree", "font", context.packageName)
    if (resId == 0) return FontFamily.SansSerif
    return FontFamily(
        listOf(400, 500, 600, 700, 800).map { w ->
            Font(resId, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}

// Calm and even: one family, regular-to-bold weights, no all-caps monospace.
fun ghTypography(family: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = TextStyle(
            fontFamily = family,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = d.titleSmall.copy(fontFamily = family),
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
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
        ),
    )
}
