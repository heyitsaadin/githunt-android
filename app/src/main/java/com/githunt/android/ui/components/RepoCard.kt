package com.githunt.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.githunt.android.BuildConfig
import com.githunt.android.data.feed.RepoItem
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/** The website uses a monospace face for stats, times, topic chips and pill buttons. */
private val Mono = FontFamily.Monospace

/**
 * One repo, as the website draws it. Used by Discover and the profile tabs.
 * Pass [onClipToggle] to show the bookmark; [clipped] says whether it is filled.
 */
@Composable
fun RepoCard(
    item: RepoItem,
    modifier: Modifier = Modifier,
    clipped: Boolean = false,
    onClipToggle: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val githubUrl = item.url
    val readmeUrl = githubUrl?.trimEnd('/')?.plus("#readme")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape)
            .clickable(enabled = githubUrl != null) {
                githubUrl?.let { runCatching { uriHandler.openUri(it) } }
            },
    ) {
        // The website's full-bleed README media preview. Shows a shimmer while it loads
        // and disappears completely if the image can't be loaded, like the website.
        item.readmeImages.firstOrNull()?.let { preview ->
            RepoPreview(
                url = preview,
                contentDescription = "${item.fullName} README preview",
                onClick = { runCatching { uriHandler.openUri(githubUrl ?: preview) } },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepoAvatar(url = item.avatarUrl, size = 36.dp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fullName.substringAfter('/'),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.owner,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.5.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val ago = relativeTime(item.updatedAt)
                        if (ago.isNotEmpty()) {
                            Text(
                                text = " \u00B7 $ago",
                                fontFamily = Mono,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            item.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            item.readmeSnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.topics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.topics.take(3).forEach { topic ->
                        Text(
                            text = topic,
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .border(1.dp, scheme.outline.copy(alpha = 0.65f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(
                    text = "Show README",
                    modifier = Modifier.weight(1f),
                    onClick = { readmeUrl?.let { runCatching { uriHandler.openUri(it) } } },
                )
                ActionPill(
                    text = "Open GitHub  \u2197",
                    modifier = Modifier.weight(1f),
                    onClick = { githubUrl?.let { runCatching { uriHandler.openUri(it) } } },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatChip(icon = CardIcons.Star, value = formatCount(item.stars), tint = scheme.onSurfaceVariant)
                StatChip(icon = CardIcons.Fork, value = formatCount(item.forks), tint = scheme.onSurfaceVariant)
                item.language?.let { language ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        languageColor(language)?.let { dot ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dot),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = language,
                            fontFamily = Mono,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardAction(
                    icon = CardIcons.Share,
                    label = "Share",
                    tint = scheme.onSurfaceVariant,
                    onClick = {
                        githubUrl?.let { link ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, link)
                            }
                            context.startActivity(Intent.createChooser(send, "Share repository"))
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                if (onClipToggle != null) {
                    CardAction(
                        icon = CardIcons.Clip,
                        filled = clipped,
                        label = if (clipped) "Clipped" else "Clip",
                        tint = if (clipped) scheme.onBackground else scheme.onSurfaceVariant,
                        onClick = onClipToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(icon: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PathIcon(pathData = icon, tint = tint, iconSize = 14.dp, strokeWidth = 1.8f)
        Spacer(Modifier.width(4.dp))
        Text(text = value, fontFamily = Mono, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun CardAction(
    icon: String,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathIcon(pathData = icon, tint = tint, iconSize = 18.dp, strokeWidth = 1.8f, filled = filled)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 12.sp,
            color = tint,
        )
    }
}

@Composable
private fun ActionPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = Mono,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Hosts the website's /api/img-proxy accepts. Anything else is loaded directly. */
private val ProxyHosts = setOf(
    "raw.githubusercontent.com",
    "user-images.githubusercontent.com",
    "camo.githubusercontent.com",
    "github.com",
    "avatars.githubusercontent.com",
)

/**
 * Routes GitHub-hosted images through the backend's /api/img-proxy, the same as the website,
 * so they come from one cached origin instead of hotlinking GitHub on every view.
 */
private fun proxiedImageUrl(src: String): String {
    val host = runCatching { Uri.parse(src).host }.getOrNull()
    if (host == null || host !in ProxyHosts) return src
    return "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/img-proxy?src=${Uri.encode(src)}"
}

@Composable
private fun PreviewSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "preview-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "preview-shimmer-alpha",
    )
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
}

/**
 * The card's 16:9 README picture. README images are often SVG, so the SVG decoder is on.
 * While loading it shows a shimmer; if loading fails the whole block is removed so the
 * card never carries a big empty box (the website's SmartImage does the same).
 */
@Composable
private fun RepoPreview(url: String, contentDescription: String, onClick: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    if (!failed) {
        val context = LocalContext.current
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(proxiedImageUrl(url))
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(true)
                .build()
        }
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            loading = { PreviewSkeleton(Modifier.fillMaxSize()) },
            onError = { failed = true },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
fun RepoAvatar(url: String?, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(
                model = proxiedImageUrl(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Grey placeholder shaped like [RepoCard], shown while a list loads. */
@Composable
fun RepoCardSkeleton(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SkeletonBar(fraction = 0.5f, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(fraction = 0.3f, height = 10.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        SkeletonBar(fraction = 1f, height = 12.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.85f, height = 12.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(fraction = 0.6f, height = 12.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonBar(fraction = 0.4f, height = 10.dp)
    }
}

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

/** "950", "1.2k", "19k", "1.5M". Drops a trailing ".0" like the website does. */
fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1f", n / 1_000_000.0).removeSuffix(".0") + "M"
    n >= 1_000 -> String.format(Locale.US, "%.1f", n / 1_000.0).removeSuffix(".0") + "k"
    else -> n.toString()
}

/** "now", "5m", "3h", "2d", "4mo" from an ISO timestamp. Empty when it can't be read. */
fun relativeTime(value: String?): String {
    if (value == null) return ""
    return try {
        val elapsed = Duration.between(Instant.parse(value), Instant.now())
        when {
            elapsed.toMinutes() < 1 -> "now"
            elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m"
            elapsed.toDays() < 1 -> "${elapsed.toHours()}h"
            elapsed.toDays() < 30 -> "${elapsed.toDays()}d"
            else -> "${elapsed.toDays() / 30}mo"
        }
    } catch (_: DateTimeParseException) {
        ""
    }
}

private val LanguageColors = mapOf(
    "JavaScript" to 0xFFF1E05A,
    "TypeScript" to 0xFF3178C6,
    "Python" to 0xFF3572A5,
    "Java" to 0xFFB07219,
    "Go" to 0xFF00ADD8,
    "Rust" to 0xFFDEA584,
    "C++" to 0xFFF34B7D,
    "C" to 0xFF555555,
    "C#" to 0xFF178600,
    "PHP" to 0xFF4F5D95,
    "Ruby" to 0xFF701516,
    "Swift" to 0xFFF05138,
    "Kotlin" to 0xFFA97BFF,
    "Dart" to 0xFF00B4AB,
    "HTML" to 0xFFE34C26,
    "CSS" to 0xFF563D7C,
    "Shell" to 0xFF89E051,
)

/** GitHub's own colour for a language, or null when we don't have one. */
fun languageColor(language: String?): Color? =
    language?.let { name -> LanguageColors[name]?.let { Color(it) } }
