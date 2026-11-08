package com.githunt.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
private val ControlSurface = Color(0xFF19191B)
private val ControlBorder = Color.White.copy(alpha = 0.10f)

/**
 * Full-screen README image viewer.
 *
 * A blurred, darkened version of the current image fills the screen behind the uncropped image,
 * avoiding the large empty black bands around landscape screenshots. Tap to hide/show controls.
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

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    var zoomed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080809))) {
            // Ambient image backdrop fills portrait screens while keeping the actual image uncropped.
            AsyncImage(
                model = remember(images[pagerState.currentPage]) {
                    ImageRequest.Builder(LocalContext.current)
                        .data(proxiedImageUrl(images[pagerState.currentPage]))
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(250)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp).graphicsLayer {
                    alpha = 0.58f
                    scaleX = 1.12f
                    scaleY = 1.12f
                },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)))

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = images.size > 1 && !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    url = images[page],
                    contentDescription = contentDescription,
                    active = pagerState.currentPage == page,
                    onZoomChange = {
                        zoomed = it
                        controlsVisible = !it
                    },
                    onBackgroundTap = { controlsVisible = !controlsVisible },
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 }),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(ControlSurface).border(1.dp, ControlBorder, CircleShape)
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
                                color = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                    .background(ControlSurface.copy(alpha = 0.92f))
                                    .border(1.dp, ControlBorder, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }

                    Text(
                        text = "GitHub ↗",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(ControlSurface)
                            .border(1.dp, ControlBorder, RoundedCornerShape(22.dp))
                            .clickable(onClick = onOpenGithub).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            if (images.size > 1) {
                AnimatedVisibility(
                    visible = controlsVisible && !zoomed,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp))
                            .background(ControlSurface.copy(alpha = 0.92f))
                            .border(1.dp, ControlBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(images.size) { index ->
                            val selected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                                    .size(width = if (selected) 16.dp else 5.dp, height = 5.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.30f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

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
    val displayedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "previewZoom",
    )

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    val request = remember(url) {
        ImageRequest.Builder(context).data(proxiedImageUrl(url))
            .decoderFactory(SvgDecoder.Factory()).crossfade(220).build()
    }

    Box(
        modifier = Modifier.fillMaxSize()
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
                            offset = if (newScale <= 1f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
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
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = displayedScale
                scaleY = displayedScale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
