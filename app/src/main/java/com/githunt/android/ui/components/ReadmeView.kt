package com.githunt.android.ui.components

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin

/** Past this many characters the README is cut, so one huge file can't make a card thousands of screens tall. */
private const val MaxReadmeChars = 15_000

private val MarkdownImage = Regex("""(!\[[^\]]*]\()([^)\s]+)((?:\s+"[^"]*")?\))""")
private val HtmlImage = Regex("""(<img[^>]*?\ssrc=["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)

/**
 * Renders GitHub-flavoured README markdown (headings, tables, task lists, code, inline HTML)
 * the way the website's ReadmeBlocks does. Relative image paths are resolved against the repo,
 * like the website's resolveImageUrl.
 */
@Composable
fun ReadmeView(
    markdown: String,
    owner: String,
    repo: String,
    branch: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurfaceVariant.toArgb()
    val linkColor = scheme.onBackground.toArgb()
    val codeBg = scheme.surfaceVariant.toArgb()
    val markwon = remember(context, textColor, linkColor, codeBg) {
        buildMarkwon(context, textColor, linkColor, codeBg)
    }
    val prepared = remember(markdown, owner, repo, branch) { prepareReadme(markdown, owner, repo, branch) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 13.5f
                setLineSpacing(0f, 1.25f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            // Re-parsing on every recomposition would be slow, so only do it when the text changed.
            val key = prepared to markwon
            if (view.tag != key) {
                view.tag = key
                markwon.setMarkdown(view, prepared)
            }
        },
    )
}

private fun buildMarkwon(context: Context, text: Int, link: Int, codeBg: Int): Markwon =
    Markwon.builder(context)
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .linkColor(link)
                    .codeTextColor(text)
                    .codeBackgroundColor(codeBg)
                    .codeBlockTextColor(text)
                    .codeBlockBackgroundColor(codeBg)
            }
        })
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .build()

/** Cuts very long READMEs and turns relative image paths into raw.githubusercontent.com links. */
internal fun prepareReadme(markdown: String, owner: String, repo: String, branch: String?): String {
    val base = "https://raw.githubusercontent.com/$owner/$repo/${branch ?: "HEAD"}/"

    fun resolve(src: String): String = when {
        src.startsWith("http://") || src.startsWith("https://") || src.startsWith("data:") -> src
        src.startsWith("//") -> "https:$src"
        else -> base + src.removePrefix("./").removePrefix("/")
    }

    var text = markdown
    if (text.length > MaxReadmeChars) {
        text = text.take(MaxReadmeChars).substringBeforeLast("\n\n") + "\n\n\u2026"
    }
    text = MarkdownImage.replace(text) { m ->
        m.groupValues[1] + resolve(m.groupValues[2]) + m.groupValues[3]
    }
    text = HtmlImage.replace(text) { m ->
        m.groupValues[1] + resolve(m.groupValues[2]) + m.groupValues[3]
    }
    return text
}
