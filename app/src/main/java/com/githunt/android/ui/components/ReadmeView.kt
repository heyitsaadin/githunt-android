package com.githunt.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Past this many characters the README is cut, so one huge file can't make a card thousands of screens tall. */
internal const val MaxReadmeChars = 15_000

/** A translation can run much longer than its source (CJK to English), so it gets more room. */
internal const val TranslatedReadmeMaxChars = 40_000

private val MarkdownImage = Regex("""(!\[[^\]]*]\()([^)\s]+)((?:\s+"[^"]*")?\))""")
private val HtmlImage = Regex("""(<img[^>]*?\ssrc=["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)

/**
 * Renders a GitHub-flavoured README (headings, tables, task lists, code, inline HTML) the way the
 * website's ReadmeBlocks does. The drawing itself is the shared [MarkdownBody], so READMEs and the
 * Changelog look identical. Relative image paths are resolved against the repo, like the website's
 * resolveImageUrl.
 */
@Composable
fun ReadmeView(
    markdown: String,
    owner: String,
    repo: String,
    branch: String?,
    modifier: Modifier = Modifier,
    maxChars: Int? = null,
) {
    val prepared = remember(markdown, owner, repo, branch, maxChars) {
        prepareReadme(markdown, owner, repo, branch, maxChars ?: MaxReadmeChars)
    }
    MarkdownBody(markdown = prepared, modifier = modifier)
}

/** Cuts very long READMEs and turns relative image paths into raw.githubusercontent.com links. */
internal fun prepareReadme(
    markdown: String,
    owner: String,
    repo: String,
    branch: String?,
    maxChars: Int = MaxReadmeChars,
): String {
    val base = "https://raw.githubusercontent.com/$owner/$repo/${branch ?: "HEAD"}/"

    fun resolve(src: String): String = when {
        src.startsWith("http://") || src.startsWith("https://") || src.startsWith("data:") -> src
        src.startsWith("//") -> "https:$src"
        else -> base + src.removePrefix("./").removePrefix("/")
    }

    var text = markdown
    if (text.length > maxChars) {
        text = text.take(maxChars).substringBeforeLast("\n\n") + "\n\n\u2026"
    }
    text = MarkdownImage.replace(text) { m ->
        m.groupValues[1] + resolve(m.groupValues[2]) + m.groupValues[3]
    }
    text = HtmlImage.replace(text) { m ->
        m.groupValues[1] + resolve(m.groupValues[2]) + m.groupValues[3]
    }
    return text
}
