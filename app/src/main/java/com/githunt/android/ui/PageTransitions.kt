package com.githunt.android.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

/*
 * Page transitions taken from PixelPlayer (its navigation/Transitions.kt), so moving between
 * pages feels the same: the new page slides in from the right while growing from 92% and fading
 * in, and the old page drifts a quarter of the way left and fades out. Going back plays the
 * mirror image: the page underneath eases back in from the left while the one being left slides
 * off to the right, shrinking and fading.
 */

private val EmphasizedDecelerate = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val PageMillis = 450

private const val TabMillis = 300
private const val TabFadeOutMillis = 90
private const val TabFadeInMillis = 210

/**
 * Switching between sibling tabs (Home, Discover, Profile): a short, light "shared axis" move.
 *
 * The page you leave fades out fast (90 ms) while drifting a little against the direction of
 * travel, then the new page fades in while sliding the short distance from the side it came
 * from. Moving to a tab further right slides left, and the reverse for tabs further left, so the
 * motion matches the order of the dock. It is a small, fast move (300 ms, about 8% of the width)
 * rather than a full-screen push, so it reads as a switch and never gets in the way.
 */
fun tabTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enter = fadeIn(
        animationSpec = tween(TabFadeInMillis, delayMillis = TabFadeOutMillis, easing = LinearOutSlowInEasing),
    ) + slideInHorizontally(
        animationSpec = tween(TabMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { (it * 0.08f).toInt() * direction },
    )
    val exit = fadeOut(
        animationSpec = tween(TabFadeOutMillis, easing = FastOutLinearInEasing),
    ) + slideOutHorizontally(
        animationSpec = tween(TabMillis, easing = FastOutSlowInEasing),
        targetOffsetX = { -(it * 0.08f).toInt() * direction },
    )
    // No size animation: both tabs fill the same area, so nothing should resize or clip.
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = null,
    )
}

/** Going deeper (Profile to Settings, Settings to a sub page). */
private val forwardTransition = (
    slideInHorizontally(
        animationSpec = tween(PageMillis, easing = EmphasizedDecelerate),
        initialOffsetX = { (it * 0.5f).toInt() },
    ) + scaleIn(
        animationSpec = tween(PageMillis, easing = EmphasizedDecelerate),
        initialScale = 0.92f,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
    ) + fadeIn(
        animationSpec = tween(PageMillis, easing = EmphasizedAccelerate),
    )
    ) to (
    slideOutHorizontally(
        animationSpec = tween(PageMillis, easing = EmphasizedAccelerate),
        targetOffsetX = { -(it * 0.25f).toInt() },
    ) + fadeOut(
        animationSpec = tween(PageMillis / 2, easing = EmphasizedAccelerate),
    )
    )

/** Going back (Settings to Profile, a sub page to the main Settings page). */
private val backTransition = (
    slideInHorizontally(
        animationSpec = tween(PageMillis, easing = EmphasizedDecelerate),
        initialOffsetX = { -(it * 0.25f).toInt() },
    ) + scaleIn(
        animationSpec = tween(PageMillis, easing = EmphasizedDecelerate),
        initialScale = 0.95f,
    ) + fadeIn(
        animationSpec = tween(PageMillis / 2, easing = EmphasizedDecelerate),
    )
    ) to (
    slideOutHorizontally(
        animationSpec = tween(PageMillis, easing = EmphasizedAccelerate),
        targetOffsetX = { (it * 0.5f).toInt() },
    ) + scaleOut(
        animationSpec = tween(PageMillis, easing = EmphasizedAccelerate),
        targetScale = 0.92f,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
    ) + fadeOut(
        animationSpec = tween(PageMillis / 2, easing = EmphasizedAccelerate),
    )
    )

/**
 * The transition for an `AnimatedContent` that behaves like a navigation stack.
 *
 * [targetDepth] is how deep the page being shown sits in the stack (0 = the base page). Deeper
 * pages are drawn on top, so a page you are leaving stays above the one coming back instead of
 * vanishing underneath it.
 */
fun pageTransition(forward: Boolean, targetDepth: Int): ContentTransform {
    val (enter, exit) = if (forward) forwardTransition else backTransition
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        targetContentZIndex = targetDepth.toFloat(),
    )
}
