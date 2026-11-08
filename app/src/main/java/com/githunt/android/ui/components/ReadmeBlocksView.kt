package com.githunt.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.githunt.android.data.translate.ReadmeBlock

/**
 * A README shown block by block while (and after) it is translated.
 *
 * A block still waiting for its translation is a shimmering skeleton the size of the text it will
 * become. The moment a block's translation is ready, its skeleton is replaced by the translated
 * text and the next block carries on, top to bottom. Blocks with nothing to translate (code,
 * images, tables of code...) are drawn as they are from the start.
 *
 * Every block is its own small view, so finishing one block re-draws only that block, never the
 * whole README. They all share one renderer and one shimmer animation.
 */
@Composable
fun TranslatedReadmeBlocks(
    blocks: List<ReadmeBlock>,
    modifier: Modifier = Modifier,
) {
    val renderer = rememberMarkdownRenderer()
    val shimmer = rememberShimmerProgress()
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                if (index > 0) Spacer(Modifier.height(10.dp))
                if (block.pending) {
                    BlockSkeleton(original = block.original, shimmer = shimmer)
                } else {
                    MarkdownBody(markdown = block.translated ?: block.original, renderer = renderer)
                }
            }
        }
    }
}

/** Grey lines about as tall as the block's text will be, so the layout barely moves when it lands. */
@Composable
private fun BlockSkeleton(original: String, shimmer: State<Float>) {
    val heading = remember(original) { original.trimStart().startsWith("#") }
    val lines = remember(original) { if (heading) 1 else (original.length / 52 + 1).coerceIn(1, 8) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .shimmerSweepWith(shimmer),
    ) {
        if (heading) {
            SkeletonBar(fraction = 0.5f, height = 16.dp)
        } else {
            repeat(lines) { i ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                SkeletonBar(fraction = if (lines > 1 && i == lines - 1) 0.6f else 1f, height = 11.dp)
            }
        }
    }
}
