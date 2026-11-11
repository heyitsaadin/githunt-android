package com.githunt.android.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import org.commonmark.node.Code
import org.commonmark.node.Heading

/**
 * The one Markdown renderer in the app: README text inside repo cards and the Changelog page both
 * use it, so they always look the same.
 *
 * It follows the website's ReadmeBlocks, with GitHub's own touches for the parts that need to be
 * easy to scan:
 *  - headings are only a little bigger than the body text, semi-bold and in the full foreground
 *    colour, with no rule line underneath;
 *  - body text is the dim colour at 13.5, links are GitHub blue and underlined;
 *  - inline `code` is a rounded chip in the bright foreground colour (like the GitHub app), and
 *    still wraps cleanly when a long command runs past the end of a line;
 *  - block quotes get a thin left bar, code blocks get a soft background and a monospace face.
 *
 * The text is parsed once per markdown string (not on every recomposition), so scrolling a card
 * past the screen edge and back does not re-layout or flicker.
 */
@Composable
fun MarkdownBody(
    markdown: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 13.5f,
    /** Centres the text, for README blocks inside `align="center"`. */
    centered: Boolean = false,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    val textColor = scheme.onSurfaceVariant.toArgb()
    val headingColor = scheme.onBackground.toArgb()
    // GitHub's own link blues for dark and light.
    val linkColor = (if (isLight) Color(0xFF0969DA) else Color(0xFF4493F8)).toArgb()
    val ruleColor = scheme.outline.copy(alpha = 0.6f).toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val codeTextColor = scheme.onBackground.toArgb()
    val chipColor = scheme.onBackground.copy(alpha = if (isLight) 0.08f else 0.14f).toArgb()
    val density = context.resources.displayMetrics.density

    val markwon = remember(context, textColor, headingColor, linkColor, ruleColor, codeBackground, codeTextColor) {
        buildMarkdownRenderer(
            context, textColor, headingColor, linkColor, ruleColor, codeBackground, codeTextColor, density,
        )
    }
    val rendered = remember(markdown, markwon) { markwon.toMarkdown(markdown) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            MarkdownTextView(ctx).apply {
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
            view.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
            view.chipColor = chipColor
            if (view.tag !== rendered) {
                view.tag = rendered
                markwon.setParsedMarkdown(view, rendered)
            }
        },
    )
}

/**
 * Marks inline code. It only sets the monospace face, size and colour; the rounded background is
 * painted by [MarkdownTextView], because a plain background span can't have rounded corners.
 * (Markwon already pads inline code with a non-breaking space on each side, and those are inside
 * this span, so the chip gets its breathing room for free.)
 */
private class InlineCodeSpan(private val textColor: Int) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        style(paint)
    }

    override fun updateDrawState(paint: TextPaint) {
        style(paint)
        paint.color = textColor
        paint.bgColor = 0
    }

    private fun style(paint: TextPaint) {
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = paint.textSize * SizeRatio
    }

    companion object {
        const val SizeRatio = 0.87f
    }
}

/**
 * A TextView that paints a rounded chip behind every [InlineCodeSpan]. A chip that wraps onto the
 * next line is drawn as one piece per line, so long commands break naturally.
 */
private class MarkdownTextView(context: Context) : TextView(context) {
    var chipColor: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipRect = RectF()
    private val metricsPaint = TextPaint()

    override fun onDraw(canvas: Canvas) {
        drawInlineCodeChips(canvas)
        super.onDraw(canvas)
    }

    private fun drawInlineCodeChips(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        val spans = spanned.getSpans(0, spanned.length, InlineCodeSpan::class.java)
        if (spans.isEmpty()) return

        val density = resources.displayMetrics.density
        val radius = 4f * density
        val verticalPad = 1.5f * density
        metricsPaint.set(paint)
        metricsPaint.typeface = Typeface.MONOSPACE
        metricsPaint.textSize = paint.textSize * InlineCodeSpan.SizeRatio
        val fm = metricsPaint.fontMetrics

        chipPaint.color = chipColor
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        for (span in spans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (end <= start) continue
            val firstLine = textLayout.getLineForOffset(start)
            val lastLine = textLayout.getLineForOffset(end - 1)
            for (line in firstLine..lastLine) {
                val segStart = maxOf(start, textLayout.getLineStart(line))
                val segEnd = minOf(end, textLayout.getLineEnd(line))
                if (segEnd <= segStart) continue
                val left = textLayout.getPrimaryHorizontal(segStart)
                val right = if (segEnd >= textLayout.getLineVisibleEnd(line)) {
                    textLayout.getLineMax(line)
                } else {
                    textLayout.getPrimaryHorizontal(segEnd)
                }
                if (right <= left) continue
                val baseline = textLayout.getLineBaseline(line).toFloat()
                chipRect.set(left, baseline + fm.ascent - verticalPad, right, baseline + fm.descent + verticalPad)
                canvas.drawRoundRect(chipRect, radius, radius, chipPaint)
            }
        }
        canvas.restore()
    }
}

private fun buildMarkdownRenderer(
    context: Context,
    text: Int,
    heading: Int,
    link: Int,
    rule: Int,
    codeBackground: Int,
    codeText: Int,
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
                    // Inline code is styled by InlineCodeSpan; these are for fenced code blocks.
                    .codeTextColor(codeText)
                    .codeTypeface(Typeface.MONOSPACE)
                    .codeBlockTextColor(codeText)
                    .codeBlockBackgroundColor(codeBackground)
                    .codeBlockTypeface(Typeface.MONOSPACE)
                    .codeBlockMargin(px(8f))
            }

            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                // Headings read in the full foreground colour, the body in the dim one.
                builder.appendFactory(Heading::class.java, SpanFactory { _, _ -> ForegroundColorSpan(heading) })
                // Inline code: bright monospace text on a rounded chip (the chip is painted by
                // MarkdownTextView).
                builder.setFactory(Code::class.java, SpanFactory { _, _ -> InlineCodeSpan(codeText) })
            }
        })
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .build()
}
