package com.githunt.android.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion: springs instead of fixed-duration tweens.
 *
 * "Spatial" springs move things (position, size, shape) and may overshoot a little, which gives
 * the bouncy feel. "Effects" springs fade colours and opacity and never overshoot. The values
 * are the ones Material's own expressive motion scheme uses.
 */
object GhMotion {
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> slowSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> fastEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> defaultEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
