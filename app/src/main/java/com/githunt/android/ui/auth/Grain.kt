package com.githunt.android.ui.auth

import android.graphics.Bitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/**
 * A fine film grain over the login card, like the soft noise in the reference design.
 * The noise tile is made once and repeated, so it costs almost nothing to draw.
 */
fun Modifier.authGrain(): Modifier = composed {
    val brush = remember {
        val side = 128
        val pixels = IntArray(side * side)
        val random = Random(7L)
        for (i in pixels.indices) {
            val shade = random.nextInt(256)
            val alpha = random.nextInt(26)
            pixels[i] = (alpha shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        val bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    drawWithContent {
        drawContent()
        drawRect(brush)
    }
}
