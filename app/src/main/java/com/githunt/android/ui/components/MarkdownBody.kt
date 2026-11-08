package com.githunt.android.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.text.Spanned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import org.commonmark.node.Heading

/**
 * The one Markdown renderer in the app: README text inside repo cards and the Changelog page both
 * use it, so they always look the same.
 *
 * It follows the website's ReadmeBlocks rather than Markwon's defaults:
 *  - headings are only a little bigger than the body text, semi-bold and in the full foreground
 *    colour (the web uses 16 / 15 / 14 / 13.5px), with no rule line underneath;
 *  - body text is the dim colour at 13.5, links are underlined;
 *  - block quotes get a thin left bar, code gets a soft background and a monospace face.
 *
 * The text is parsed once per markdown string (not on every recomposition), so scrolling a card
 * past the screen edge and back does not re-layout or flicker.
 */
@Composable
fun MarkdownBody(
    markdown: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 13.5f,
    renderer: Markwon? = null,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurfaceVariant.toArgb()
    val headingColor = scheme.onBackground.toArgb()
    val linkColor = scheme.onBackground.toArgb()
    val ruleColor = scheme.outline.copy(alpha = 0.6f).toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val density = context.resources.displayMetrics.density

    val markwon = remember(context, textColor, headingColor, linkColor, ruleColor, codeBackground) {
        buildMarkdownRenderer(context, textColor, headingColor, linkColor, ruleColor, codeBackground, density)
    }
    // The first text is parsed right here, exactly as before, so a card scrolled back into view
    // has its README at full height straight away. When the text later CHANGES (a README being
    // translated), the new text is parsed on a background thread and swapped in once ready, with
    // the old text staying on screen meanwhile, so the UI never freezes while it works.
    var rendered: Spanned by remember(markwon) { mutableStateOf(markwon.toMarkdown(markdown)) }
    var renderedFor by remember(markwon) { mutableStateOf(markdown) }
    val latestMarkdown by rememberUpdatedState(markdown)
    // A Markwon instance isn't safe to run twice at once (its HTML plugin keeps state), so one
    // render at a time per instance. When a newer text is already waiting, the older one is skipped.
    val renderLock = remember(markwon) { Mutex() }
    LaunchedEffect(markdown, markwon) {
        if (markdown == renderedFor) return@LaunchedEffect
        val next = renderLock.withLock {
            if (markdown != latestMarkdown) {
                null
            } else {
                withContext(Dispatchers.Default) { markwon.toMarkdown(markdown) }
            }
        } ?: return@LaunchedEffect
        rendered = next
        renderedFor = markdown
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = textSizeSp
                setLineSpacing(0f, 1.35f)
                includeFontPadding = false
                val pad = (2f * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            if (view.tag !== rendered) {
                view.tag = rendered
                markwon.setParsedMarkdown(view, rendered)
            }
        },
    )
}

private fun buildMarkdownRenderer(
    context: Context,
    text: Int,
    heading: Int,
    link: Int,
    rule: Int,
    codeBackground: Int,
    density: Float,
): Markwon {
    fun px(dp: Float): Int = (dp * density).toInt()
    return Markwon.builder(context)
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .linkColor(link)
                    .blockMargin(px(16f))
                    .blockQuoteWidth(px(2f))
                    .blockQuoteColor(rule)
                    .listItemColor(text)
                    .bulletWidth(px(5f))
                    // Headings: barely bigger than the body, and no line under h1 / h2.
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(floatArrayOf(1.19f, 1.11f, 1.04f, 1f, 1f, 1f))
                    .thematicBreakColor(rule)
                    .thematicBreakHeight(px(1f))
                    .codeTextColor(text)
                    .codeBackgroundColor(codeBackground)
                    .codeTypeface(Typeface.MONOSPACE)
                    .codeBlockTextColor(text)
                    .codeBlockBackgroundColor(codeBackground)
                    .codeBlockTypeface(Typeface.MONOSPACE)
                    .codeBlockMargin(px(8f))
            }

            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                // Headings read in the full foreground colour, the body in the dim one.
                builder.appendFactory(Heading::class.java, SpanFactory { _, _ -> ForegroundColorSpan(heading) })
            }
        })
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .build()
}
