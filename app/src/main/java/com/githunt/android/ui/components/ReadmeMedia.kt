package com.githunt.android.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.request.ImageRequest
import com.githunt.android.ui.theme.LocalGhMono

private fun openExternally(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Round dark button with a white play triangle, drawn on top of a video's picture. */
@Composable
internal fun PlayBadge(modifier: Modifier = Modifier, diameter: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.38f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.18f, 0f)
                lineTo(w, h / 2f)
                lineTo(w * 0.18f, h)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
private fun VideoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = LocalGhMono.current,
        fontSize = 10.sp,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A YouTube video: its thumbnail with a play button, and the real player once [playing] is true.
 * The size comes from [modifier] (callers give it a 16:9 box).
 */
@Composable
fun YouTubeVideo(
    id: String,
    playing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var hdFailed by remember(id) { mutableStateOf(false) }
    var embedFailed by remember(id) { mutableStateOf(false) }

    Box(modifier.background(Color.Black)) {
        if (playing && !embedFailed) {
            YouTubeWebPlayer(id = id, onFailed = { embedFailed = true }, modifier = Modifier.fillMaxSize())
        } else {
            // The full-size thumbnail is missing for some videos, so fall back to the 4:3 one,
            // whose black bars are cropped away by the 16:9 box.
            val thumb = if (hdFailed) {
                "https://img.youtube.com/vi/$id/hqdefault.jpg"
            } else {
                "https://img.youtube.com/vi/$id/maxresdefault.jpg"
            }
            val request = remember(thumb) { ImageRequest.Builder(context).data(thumb).crossfade(250).build() }
            ShimmerImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onFailed = { if (!hdFailed) hdFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().clickable {
                    if (embedFailed) openExternally(context, "https://www.youtube.com/watch?v=$id") else onPlay()
                },
            )
            PlayBadge(Modifier.align(Alignment.Center))
            VideoLabel(
                text = if (embedFailed) "Can't play here \u00B7 open in YouTube" else "YouTube",
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

private class CustomViewHolder(val view: View, val callback: WebChromeClient.CustomViewCallback)

/** The YouTube embed page in a WebView, with working full screen. Released when it leaves the screen. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebPlayer(id: String, onFailed: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fullscreen by remember { mutableStateOf<CustomViewHolder?>(null) }

    val webView = remember(id) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    fullscreen = CustomViewHolder(view, callback)
                }

                override fun onHideCustomView() {
                    fullscreen = null
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    val onEmbed = uri.host.orEmpty().endsWith("youtube.com") &&
                        uri.path.orEmpty().startsWith("/embed/")
                    if (onEmbed) return false
                    // Title / "Watch on YouTube" taps leave the embed: open them outside the card.
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) onFailed()
                }
            }
            // YouTube wants an app identity on embeds from Android; the Referer provides it.
            loadUrl(
                "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1",
                mapOf("Referer" to "https://${context.packageName}"),
            )
        }
    }

    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)

    val custom = fullscreen
    if (custom != null) {
        Dialog(
            onDismissRequest = {
                custom.callback.onCustomViewHidden()
                fullscreen = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AndroidView(
                factory = {
                    (custom.view.parent as? ViewGroup)?.removeView(custom.view)
                    custom.view
                },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/** A video file (GitHub upload, .mp4, .webm): a poster with a play button, then an in-place player. */
@Composable
fun DirectVideo(url: String, poster: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }

    Box(modifier.background(Color.Black)) {
        if (playing && !failed) {
            ExoVideo(
                url = url,
                onError = {
                    failed = true
                    playing = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (poster != null) {
                val request = remember(poster) {
                    ImageRequest.Builder(context).data(proxiedImageUrl(poster)).crossfade(250).build()
                }
                ShimmerImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().clickable {
                    if (failed) openExternally(context, url) else playing = true
                },
            )
            PlayBadge(Modifier.align(Alignment.Center))
            VideoLabel(
                text = if (failed) "Can't play here \u00B7 open in browser" else "Video",
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun ExoVideo(url: String, onError: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(lifecycle, player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
    )
}
