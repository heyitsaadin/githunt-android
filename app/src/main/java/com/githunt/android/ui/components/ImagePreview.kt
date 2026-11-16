package com.githunt.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val ClosePath = "M6 6L18 18 M18 6L6 18"
private const val OpenPath = "M7 17L17 7 M8 7H17V16"
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f
private const val FlingDismissVelocity = 1800f
private val DismissDistance = 120.dp

// Fully opaque controls: a solid dark fill with a solid outline, nothing see-through.
private val ControlFill = Color(0xFF1C1C1F)
private val ControlOutline = Color(0xFF3F3F46)
private val ControlOnFill = Color(0xFFF4F4F5)

/**
 * Full-screen image viewer with a blurred ambient backdrop, tap-to-hide controls,
 * pinch / double-tap zoom and swipe up or down to dismiss.
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

    val context = LocalContext.current
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    val currentImageUrl = images[pagerState.currentPage]
    val backdropRequest = remember(currentImageUrl, context) {
        ImageRequest.Builder(context)
            .data(proxiedImageUrl(currentImageUrl))
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(250)
            .build()
    }
    var zoomed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Swipe-to-dismiss state: the image follows the finger, the backdrop fades out.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissDistancePx = with(density) { DismissDistance.toPx() }
    val scope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val dragState = rememberDraggableState { delta -> dragY += delta }
    val dragProgress = { (abs(dragY) / (screenHeightPx * 0.45f)).coerceIn(0f, 1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // A Dialog window does not paint under the status bar and the navigation bar by default,
        // so the screen behind showed through those two strips. Make the window span the whole
        // display, draw under the bars (including a camera cutout) and keep the bars transparent
        // with light icons, so the viewer's own backdrop fills every pixel.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.also {
                    it.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = !zoomed,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    val sameDirection = dragY == 0f || velocity * dragY >= 0f
                    val pulledFar = abs(dragY) > dismissDistancePx && sameDirection
                    val flung = abs(velocity) > FlingDismissVelocity && sameDirection
                    settleJob = scope.launch {
                        if (pulledFar || flung) {
                            val direction = sign(if (dragY != 0f) dragY else velocity).let { if (it == 0f) 1f else it }
                            animate(dragY, direction * screenHeightPx, animationSpec = tween(180)) { value, _ ->
                                dragY = value
                            }
                            onDismiss()
                        } else {
                            animate(
                                initialValue = dragY,
                                targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            ) { value, _ -> dragY = value }
                        }
                    }
                },
            ),
        ) {
            // Backdrop layer: fades as the image is dragged away.
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 1f - dragProgress() * 0.95f }
                    .background(Color(0xFF080809)),
            ) {
                // The blurred image fills portrait screens while the foreground stays uncropped.
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp).graphicsLayer {
                        alpha = 0.58f
                        scaleX = 1.12f
                        scaleY = 1.12f
                    },
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)))
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = images.size > 1 && !zoomed,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = dragY
                    val s = 1f - dragProgress() * 0.1f
                    scaleX = s
                    scaleY = s
                },
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

            // Top bar: close on the left, open on GitHub on the right.
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 }),
                modifier = Modifier.align(Alignment.TopCenter)
                    .graphicsLayer { alpha = (1f - dragProgress() * 3f).coerceIn(0f, 1f) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Close: solid dark circle.
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(ControlFill)
                            .border(1.5.dp, ControlOutline, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        PathIcon(pathData = ClosePath, tint = ControlOnFill, iconSize = 18.dp, strokeWidth = 2.2f)
                    }

                    Spacer(Modifier.weight(1f))

                    // Open on GitHub: solid light button so it reads as the primary action.
                    Row(
                        modifier = Modifier.height(44.dp).clip(CircleShape)
                            .background(ControlOnFill)
                            .clickable(onClick = onOpenGithub)
                            .padding(start = 18.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "GitHub",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111113),
                        )
                        Spacer(Modifier.width(6.dp))
                        PathIcon(pathData = OpenPath, tint = Color(0xFF111113), iconSize = 16.dp, strokeWidth = 2.2f)
                    }
                }
            }

            // Page dots.
            if (images.size > 1) {
                AnimatedVisibility(
                    visible = controlsVisible && !zoomed,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = (1f - dragProgress() * 3f).coerceIn(0f, 1f) },
                ) {
                    Row(
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 20.dp)
                            .clip(CircleShape)
                            .background(ControlFill)
                            .border(1.5.dp, ControlOutline, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(images.size) { index ->
                            val selected = pagerState.currentPage == index
                            val dotWidth by animateDpAsState(
                                targetValue = if (selected) 18.dp else 6.dp,
                                label = "previewDotWidth",
                            )
                            Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                                    .size(width = dotWidth, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) ControlOnFill else Color(0xFF71717A)),
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
