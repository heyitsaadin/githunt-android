package com.githunt.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.githunt.android.ui.theme.LocalGhMono

private const val ClosePath = "M6 6L18 18 M18 6L6 18"
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f
private val PreviewSurface = Color(0xFF171717)
private val PreviewBorder = Color.White.copy(alpha = 0.12f)

/**
 * Full-screen look at a repo's README pictures.
 *
 * Swipe sideways between pictures, pinch to zoom, drag to pan while zoomed, and double-tap to
 * toggle zoom. Pictures are shown whole and uncropped. GitHub remains available from the toolbar.
 */
@Composable
fun ImagePreviewDialog(
    images: List<String>,
    startIndex: Int,
    contentDescription: String,
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF111111), Color.Black, Color(0xFF080808)),
                    ),
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = images.size > 1 && !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    url = images[page],
                    contentDescription = contentDescription,
                    active = pagerState.currentPage == page,
                    onZoomChange = { zoomed = it },
                    onBackgroundTap = onDismiss,
                )
            }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { -it / 3 },
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PreviewSurface)
                            .border(1.dp, PreviewBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        PathIcon(pathData = ClosePath, tint = Color.White, iconSize = 18.dp, strokeWidth = 2f)
                    }

                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (images.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${images.size}",
                                fontFamily = LocalGhMono.current,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(PreviewSurface.copy(alpha = 0.92f))
                                    .border(1.dp, PreviewBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }

                    Text(
                        text = "GitHub ↗",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                            .clickable(onClick = onOpenGithub)
                            .padding(horizontal = 15.dp, vertical = 11.dp),
                    )
                }
            }

            if (images.size > 1 && !zoomed) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(350, delayMillis = 120)) + slideInVertically(tween(350)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        text = "Swipe to explore",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.48f),
                        modifier = Modifier
                            .padding(bottom = 26.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/**
 * One picture, shown whole (never cropped). Pinch to zoom up to 5x, pan while zoomed, double-tap
 * to toggle zoom. A one-finger swipe at normal size is left alone so the pager can change page.
 */
@Composable
private fun ZoomableImage(
    url: String,
    contentDescription: String,
    active: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onBackgroundTap: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(proxiedImageUrl(url))
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(260)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1f) onBackgroundTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChange(false)
                        } else {
                            scale = DoubleTapZoom
                            onZoomChange(true)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, MaxZoom)
                            val pan = event.calculatePan()
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            offset = if (newScale <= 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                            scale = newScale
                            onZoomChange(newScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
