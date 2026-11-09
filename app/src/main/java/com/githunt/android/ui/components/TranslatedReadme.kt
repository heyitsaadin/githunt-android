package com.githunt.android.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** At most this many skeletons shimmer at once (from the top); the rest stay still to save work. */
private const val ANIMATED_SKELETONS = 6

/**
 * A README that is being translated block by block.
 *
 * [waiting] is true for a block that has words to translate and has not been answered yet: it shows
 * a skeleton. When its translation arrives it goes false and [translated] holds the new text (null
 * means the block stays as the original). Blocks with nothing to translate (code, images, badges)
 * never wait.
 */
internal class TranslatingReadme(
    val blocks: List<String>,
    needsTranslation: List<Boolean>,
) {
    val translated = mutableStateListOf<String?>().apply { repeat(blocks.size) { add(null) } }
    val waiting = mutableStateListOf<Boolean>().apply { addAll(needsTranslation) }

    /** How many blocks have words to translate. */
    val total: Int = needsTranslation.count { it }

    /** How many of those are done. */
    val finished: Int get() = total - waiting.count { it }
}

/**
 * The README drawn as separate blocks. A block still waiting for its translation is a skeleton the
 * size of its text; the moment its translation lands the skeleton fades into plain text and the
 * next block carries on loading. Everything else (code, images, ...) is shown as it is, right away.
 */
@Composable
internal fun TranslatedReadmeBlocks(state: TranslatingReadme, modifier: Modifier = Modifier) {
    val shared = rememberSharedMarkdown()
    Column(modifier = modifier.fillMaxWidth()) {
        state.blocks.forEachIndexed { index, block ->
            key(index) {
                if (index > 0) Spacer(Modifier.height(10.dp))
                val isWaiting = state.waiting[index]
                val animate = isWaiting && state.waiting.subList(0, index).count { it } < ANIMATED_SKELETONS
                Crossfade(
                    targetState = isWaiting,
                    animationSpec = tween(durationMillis = 250),
                    label = "readme-block",
                ) { waiting ->
                    if (waiting) {
                        BlockSkeleton(block = block, animate = animate)
                    } else {
                        SharedMarkdownText(markdown = state.translated[index] ?: block, shared = shared)
                    }
                }
            }
        }
    }
}

/** Grey lines about as long as the block's text, plus a heading bar when the block opens with one. */
@Composable
private fun BlockSkeleton(block: String, animate: Boolean) {
    // Link-reference definitions copied onto a block draw nothing, so they don't count.
    val visible = block.lineSequence()
        .filterNot { it.trimStart().startsWith("[") && it.contains("]:") }
        .joinToString("\n")
    val heading = visible.trimStart().startsWith("#")
    val bodyLines = (visible.length / 48).coerceIn(1, 6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (animate) Modifier.shimmerSweep() else Modifier),
    ) {
        if (heading) {
            SkeletonBar(fraction = 0.45f, height = 16.dp)
            Spacer(Modifier.height(10.dp))
        }
        repeat(bodyLines) { i ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            val fraction = when {
                i == bodyLines - 1 && bodyLines > 1 -> 0.6f
                i % 3 == 1 -> 0.92f
                else -> 1f
            }
            SkeletonBar(fraction = fraction, height = 11.dp)
        }
    }
}
