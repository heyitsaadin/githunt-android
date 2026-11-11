package com.githunt.android.ui.components

/** Past this many characters the README is cut, so one huge file can't make a card thousands of screens tall. */
internal const val MaxReadmeChars = 15_000

/** One picture from a README. [badge] pictures (shields.io etc.) are drawn small and are not opened full screen. */
internal data class ReadmePicture(
    val url: String,
    val darkUrl: String?,
    val alt: String,
    val widthDp: Int?,
    val widthFraction: Float?,
    val link: String?,
    val badge: Boolean,
)

internal fun ReadmePicture.displayUrl(dark: Boolean): String = if (dark && darkUrl != null) darkUrl else url

/**
 * A README is cut into blocks so each kind of content gets the right view: text goes through the
 * Markdown renderer, pictures and GIFs through Coil (animated, centred, sized like GitHub), and
 * videos through a real player. Everything the single text view could not do (centred headers,
 * badge rows, GIFs, SVG, video) lives in the non-text blocks.
 */
internal sealed interface ReadmeBlock {
    data class Markdown(val text: String, val centered: Boolean) : ReadmeBlock
    data class PictureRow(val items: List<ReadmePicture>, val centered: Boolean) : ReadmeBlock
    data class YouTube(val id: String) : ReadmeBlock
    data class Video(val url: String, val poster: String?) : ReadmeBlock
}

/** Resolves README-relative paths against the repo, like the website's resolveImageUrl. */
internal class ReadmeContext(owner: String, repo: String, branch: String?) {
    private val ref = branch ?: "HEAD"
    private val raw = "https://raw.githubusercontent.com/$owner/$repo/$ref/"
    private val blob = "https://github.com/$owner/$repo/blob/$ref/"

    private fun absolute(s: String) = s.startsWith("http://") || s.startsWith("https://") || s.startsWith("data:")
    private fun clean(s: String) = s.removePrefix("./").removePrefix("/")

    fun media(src: String): String = when {
        absolute(src) -> src
        src.startsWith("//") -> "https:$src"
        else -> raw + clean(src)
    }

    fun link(href: String): String = when {
        absolute(href) || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:") -> href
        href.startsWith("//") -> "https:$href"
        else -> blob + clean(href)
    }
}

private val HtmlComment = Regex("<!--[\\s\\S]*?-->")
private val SummaryTag = Regex("""<summary[^>]*>([\s\S]*?)</summary>""", RegexOption.IGNORE_CASE)
private val DetailsTag = Regex("""</?details[^>]*>""", RegexOption.IGNORE_CASE)
private val MarkdownImage = Regex("""(!\[[^\]]*]\()([^)\s]+)((?:\s+"[^"]*")?\))""")
private val HtmlImageSrc = Regex("""(<img[^>]*?\ssrc=["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
private val MarkdownLink = Regex("""(?<!!)(\[[^\]]*]\()([^)\s]+)((?:\s+"[^"]*")?\))""")

/**
 * Cuts very long READMEs, drops comments, turns `<details>` into a bold title, and makes relative
 * image and link paths absolute.
 */
internal fun prepareReadme(markdown: String, owner: String, repo: String, branch: String?): String {
    val ctx = ReadmeContext(owner, repo, branch)
    var text = markdown.replace("\r\n", "\n").replace('\r', '\n')
    if (text.length > MaxReadmeChars) {
        text = text.take(MaxReadmeChars).substringBeforeLast("\n\n") + "\n\n\u2026"
    }
    text = HtmlComment.replace(text, "")
    text = SummaryTag.replace(text) { m -> "\n\n**" + m.groupValues[1].trim() + "**\n\n" }
    text = DetailsTag.replace(text, "\n")
    text = MarkdownImage.replace(text) { m -> m.groupValues[1] + ctx.media(m.groupValues[2]) + m.groupValues[3] }
    text = HtmlImageSrc.replace(text) { m -> m.groupValues[1] + ctx.media(m.groupValues[2]) + m.groupValues[3] }
    text = MarkdownLink.replace(text) { m -> m.groupValues[1] + ctx.link(m.groupValues[2]) + m.groupValues[3] }
    return text
}

internal fun parseReadme(markdown: String, owner: String, repo: String, branch: String?): List<ReadmeBlock> {
    val ctx = ReadmeContext(owner, repo, branch)
    val text = prepareReadme(markdown, owner, repo, branch)
    val out = ArrayList<ReadmeBlock>()
    parseInto(text, ctx, centered = false, inHtml = false, depth = 0, out = out)
    return out
}

// ---- Patterns --------------------------------------------------------------------------------

private val Fence = Regex("^\\s{0,3}(```|~~~)")
private val OnlyBreaks = Regex("""^(?:<br\s*/?>\s*)+$""", RegexOption.IGNORE_CASE)
private val OpenTag = Regex("""<([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>""")
private val ContainerTags = setOf("p", "div", "center", "h1", "h2", "h3", "h4", "h5", "h6")

private val MdLinkedImage = Regex("""\[!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val MdImage = Regex("""!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val HtmlAnchorMedia = Regex(
    """<a\s[^>]*?href\s*=\s*["']([^"']+)["'][^>]*>\s*(<img\b[^>]*>|<picture\b[\s\S]*?</picture>)\s*</a>""",
    RegexOption.IGNORE_CASE,
)
private val HtmlPicture = Regex("""<picture\b[\s\S]*?</picture>""", RegexOption.IGNORE_CASE)
private val HtmlImg = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HtmlSource = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)
private val Br = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

private val HtmlA = Regex("""<a\b[^>]*?href\s*=\s*["']([^"']*)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
private val HtmlBold = Regex("""<(b|strong)\b[^>]*>([\s\S]*?)</\1>""", RegexOption.IGNORE_CASE)
private val HtmlItalic = Regex("""<(i|em)\b[^>]*>([\s\S]*?)</\1>""", RegexOption.IGNORE_CASE)
private val HtmlStrike = Regex("""<(s|strike|del)\b[^>]*>([\s\S]*?)</\1>""", RegexOption.IGNORE_CASE)
private val HtmlCode = Regex("""<code\b[^>]*>([\s\S]*?)</code>""", RegexOption.IGNORE_CASE)
private val HtmlWrapper = Regex("""</?(?:p|div|span|center|font|section)\b[^>]*>""", RegexOption.IGNORE_CASE)

private val YoutubeUrl = Regex(
    """^https?://(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?(?:[^#\s]*&)?v=|embed/|shorts/|live/|v/)|youtube-nocookie\.com/embed/|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
    RegexOption.IGNORE_CASE,
)
private val YoutubeThumb = Regex(
    """^https?://(?:img\.youtube\.com|i\d*\.ytimg\.com)/vi(?:_webp)?/([A-Za-z0-9_-]{11})/""",
    RegexOption.IGNORE_CASE,
)
private val VideoFile = Regex("""\.(?:mp4|webm|mov|m4v)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
private val BareUrl = Regex("""^<?(https?://[^\s<>]+)>?$""")
private val GithubAsset = Regex(
    """^https://github\.com/(?:user-attachments/assets/[0-9a-fA-F-]{36}|[^/]+/[^/]+/assets/\d+/[0-9a-fA-F-]{36})/?$""",
)

private val BadgePatterns = listOf(
    "shields.io", "badge", "travis-ci", "circleci", "coveralls", "codecov", "github.com/sponsors",
    "buymeacoffee", "ko-fi.com", "opencollective", "api.codacy", "app.codacy",
)

private fun isBadge(url: String): Boolean = BadgePatterns.any { url.contains(it, ignoreCase = true) }
private fun youtubeId(url: String): String? = YoutubeUrl.find(url.trim())?.groupValues?.get(1)
private fun youtubeThumbId(url: String): String? = YoutubeThumb.find(url.trim())?.groupValues?.get(1)
private fun isVideoFile(url: String): Boolean = VideoFile.containsMatchIn(url)

// ---- HTML helpers ----------------------------------------------------------------------------

private fun attr(tag: String, name: String): String? {
    val re = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    val m = re.find(tag) ?: return null
    return (m.groups[1] ?: m.groups[2] ?: m.groups[3])?.value
}

private fun isCentered(attrs: String): Boolean {
    val align = attr("<x $attrs>", "align")?.trim()?.lowercase()
    if (align == "center") return true
    val style = attr("<x $attrs>", "style")?.lowercase().orEmpty()
    return style.replace(" ", "").contains("text-align:center")
}

/** Index range of the `</tag>` that closes an element whose opening tag ended at [from]. */
private fun findClose(text: String, tag: String, from: Int): IntRange? {
    val re = Regex("""<(/?)$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
    var depth = 1
    var pos = from
    while (true) {
        val m = re.find(text, pos) ?: return null
        if (m.groupValues[1] == "/") {
            depth--
            if (depth == 0) return m.range
        } else if (!m.value.endsWith("/>")) {
            depth++
        }
        pos = m.range.last + 1
    }
}

/** Converts the inline HTML READMEs use inside centred blocks into Markdown the renderer handles well. */
private fun htmlInlineToMarkdown(s: String, lineBreak: String): String {
    var t = s
    t = HtmlImg.replace(t) { m ->
        val src = attr(m.value, "src")
        if (src == null) "" else "![" + attr(m.value, "alt").orEmpty() + "](" + src + ")"
    }
    t = HtmlA.replace(t) { m -> "[" + m.groupValues[2].trim() + "](" + m.groupValues[1] + ")" }
    t = HtmlBold.replace(t) { m -> "**" + m.groupValues[2] + "**" }
    t = HtmlItalic.replace(t) { m -> "*" + m.groupValues[2] + "*" }
    t = HtmlStrike.replace(t) { m -> "~~" + m.groupValues[2] + "~~" }
    t = HtmlCode.replace(t) { m -> "`" + m.groupValues[1] + "`" }
    t = Br.replace(t, lineBreak)
    t = HtmlWrapper.replace(t, "")
    return t
}

// ---- Media runs ------------------------------------------------------------------------------

private class RawMedia(
    val src: String,
    val darkSrc: String?,
    val alt: String,
    val width: String?,
    val link: String?,
)

private class Token(val media: RawMedia?, val end: Int)

private fun imgToRaw(tag: String): RawMedia? {
    val src = attr(tag, "src") ?: return null
    return RawMedia(src, null, attr(tag, "alt").orEmpty(), attr(tag, "width"), null)
}

private fun parsePicture(html: String): RawMedia? {
    val img = HtmlImg.find(html)?.value ?: return null
    val src = attr(img, "src") ?: return null
    var dark: String? = null
    for (source in HtmlSource.findAll(html)) {
        val media = attr(source.value, "media").orEmpty()
        if (media.contains("dark", ignoreCase = true)) {
            dark = (attr(source.value, "srcset") ?: attr(source.value, "src"))
                ?.trim()?.substringBefore(',')?.trim()?.substringBefore(' ')
        }
    }
    return RawMedia(src, dark, attr(img, "alt").orEmpty(), attr(img, "width"), null)
}

private fun tokenAt(t: String, p: Int): Token? {
    val linked = MdLinkedImage.matchAt(t, p)
    if (linked != null) {
        val g = linked.groupValues
        return Token(RawMedia(g[2], null, g[1], null, g[3]), linked.range.last + 1)
    }
    val anchor = HtmlAnchorMedia.matchAt(t, p)
    if (anchor != null) {
        val inner = anchor.groupValues[2]
        val raw = if (inner.startsWith("<picture", ignoreCase = true)) parsePicture(inner) else imgToRaw(inner)
        if (raw != null) {
            return Token(RawMedia(raw.src, raw.darkSrc, raw.alt, raw.width, anchor.groupValues[1]), anchor.range.last + 1)
        }
    }
    val picture = HtmlPicture.matchAt(t, p)
    if (picture != null) {
        val raw = parsePicture(picture.value)
        if (raw != null) return Token(raw, picture.range.last + 1)
    }
    val md = MdImage.matchAt(t, p)
    if (md != null) {
        val g = md.groupValues
        return Token(RawMedia(g[2], null, g[1], null, null), md.range.last + 1)
    }
    val img = HtmlImg.matchAt(t, p)
    if (img != null) {
        val raw = imgToRaw(img.value)
        if (raw != null) return Token(raw, img.range.last + 1)
    }
    val br = Br.matchAt(t, p)
    if (br != null) return Token(null, br.range.last + 1)
    return null
}

/**
 * Reads a run of pictures (Markdown or HTML, linked or not, separated by spaces / line breaks) that
 * starts at [start] and fills its lines on its own. Returns null if there is none, or if other text
 * follows on the same line (then the line is ordinary Markdown and stays inline).
 */
private fun readMediaRun(text: String, start: Int): Pair<List<RawMedia>, Int>? {
    val items = ArrayList<RawMedia>()
    var p = start
    var end = start
    while (p < text.length) {
        var q = p
        var newlines = 0
        while (q < text.length && text[q].isWhitespace()) {
            if (text[q] == '\n') newlines++
            q++
        }
        if (items.isNotEmpty() && newlines >= 2) break
        if (q >= text.length) break
        val token = tokenAt(text, q) ?: break
        if (token.media != null) {
            items += token.media
            end = token.end
        }
        p = token.end
    }
    if (items.isEmpty()) return null
    val nl = text.indexOf('\n', end)
    val eol = if (nl < 0) text.length else nl
    if (text.substring(end, eol).isNotBlank()) return null
    return items to end
}

private fun pictureOf(m: RawMedia, ctx: ReadmeContext): ReadmePicture {
    val url = ctx.media(m.src)
    val w = m.width?.trim()
    var widthDp: Int? = null
    var fraction: Float? = null
    if (w != null) {
        if (w.endsWith("%")) {
            fraction = w.dropLast(1).toFloatOrNull()?.div(100f)?.takeIf { it in 0.01f..1f }
        } else {
            widthDp = w.removeSuffix("px").toFloatOrNull()?.toInt()?.takeIf { it > 0 }
        }
    }
    return ReadmePicture(
        url = url,
        darkUrl = m.darkSrc?.let { ctx.media(it) },
        alt = m.alt,
        widthDp = widthDp,
        widthFraction = fraction,
        link = m.link?.let { ctx.link(it) },
        badge = isBadge(url),
    )
}

/** Pictures become a row; a picture linking to a video (or a YouTube thumbnail) becomes a player. */
private fun runToBlocks(items: List<RawMedia>, ctx: ReadmeContext, centered: Boolean): List<ReadmeBlock> {
    val out = ArrayList<ReadmeBlock>()
    val row = ArrayList<ReadmePicture>()
    fun flushRow() {
        if (row.isNotEmpty()) {
            out += ReadmeBlock.PictureRow(row.toList(), centered)
            row.clear()
        }
    }
    for (m in items) {
        val link = m.link
        val yt = (if (link != null) youtubeId(link) else null) ?: youtubeThumbId(m.src)
        if (yt != null) {
            flushRow()
            if ((out.lastOrNull() as? ReadmeBlock.YouTube)?.id != yt) out += ReadmeBlock.YouTube(yt)
        } else if (link != null && isVideoFile(link)) {
            flushRow()
            out += ReadmeBlock.Video(ctx.media(link), ctx.media(m.src))
        } else {
            row += pictureOf(m, ctx)
        }
    }
    flushRow()
    return out
}

/** A line that is only a video address: YouTube, a video file, or a GitHub-uploaded video. */
private fun bareUrlBlock(line: String): ReadmeBlock? {
    val url = BareUrl.matchEntire(line)?.groupValues?.get(1) ?: return null
    val yt = youtubeId(url)
    if (yt != null) return ReadmeBlock.YouTube(yt)
    if (isVideoFile(url) || GithubAsset.matches(url)) return ReadmeBlock.Video(url, null)
    return null
}

// ---- Block parser ----------------------------------------------------------------------------

private class Parsed(val blocks: List<ReadmeBlock>, val end: Int)

private fun parseHtmlBlock(text: String, start: Int, ctx: ReadmeContext, centered: Boolean, depth: Int): Parsed? {
    val open = OpenTag.matchAt(text, start) ?: return null
    val tag = open.groupValues[1].lowercase()
    val attrs = open.groupValues[2]
    val afterOpen = open.range.last + 1

    if (tag == "video") {
        val close = findClose(text, "video", afterOpen)
        val endIdx = if (close != null) close.last + 1 else afterOpen
        val whole = text.substring(start, endIdx)
        val src = attr(open.value, "src")
            ?: HtmlSource.find(whole)?.value?.let { attr(it, "src") }
            ?: return null
        val poster = attr(open.value, "poster")
        val yt = youtubeId(src)
        val block = if (yt != null) {
            ReadmeBlock.YouTube(yt)
        } else {
            ReadmeBlock.Video(ctx.media(src), poster?.let { ctx.media(it) })
        }
        return Parsed(listOf(block), endIdx)
    }

    if (tag !in ContainerTags) return null
    val close = findClose(text, tag, afterOpen) ?: return null
    val inner = text.substring(afterOpen, close.first)
    val aligned = centered || tag == "center" || isCentered(attrs)
    val blocks = ArrayList<ReadmeBlock>()

    if (tag.length == 2 && tag[0] == 'h') {
        val level = tag[1].digitToInt()
        val trimmed = inner.trim()
        val run = readMediaRun(trimmed, 0)
        if (run != null && trimmed.substring(run.second).isBlank()) {
            blocks += runToBlocks(run.first, ctx, aligned)
        } else if (trimmed.isNotEmpty()) {
            val oneLine = trimmed.replace(Regex("\\s*\n\\s*"), " ")
            blocks += ReadmeBlock.Markdown("#".repeat(level) + " " + htmlInlineToMarkdown(oneLine, " "), aligned)
        }
    } else {
        parseInto(inner.trim('\n'), ctx, aligned, inHtml = true, depth = depth + 1, out = blocks)
    }
    return Parsed(blocks, close.last + 1)
}

private fun parseInto(
    text: String,
    ctx: ReadmeContext,
    centered: Boolean,
    inHtml: Boolean,
    depth: Int,
    out: MutableList<ReadmeBlock>,
) {
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotBlank()) {
            val raw = buf.toString().trim('\n')
            out += ReadmeBlock.Markdown(if (inHtml) htmlInlineToMarkdown(raw, "  \n") else raw, centered)
        }
        buf.setLength(0)
    }

    var inFence = false
    var i = 0
    while (i < text.length) {
        val nl = text.indexOf('\n', i)
        val eol = if (nl < 0) text.length else nl
        val next = if (nl < 0) text.length else nl + 1
        val rawLine = text.substring(i, eol)
        // Inside an HTML block, indentation is just layout; left in, 4 spaces would turn into code.
        val line = if (inHtml) rawLine.trim() else rawLine
        val t = rawLine.trim()

        if (Fence.containsMatchIn(rawLine)) {
            inFence = !inFence
            buf.append(line).append('\n')
            i = next
            continue
        }
        if (inFence || t.isEmpty()) {
            buf.append(line).append('\n')
            i = next
            continue
        }
        if (OnlyBreaks.matches(t)) {
            buf.append('\n')
            i = next
            continue
        }

        val start = i + (rawLine.length - rawLine.trimStart().length)
        val c = t[0]
        if (c == '<' && depth < 8) {
            val parsed = parseHtmlBlock(text, start, ctx, centered, depth)
            if (parsed != null) {
                flush()
                out += parsed.blocks
                i = parsed.end
                continue
            }
        }
        if (c == '<' || c == '!' || c == '[') {
            val run = readMediaRun(text, start)
            if (run != null) {
                flush()
                out += runToBlocks(run.first, ctx, centered)
                i = run.second
                continue
            }
        }
        val bare = bareUrlBlock(t)
        if (bare != null) {
            flush()
            out += bare
            i = next
            continue
        }

        buf.append(line).append('\n')
        i = next
    }
    flush()
}
