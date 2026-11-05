package com.githunt.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * An image that never pops in.
 *
 * The size comes from [modifier], so the space is reserved before anything loads and the layout
 * never jumps. A shimmer fills that space while the image loads, then fades out over the picture
 * as it arrives. Put `crossfade(...)` on the request (as RepoCard does) so the picture itself also
 * fades in; images already in Coil's memory cache skip the fade and show straight away.
 *
 * [onFailed] is called if the image can't be loaded. The shimmer stops either way.
 */
@Composable
fun ShimmerImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onFailed: () -> Unit = {},
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val shimmerAlpha by animateFloatAsState(
        targetValue = if (loaded) 0f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "image-shimmer-fade",
    )

    Box(modifier) {
        if (shimmerAlpha > 0f) {
            ShimmerBlock(Modifier.matchParentSize().alpha(shimmerAlpha))
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onSuccess = { loaded = true },
            onError = {
                loaded = true
                onFailed()
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}
