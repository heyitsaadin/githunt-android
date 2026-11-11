package com.githunt.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/** Picture url -> decoded width / height, for the whole session, so a re-opened README doesn't jump. */
private val NaturalSizeCache = HashMap<String, Pair<Float, Float>>()

/**
 * Renders a GitHub-flavoured README the way GitHub lays it out: Markdown text, centred headers and
 * logos, rows of badges, animated GIFs, SVG, and playable videos (YouTube, GitHub uploads, .mp4).
 * The text itself is the shared [MarkdownBody], so READMEs and the Changelog look identical. The
 * README is first cut into blocks by [parseReadme]; relative image paths are resolved against the
 * repo, like the website's resolveImageUrl.
 */
@Composable
fun ReadmeView(
    markdown: String,
    owner: String,
    repo: String,
    branch: String?,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown, owner, repo, branch) { parseReadme(markdown, owner, repo, branch) }
    val dark = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val uriHandler = LocalUriHandler.current
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // Every non-badge picture, so the full-screen preview can swipe through the whole README.
    val gallery = remember(blocks, dark) {
        blocks.filterIsInstance<ReadmeBlock.PictureRow>()
            .flatMap { it.items }
            .filter { !it.badge }
            .map { it.displayUrl(dark) }
            .distinct()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is ReadmeBlock.Markdown -> MarkdownBody(markdown = block.text, centered = block.centered)
                is ReadmeBlock.PictureRow -> PictureRowView(
                    row = block,
                    dark = dark,
                    onOpen = { previewUrl = it },
                    onLink = { runCatching { uriHandler.openUri(it) } },
                )
                is ReadmeBlock.YouTube -> ReadmeYouTube(block.id)
                is ReadmeBlock.Video -> DirectVideo(
                    url = block.url,
                    poster = block.poster,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
    }

    val open = previewUrl
    if (open != null && gallery.isNotEmpty()) {
        ImagePreviewDialog(
            images = gallery,
            startIndex = gallery.indexOf(open).coerceAtLeast(0),
            contentDescription = "$repo README image",
            onDismiss = { previewUrl = null },
            onOpenGithub = {
                previewUrl = null
                runCatching { uriHandler.openUri("https://github.com/$owner/$repo") }
            },
        )
    }
}

@Composable
private fun ReadmeYouTube(id: String) {
    var playing by remember(id) { mutableStateOf(false) }
    YouTubeVideo(
        id = id,
        playing = playing,
        onPlay = { playing = true },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
        contentDescription = "YouTube video",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PictureRowView(
    row: ReadmeBlock.PictureRow,
    dark: Boolean,
    onOpen: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (row.centered) {
                Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(6.dp)
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (pic in row.items) {
                ReadmePictureView(pic = pic, dark = dark, maxWidth = maxW, onOpen = onOpen, onLink = onLink)
            }
        }
    }
}

/**
 * One README picture, sized like GitHub: an explicit `width` wins, badges are 20dp tall, anything
 * else is its own size but never wider than the card. Works for GIF (animated), SVG and bitmaps.
 * A picture that can't be loaded takes no space.
 */
@Composable
private fun ReadmePictureView(
    pic: ReadmePicture,
    dark: Boolean,
    maxWidth: Dp,
    onOpen: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val url = pic.displayUrl(dark)
    var failed by remember(url) { mutableStateOf(false) }
    var natural by remember(url) { mutableStateOf(NaturalSizeCache[url]) }
    if (failed) return

    val nat = natural
    val ratio = when {
        nat != null -> nat.first / nat.second
        pic.badge -> 4.5f
        else -> 16f / 9f
    }
    val fraction = pic.widthFraction
    val explicit = pic.widthDp
    val wanted: Dp = when {
        explicit != null -> explicit.dp
        fraction != null -> maxWidth * fraction
        pic.badge -> 20.dp * ratio
        nat != null -> nat.first.dp
        else -> maxWidth
    }
    val width = minOf(wanted, maxWidth)

    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(proxiedImageUrl(url))
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(250)
            .build()
    }
    val link = pic.link
    val clickable = link != null || !pic.badge

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(if (pic.badge) 3.dp else 8.dp))
            .clickable(enabled = clickable) {
                if (link != null) onLink(link) else onOpen(url)
            },
    ) {
        ShimmerImage(
            model = request,
            contentDescription = pic.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onFailed = { failed = true },
            onLoaded = { w, h ->
                val known = natural
                if (known == null || known.first != w || known.second != h) {
                    natural = w to h
                    NaturalSizeCache[url] = w to h
                }
            },
        )
    }
}
