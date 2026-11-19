package com.githunt.android.ui.feed

import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.githunt.android.data.discover.RailShelf
import com.githunt.android.data.discover.RailShelfStore
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.ui.components.CardIcons
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.ShimmerBlock
import com.githunt.android.ui.components.ShimmerImage
import com.githunt.android.ui.components.formatCount
import com.githunt.android.ui.components.proxiedImageUrl
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * The website's RandomRepoRail: a themed shelf of big rounded cards you flick through sideways
 * (styled after the Play Store's "Events happening now" carousel).
 *
 * Each card is a 2:1 banner (README image, "Updated ..." badge top-left, headline + one-line
 * subtitle over a dark gradient) and an app row under it (icon, name, owner, a split "GitHub"
 * button that opens the repo) with stars, forks and language.
 *
 * - Only repos that have a README image are shown; if a repo's image fails to load the next README
 *   image is tried, and the card is dropped once all of them fail.
 * - The row snaps card by card and the next card peeks in from the right as the scroll cue.
 * - Scroll feel, like the website: cards shrink slightly the further they are from rest, and each
 *   banner image drifts slower than its card (parallax). Skipped when system animations are off.
 *
 * [slot] picks which shelf of [store] this rail shows; it draws nothing until that shelf has repos.
 */
@Composable
fun RepoRail(slot: Int, store: RailShelfStore) {
    val shelves by store.shelves.collectAsState()
    val shelf = shelves[slot]

    // Starts the load when the rail first composes, and again after a pull-to-refresh cleared it.
    LaunchedEffect(shelf == null) {
        if (shelf == null) store.ensure(slot)
    }

    when (shelf) {
        null -> Unit
        RailShelf.Empty -> Unit
        is RailShelf.Loading -> RailFrame(shelf.label) { RailSkeleton() }
        is RailShelf.Ready -> ReadyRail(shelf)
    }
}

// How far the row bleeds past the feed's 16dp side padding, and the row's own start/end padding.
private val RailEdge = 16.dp
private val RailGap = 12.dp
private val CardShape = RoundedCornerShape(28.dp)

// Scroll effect, same numbers as the website: 4.5% shrink at a full card away, 7% parallax.
private const val SCALE_DROP = 0.045f
private const val PARALLAX = 0.07f
private const val IMG_SCALE = 1.16f

/** Lets the scrolling row run edge to edge even though the feed list pads its content. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.maxWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun RailFrame(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        Text(
            text = label,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun RailSkeleton() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().bleed(RailEdge)) {
        val cardWidth = minOf(maxWidth * 0.86f, 400.dp)
        Row(
            modifier = Modifier.padding(horizontal = RailEdge),
            horizontalArrangement = Arrangement.spacedBy(RailGap),
        ) {
            repeat(2) {
                ShimmerBlock(Modifier.width(cardWidth).height(250.dp).clip(CardShape))
            }
        }
    }
}

@Composable
private fun ReadyRail(shelf: RailShelf.Ready) {
    // fullName -> how many of that repo's README images failed to load, so a card can fall through
    // to its next image before it is dropped.
    val failures = remember(shelf) { mutableStateMapOf<String, Int>() }
    val visible = shelf.repos.filter { (failures[it.fullName] ?: 0) < it.readmeImages.size }
    if (visible.isEmpty()) return

    RailFrame(shelf.label) {
        RailCards(repos = visible, failures = failures)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailCards(repos: List<RepoItem>, failures: MutableMap<String, Int>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val palette = rememberRailPalette()
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Where a card's left edge is relative to its resting (snapped) position, in px. Read while
    // drawing, so scrolling moves the cards without recomposing anything.
    val offsetOf: (Int) -> Float = { index ->
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.toFloat() ?: 0f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().bleed(RailEdge)) {
        // Wide enough that the next card peeks in, capped so it doesn't get huge on tablets.
        val cardWidth = minOf(maxWidth * 0.86f, 400.dp)
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = RailEdge),
            horizontalArrangement = Arrangement.spacedBy(RailGap),
        ) {
            itemsIndexed(repos, key = { _, repo -> repo.fullName }) { index, repo ->
                val imageIndex = (failures[repo.fullName] ?: 0).coerceAtMost(repo.readmeImages.lastIndex)
                RailCard(
                    repo = repo,
                    image = repo.readmeImages[imageIndex],
                    onImageFailed = { failures[repo.fullName] = (failures[repo.fullName] ?: 0) + 1 },
                    width = cardWidth,
                    palette = palette,
                    offsetPx = { offsetOf(index) },
                    animate = !reduceMotion,
                )
            }
        }
    }
}

private class RailPalette(val btnBg: Color, val btnText: Color, val accent: Color)

/** Same as the website: tonal light-blue button in dark mode, solid blue with white text in light. */
@Composable
private fun rememberRailPalette(): RailPalette {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return remember(light) {
        if (light) RailPalette(Color(0xFF0B57D0), Color.White, Color(0xFF0B57D0))
        else RailPalette(Color(0xFFA8C7FA), Color(0xFF062E6F), Color(0xFFA8C7FA))
    }
}

@Composable
private fun RailCard(
    repo: RepoItem,
    image: String,
    onImageFailed: () -> Unit,
    width: Dp,
    palette: RailPalette,
    offsetPx: () -> Float,
    animate: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val repoName = repo.fullName.substringAfter('/')
    val headline = repo.description?.trim()?.takeIf { it.isNotEmpty() } ?: repoName
    val sub = repo.readmeSnippet?.trim()?.takeIf { it.isNotEmpty() }
    val updated = updatedAgo(repo.updatedAt)
    val openRepo = {
        runCatching { uriHandler.openUri(repo.url ?: "https://github.com/${repo.fullName}") }
        Unit
    }

    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                if (animate) {
                    val t = (abs(offsetPx()) / size.width).coerceIn(0f, 1f)
                    val s = 1f - t * SCALE_DROP
                    scaleX = s
                    scaleY = s
                }
            }
            .clip(CardShape)
            .background(scheme.surface)
            .border(1.5.dp, scheme.outline.copy(alpha = 0.85f), CardShape),
    ) {
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .background(scheme.surfaceVariant)
                .clipToBounds(),
        ) {
            val request = remember(image) {
                ImageRequest.Builder(context)
                    .data(proxiedImageUrl(image))
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(350)
                    .build()
            }
            ShimmerImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onFailed = onImageFailed,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Pre-scaled so the parallax shift never exposes an empty edge.
                        scaleX = IMG_SCALE
                        scaleY = IMG_SCALE
                        if (animate) translationX = -offsetPx() * PARALLAX
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.28f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
            )

            if (updated.isNotEmpty()) {
                Text(
                    text = "Updated $updated",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8C1D18),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF9DEDC))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            ) {
                Text(
                    text = headline,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // App row
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RailIcon(repo)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 2.dp)) {
                    Text(
                        text = repoName,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = repo.owner,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GithubSplitButton(palette = palette, onClick = openRepo)
                    Text(
                        text = "Public repo",
                        fontSize = 10.5.sp,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 12.dp).height(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatCount(repo.stars), fontSize = 13.sp, color = scheme.onBackground)
                    Spacer(Modifier.width(6.dp))
                    PathIcon(pathData = CardIcons.Star, tint = palette.accent, iconSize = 14.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.dp, scheme.onBackground.copy(alpha = 0.6f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PathIcon(
                            pathData = CardIcons.Fork,
                            tint = scheme.onBackground,
                            iconSize = 12.dp,
                            strokeWidth = 2.4f,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(text = "${formatCount(repo.forks)} forks", fontSize = 13.sp, color = scheme.onBackground)
                }
            }
            // Always reserves the language line so every card in the row is the same height.
            Box(modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(18.dp)) {
                repo.language?.let { language ->
                    Text(
                        text = language,
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The owner's avatar as a 52dp rounded square, or their initial if there isn't one. */
@Composable
private fun RailIcon(repo: RepoItem) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outline.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = repo.avatarUrl
        if (avatar != null) {
            val request = remember(avatar) {
                ImageRequest.Builder(context).data(proxiedImageUrl(avatar)).crossfade(250).build()
            }
            ShimmerImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = repo.owner.firstOrNull()?.uppercase() ?: "?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )
        }
    }
}

/** Play Store's "Install + dropdown" split button; the second half is a redirect arrow. */
@Composable
private fun GithubSplitButton(palette: RailPalette, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(topStart = 19.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 19.dp))
                .background(palette.btnBg)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathIcon(pathData = CardIcons.GitHub, tint = palette.btnText, iconSize = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "GitHub",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.btnText,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 38.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 19.dp, bottomEnd = 19.dp, bottomStart = 6.dp))
                .background(palette.btnBg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PathIcon(
                pathData = "M7 17L17 7 M8 7h9v9",
                tint = palette.btnText,
                iconSize = 16.dp,
                strokeWidth = 2.4f,
            )
        }
    }
}

/** "just now", "5h ago", "3d ago", "2mo ago", "1y ago" (the website's wording for this card). */
private fun updatedAgo(iso: String?): String {
    if (iso == null) return ""
    val secs = runCatching { Duration.between(Instant.parse(iso), Instant.now()).seconds }.getOrNull()
        ?: return ""
    return when {
        secs < 3_600 -> "just now"
        secs < 86_400 -> "${secs / 3_600}h ago"
        secs < 86_400 * 30 -> "${secs / 86_400}d ago"
        secs < 86_400 * 365 -> "${secs / (86_400 * 30)}mo ago"
        else -> "${secs / (86_400 * 365)}y ago"
    }
}
