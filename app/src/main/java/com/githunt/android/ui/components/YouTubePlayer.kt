package com.githunt.android.ui.components

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.githunt.android.BuildConfig
import com.githunt.android.data.feed.YouTubeIdRegex

/** Slides in the card carousel are plain strings; a video slide is `yt:<id>`, a picture is its url. */
internal const val VIDEO_SLIDE_PREFIX = "yt:"

internal fun videoSlide(id: String): String = VIDEO_SLIDE_PREFIX + id

internal fun isVideoSlide(slide: String): Boolean = slide.startsWith(VIDEO_SLIDE_PREFIX)

/** Videos are always shown as a 16:9 player, like the website. */
internal const val VIDEO_RATIO = 16f / 9f

/**
 * A YouTube video inside the repo card, like the website's YouTubeEmbed.
 *
 * It starts as YouTube's own thumbnail with a play button, so nothing but one small picture loads
 * until the person taps. Tapping swaps in the real player (youtube-nocookie, autoplay) right in the
 * card, with a close button in the corner. Nothing here leaves the app unless the video can't be
 * embedded, in which case YouTube's own "Watch on YouTube" link opens in the YouTube app/browser.
 *
 * Playback stops by itself when this slide stops being the active one (swiped away), the app goes
 * to the background, or the card scrolls out of the list.
 *
 * @param active false when this slide is not the one in view (it is composed next to the current
 *   page by the pager, so it has to be told to stop).
 * @param onPlayingChange the carousel uses this to pause its auto-advance and hide its dots.
 */
@Composable
internal fun YouTubeSlide(
    videoId: String,
    title: String,
    active: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var playing by remember(videoId) { mutableStateOf(false) }

    DisposableEffect(playing) {
        if (playing) onPlayingChange(true)
        onDispose { if (playing) onPlayingChange(false) }
    }
    LaunchedEffect(active) { if (!active) playing = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playing = false }

    Box(modifier = modifier.background(Color.Black)) {
        if (playing) {
            YouTubeWebView(videoId = videoId, modifier = Modifier.fillMaxSize())
            CloseButton(
                onClick = { playing = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        } else {
            YouTubePoster(
                videoId = videoId,
                title = title,
                onPlay = { playing = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** YouTube's thumbnail with the red play button. maxres is the true 16:9 frame; hq always exists. */
@Composable
private fun YouTubePoster(
    videoId: String,
    title: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posters = remember(videoId) {
        listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        )
    }
    var posterIndex by remember(videoId) { mutableIntStateOf(0) }
    fun nextPoster() {
        if (posterIndex < posters.lastIndex) posterIndex += 1
    }

    Box(
        modifier = modifier.clickable(onClickLabel = "Play video", onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        ShimmerImage(
            model = posters[posterIndex],
            contentDescription = "Video thumbnail: $title",
            // YouTube's thumbnails are 16:9 frames (hq has bars baked in), so Crop fills the 16:9
            // box exactly and trims those bars.
            contentScale = ContentScale.Crop,
            onFailed = { nextPoster() },
            // A missing maxres frame can come back as a tiny placeholder instead of an error.
            onLoaded = { width, _ -> if (width <= 120f) nextPoster() },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFF0000)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                // The same triangle as the website's play icon (24x24 grid).
                val s = size.width / 24f
                val triangle = Path().apply {
                    moveTo(8f * s, 5f * s)
                    lineTo(8f * s, 19f * s)
                    lineTo(19f * s, 12f * s)
                    close()
                }
                drawPath(triangle, Color.White)
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClickLabel = "Close video", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

/**
 * YouTube sends an error ("video player configuration error") when a player is embedded without a
 * referrer, which a WebView does not send on its own. Loading the page with a real https origin as
 * its base URL fixes that: the website's own address when it is https, else YouTube's.
 */
private val EmbedOrigin: String =
    BuildConfig.API_BASE_URL.trimEnd('/').takeIf { it.startsWith("https://") }
        ?: "https://www.youtube-nocookie.com"

private fun embedHtml(id: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
</style>
</head>
<body>
<iframe
  src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1"
  allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
  allowfullscreen
  referrerpolicy="strict-origin-when-cross-origin"></iframe>
</body>
</html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebView(videoId: String, modifier: Modifier = Modifier) {
    // The id goes into HTML, so only ever accept a well-formed one.
    if (!YouTubeIdRegex.matches(videoId)) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = ExternalLinksWebViewClient(context)
                webChromeClient = FullscreenChromeClient(context)
                loadDataWithBaseURL(EmbedOrigin, embedHtml(videoId), "text/html", "utf-8", null)
            }
        },
        onRelease = { webView ->
            // Stops the video and its sound as soon as the player leaves the screen.
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

/** Anything that tries to leave the player page ("Watch on YouTube", a channel link) opens outside. */
private class ExternalLinksWebViewClient(private val context: Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }
}

/** The player's full-screen button: shows the player's full-screen view in a black dialog. */
private class FullscreenChromeClient(private val context: Context) : WebChromeClient() {
    private var dialog: Dialog? = null
    private var callback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (dialog != null) {
            callback.onCustomViewHidden()
            return
        }
        this.callback = callback
        dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            // Back button / swipe back: tell the page it is no longer full screen.
            setOnDismissListener {
                val pending = this@FullscreenChromeClient.callback
                this@FullscreenChromeClient.dialog = null
                this@FullscreenChromeClient.callback = null
                pending?.onCustomViewHidden()
            }
            show()
        }
    }

    override fun onHideCustomView() {
        val shown = dialog ?: return
        shown.setOnDismissListener(null)
        dialog = null
        callback = null
        shown.dismiss()
    }
}
