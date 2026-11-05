package com.githunt.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Character positions in "githunt.": g0 i1 t2 h3 u4 n5 t6 .7
private const val IDX_H = 3
private const val IDX_T = 6
private const val IDX_DOT = 7

// How far above the baseline the top of each letter sits (as a fraction of the font size), and where
// across the letter its stem is (0 = left edge, 1 = right edge). The dot lands on the stem.
// Tune these if the dot looks like it floats above, or sinks into, a letter.
private const val H_TOP = 0.73f
private const val T_TOP = 0.62f
private const val H_STEM = 0.26f
private const val T_STEM = 0.38f

/**
 * The same wordmark as [Wordmark], but it types itself out, then the dot drops onto the "h",
 * bounces, hops onto the "t" and finally rolls home to the end of the word.
 *
 * The finished frame is identical to [Wordmark]: same text, same layout. The word is always laid out
 * in full (so nothing shifts) and the typing is a clip that reveals it letter by letter.
 */
@Composable
fun AnimatedWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    onFinished: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val finished by rememberUpdatedState(onFinished)
    val caretColor = scheme.onBackground

    val text = remember(scheme.onSurfaceVariant, scheme.onBackground, scheme.primary) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Medium)) { append("git") }
            withStyle(SpanStyle(color = scheme.onBackground, fontWeight = FontWeight.Black)) { append("hunt") }
            withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Black)) { append(".") }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var typed by remember { mutableIntStateOf(0) }       // how many of "githunt" are showing
    var caretOn by remember { mutableStateOf(false) }
    var dotShown by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }     // true once the dot is home
    var pivot by remember { mutableStateOf(TransformOrigin.Center) }
    val dotX = remember { Animatable(0f) }                // dot offset from its home spot, in px
    val dotY = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val l = snapshotFlow { layout }.filterNotNull().first()
        val fs = with(density) { fontSize.toPx() }
        val baseline = l.getLineBaseline(0)
        val dotBox = l.getBoundingBox(IDX_DOT)
        val dotCx = (dotBox.left + dotBox.right) / 2f
        val hBox = l.getBoundingBox(IDX_H)
        val tBox = l.getBoundingBox(IDX_T)

        val hX = hBox.left + hBox.width * H_STEM - dotCx
        val tX = tBox.left + tBox.width * T_STEM - dotCx
        val hY = -H_TOP * fs
        val tY = -T_TOP * fs
        pivot = TransformOrigin(dotCx / l.size.width, baseline / l.size.height)

        // 1. Typing.
        delay(250)
        caretOn = true
        longArrayOf(80, 70, 95, 85, 70, 75, 110).forEachIndexed { i, gap ->
            typed = i + 1
            delay(gap)
        }
        delay(150)
        caretOn = false
        delay(60)

        // 2. The dot falls onto the h.
        dotX.snapTo(hX)
        dotY.snapTo(hY - 1.8f * fs)
        dotShown = true
        dotY.animateTo(hY, tween(380, easing = FastOutLinearInEasing))
        launch { thump(squash, 0.62f) }

        // 3. A little bounce on the h, then a hop onto the t.
        hop(dotX, dotY, hX, hY, apex = 0.22f * fs, millis = 260)
        hop(dotX, dotY, tX, tY, apex = 0.5f * fs, millis = 340)
        launch { thump(squash, 0.68f) }
        hop(dotX, dotY, tX, tY, apex = 0.12f * fs, millis = 190)

        // 4. Home: down to the baseline after the last letter, with a tiny settle.
        hop(dotX, dotY, 0f, 0f, apex = 0.55f * fs, millis = 360)
        launch { thump(squash, 0.72f) }
        hop(dotX, dotY, 0f, 0f, apex = 0.06f * fs, millis = 150)

        settled = true
        delay(220)
        finished()
    }

    Box(modifier) {
        // The word, revealed by a clip that grows as each letter is typed. Once the dot is home the
        // clip is dropped and this is exactly the static wordmark.
        WordmarkText(
            text = text,
            fontSize = fontSize,
            onTextLayout = { layout = it },
            modifier = Modifier.drawWithContent {
                val l = layout
                if (settled) {
                    drawContent()
                } else if (l != null && typed > 0) {
                    val last = l.getBoundingBox(typed - 1)
                    clipRect(left = 0f, top = 0f, right = last.right + 1.sp.toPx(), bottom = this.size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
                if (caretOn && !settled && l != null) {
                    val x = if (typed == 0) l.getBoundingBox(0).left else l.getBoundingBox(typed - 1).right
                    val fs = fontSize.toPx()
                    val baseline = l.getLineBaseline(0)
                    drawRect(
                        color = caretColor,
                        topLeft = Offset(x + 2.dp.toPx(), baseline - 0.74f * fs),
                        size = Size(2.5.dp.toPx(), 0.86f * fs),
                    )
                }
            },
        )

        // A second copy of the same text, clipped down to just the dot, that is moved around.
        // At offset (0, 0) it sits exactly where the real dot ends up.
        WordmarkText(
            text = text,
            fontSize = fontSize,
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer {
                    alpha = if (dotShown && !settled) 1f else 0f
                    translationX = dotX.value
                    translationY = dotY.value
                    scaleY = squash.value
                    scaleX = 1f + (1f - squash.value) * 0.5f
                    transformOrigin = pivot
                }
                .drawWithContent {
                    val l = layout
                    if (l != null) {
                        val b = l.getBoundingBox(IDX_DOT)
                        val cx = (b.left + b.right) / 2f
                        val half = 0.13f * fontSize.toPx()
                        clipRect(left = cx - half, top = 0f, right = cx + half, bottom = this.size.height) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        )
    }
}

@Composable
private fun WordmarkText(
    text: AnnotatedString,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    // Same settings as Wordmark, so the two look identical when the animation is done.
    Text(
        text = text,
        fontSize = fontSize,
        letterSpacing = (-1).sp,
        modifier = modifier,
        onTextLayout = onTextLayout,
    )
}

/** One bounce: up to [apex] px above the higher end, across to ([toX], [toY]), and down again. */
private suspend fun hop(
    x: Animatable<Float, AnimationVector1D>,
    y: Animatable<Float, AnimationVector1D>,
    toX: Float,
    toY: Float,
    apex: Float,
    millis: Int,
) = coroutineScope {
    val peak = minOf(y.value, toY) - apex
    launch { x.animateTo(toX, tween(millis, easing = LinearEasing)) }
    launch {
        y.animateTo(peak, tween((millis * 0.46f).toInt(), easing = LinearOutSlowInEasing))
        y.animateTo(toY, tween((millis * 0.54f).toInt(), easing = FastOutLinearInEasing))
    }
}

/** A quick squash when the dot lands, then it springs back. */
private suspend fun thump(squash: Animatable<Float, AnimationVector1D>, depth: Float) {
    squash.animateTo(depth, tween(45))
    squash.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
}
