package com.githunt.android.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import com.githunt.android.ui.theme.GhMotion

/*
 * Page transitions, now driven by Material 3 Expressive springs instead of fixed-length tweens.
 * Same choreography as before (the new page slides in from the right while growing from 92% and
 * fading in, the old one drifts a quarter of the way left and fades out, and back plays the
 * mirror image), but the movement is a spring, so it settles with a touch of bounce and can be
 * interrupted smoothly. Fades use the non-bouncy effects spring.
 */

/**
 * Switching between sibling tabs (Home, Discover, Profile): a short, light "shared axis" move.
 * The new tab slides in a small distance from the side it sits on in the dock, and the old one
 * slides a small distance the other way, both cross-fading.
 */
fun tabTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enter = fadeIn(
        animationSpec = GhMotion.defaultEffects(),
    ) + slideInHorizontally(
        animationSpec = GhMotion.defaultSpatial(),
        initialOffsetX = { (it * 0.08f).toInt() * direction },
    )
    val exit = fadeOut(
        animationSpec = GhMotion.fastEffects(),
    ) + slideOutHorizontally(
        animationSpec = GhMotion.defaultSpatial(),
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
        animationSpec = GhMotion.defaultSpatial(),
        initialOffsetX = { (it * 0.5f).toInt() },
    ) + scaleIn(
        animationSpec = GhMotion.defaultSpatial(),
        initialScale = 0.92f,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
    ) + fadeIn(
        animationSpec = GhMotion.defaultEffects(),
    )
    ) to (
    slideOutHorizontally(
        animationSpec = GhMotion.defaultSpatial(),
        targetOffsetX = { -(it * 0.25f).toInt() },
    ) + fadeOut(
        animationSpec = GhMotion.defaultEffects(),
    )
    )

/** Going back (Settings to Profile, a sub page to the main Settings page). */
private val backTransition = (
    slideInHorizontally(
        animationSpec = GhMotion.defaultSpatial(),
        initialOffsetX = { -(it * 0.25f).toInt() },
    ) + scaleIn(
        animationSpec = GhMotion.defaultSpatial(),
        initialScale = 0.95f,
    ) + fadeIn(
        animationSpec = GhMotion.defaultEffects(),
    )
    ) to (
    slideOutHorizontally(
        animationSpec = GhMotion.defaultSpatial(),
        targetOffsetX = { (it * 0.5f).toInt() },
    ) + scaleOut(
        animationSpec = GhMotion.defaultSpatial(),
        targetScale = 0.92f,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
    ) + fadeOut(
        animationSpec = GhMotion.defaultEffects(),
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
