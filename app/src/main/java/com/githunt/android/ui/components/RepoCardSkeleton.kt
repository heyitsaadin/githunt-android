package com.githunt.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder with the same layout as [RepoCard], shown while a list loads: preview picture,
 * avatar and title, description, README excerpt, topic chips, the two buttons, stats and the
 * share row. The heights match the real card, so when the data arrives the list does not jump.
 *
 * All the grey shapes live in one group, so a single highlight sweeps across the whole card.
 * Pass [showPreview] = false for a card that has no README picture.
 */
@Composable
fun RepoCardSkeleton(modifier: Modifier = Modifier, showPreview: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth().shimmerSweep()) {
            if (showPreview) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(scheme.surfaceVariant),
                )
            }

            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        SkeletonBar(fraction = 0.55f, height = 15.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBar(fraction = 0.3f, height = 11.dp)
                    }
                }

                // Description, two lines.
                Spacer(Modifier.height(10.dp))
                SkeletonBar(fraction = 1f, height = 12.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonBar(fraction = 0.7f, height = 12.dp)

                // README excerpt, two lines.
                Spacer(Modifier.height(14.dp))
                SkeletonBar(fraction = 0.95f, height = 10.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(fraction = 0.8f, height = 10.dp)

                // Topic chips.
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonPill(width = 54.dp, height = 18.dp)
                    SkeletonPill(width = 66.dp, height = 18.dp)
                    SkeletonPill(width = 46.dp, height = 18.dp)
                }

                // Show README / Open GitHub.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonPill(width = 0.dp, height = 32.dp, modifier = Modifier.weight(1f))
                    SkeletonPill(width = 0.dp, height = 32.dp, modifier = Modifier.weight(1f))
                }

                // Stars, forks, language.
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SkeletonPill(width = 36.dp, height = 12.dp)
                    SkeletonPill(width = 36.dp, height = 12.dp)
                    SkeletonPill(width = 58.dp, height = 12.dp)
                }

                // Divider and the share / clip row.
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.outline.copy(alpha = 0.4f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonPill(width = 64.dp, height = 14.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonPill(width = 52.dp, height = 14.dp)
                }
            }
        }
    }
}

/**
 * Placeholder for a README while it loads inside an expanded card: a heading, some paragraph
 * lines and a code block, all under one shimmer sweep.
 */
@Composable
fun ReadmeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().shimmerSweep()) {
        SkeletonBar(fraction = 0.45f, height = 16.dp)
        Spacer(Modifier.height(14.dp))
        SkeletonBar(fraction = 1f, height = 11.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.94f, height = 11.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.88f, height = 11.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.55f, height = 11.dp)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(14.dp))
        SkeletonBar(fraction = 0.97f, height = 11.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.7f, height = 11.dp)
    }
}

/**
 * One rounded grey line. On its own it is static; put a group of them inside a container with
 * [shimmerSweep] to make them shimmer together.
 */
@Composable
fun SkeletonBar(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** A grey capsule of a fixed size. Pass width = 0.dp with a weight modifier to let it stretch. */
@Composable
private fun SkeletonPill(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (width > 0.dp) Modifier.width(width) else Modifier)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
