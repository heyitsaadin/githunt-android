package com.githunt.android.data.translate

import java.util.regex.Pattern

/*
 * In-place README translation, ported from the website's lib/translate-markdown.js.
 *
 * The README markdown is never rebuilt. It is cut into parts that join back to the exact original:
 *
 *   - literal parts: code, HTML tags, URLs, link/image syntax, badges, list/quote/heading markers,
 *     table pipes, emphasis markers... never sent anywhere, never changed
 *   - text parts: the human-readable words between those. The only thing sent for translation.
 *
 * Translated text is dropped back into its exact slot. A piece that fails to translate keeps its
 * original text, so structure can never be lost and a failure can never blank out a README.
 */

/** A stretch of the original markdown. Joining every part's text gives back the exact original. */
internal class MdPart(
    var text: String,
    val translate: Boolean,
    val atStart: Boolean = false,
    val cell: Boolean = false,
)

/** Either literal text, or a unit of text that gets replaced by its translation when there is one. */
internal sealed class Slot {
    class Literal(val text: String) : Slot()
    class Translatable(val text: String, val atStart: Boolean, val cell: Boolean) : Slot()
}

/** [slots] rebuild the markdown; [order] is each distinct unit to translate, top to bottom. */
internal class MarkdownPlan(val slots: List<Slot>, val order: List<String>)

internal fun planMarkdown(markdown: String, target: String): MarkdownPlan =
    planSlots(segmentMarkdown(markdown), target)

// ---------------------------------------------------------------------------------------------
// 1. Segmentation
// ---------------------------------------------------------------------------------------------

private const val KNOWN_TAGS =
    "img|a|p|div|span|table|thead|tbody|tr|td|th|br|hr|h[1-6]|picture|source|video|audio|details|summary|svg|" +
        "ul|ol|li|code|pre|b|i|u|s|em|strong|center|font|sup|sub|kbd|iframe|input|button|section|article|nav|header|" +
        "footer|figure|figcaption|style|script|link|meta|blockquote|dl|dt|dd|small|mark|abbr|cite|q|label|form"

/**
 * Inline things that must never be translated or altered. The first alternative that matches at a
 * position wins, so the order matters.
 */
private val PROTECTED: Pattern = Pattern.compile(
    listOf(
        """<!--[\s\S]*?-->""", // HTML comment
        """`{3}[^\n]*?`{3}""", // ```inline fence```
        """`{2}[^\n]*?`{2}""", // ``code``
        """`[^`\n]+`""", // `code`
        """!\[[^\]\n]*\]\([^)\n]*\)""", // ![alt](src)
        """!\[[^\]\n]*\]\[[^\]\n]*\]""", // ![alt][ref]
        """\]\((?:<[^>\n]*>|[^)\s]*)(?:\s+(?:"[^"\n]*"|'[^'\n]*'))?\s*\)""", // ](dest "title")
        """\]\[[^\]\n]*\]""", // ][ref]
        """\[\^[^\]\n]+\]""", // [^footnote]
        """\[![A-Za-z]+\]""", // [!NOTE] alert marker
        """<\/?[A-Za-z][^>\n]*>""", // HTML tag or <autolink>
        // A tag whose closing > is on a later line: a bare "<img" at the end of a line, or one that
        // already carries an attr="value". Plain prose like "a<b then" must not match.
        """<\/?(?:$KNOWN_TAGS)\b\s*${'$'}""",
        """<\/?(?:$KNOWN_TAGS)\b(?:\s+[^\s=>]+)*?\s+[A-Za-z_:][-\w:.]*\s*=[^>\n]*${'$'}""",
        """(?:https?:\/\/|www\.)[^\s<>"'，。！？；：、）】」』》]*[^\s<>"'.,;:!?)\]，。！？；：、）】」』》]""", // bare URL
        """[\w.+-]+@[\w-]+(?:\.[\w-]+)+""", // email
        """&(?:#\d+|#x[0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);""", // HTML entity
        """\\[\\`*_{}\[\]()#+\-.!|<>~]""", // backslash escapes
        """\*{1,3}|~~""", // emphasis / strikethrough markers
        """[\[\]]""", // any remaining bracket: keeps link syntax out of the text
    ).joinToString("|"),
)

private val LETTER = Regex("""\p{L}""")
private val WORD_CHAR = Regex("""[\p{L}\p{N}]""")
private val CJK_TARGET = Regex("""^(zh|ja|ko|th)""", RegexOption.IGNORE_CASE)
private val ENGLISH_TARGET = Regex("""^en""", RegexOption.IGNORE_CASE)

private val NEWLINE = Regex("""\r?\n""")
private val REF_DEF_COLLECT = Regex("""^\s{0,3}\[(?!\^)([^\]\n]+)\]:""", RegexOption.MULTILINE)
private val LIST_START = Regex("""^\s*(?:>\s?)*\s*(?:[-*+]|\d{1,9}[.)])[ \t]""")
private val INDENT2 = Regex("""^(?: {2,}|\t)""")
private val FENCE_CLOSE = Regex("""^\s*(?:>\s?)*\s*(`{3,}|~{3,})\s*${'$'}""")
private val FENCE_OPEN = Regex("""^(\s*(?:>\s?)*\s*(?:(?:[-*+]|\d{1,9}[.)])\s+)?)(`{3,}|~{3,})(.*)${'$'}""")
private val COMMENT_FULL = Regex("""<!--[\s\S]*?-->""")
private val RAW_OPEN = Regex("""^\s*<(pre|script|style|textarea)\b""", RegexOption.IGNORE_CASE)
private val REF_DEF = Regex("""^\s{0,3}\[(?!\^)[^\]\n]+\]:\s*\S""")
private val HR = Regex("""^\s{0,3}([-*_=])(?:\s*\1){2,}\s*${'$'}""")
private val INDENTED = Regex("""^(?: {4}|\t)""")
private val FOOTNOTE_DEF = Regex("""^\s{0,3}\[\^[^\]\n]+\]:[ \t]*""")
private val PREFIX = Regex(
    """^(?:[ \t]*(?:>[ \t]?|(?:[-*+]|\d{1,9}[.)])[ \t]+(?:\[[ xX]\][ \t]+)?))*[ \t]*(?:#{1,6}[ \t]+)?""",
)
private val HEADING_PREFIX_END = Regex("""#{1,6}[ \t]+${'$'}""")
private val HEADING_CLOSE = Regex("""^(.*?)(\s+#+\s*)${'$'}""")
private val SINGLE_COLUMN_RULE = Regex("""^:?-{3,}:?${'$'}""")
private val TABLE_DELIM_CELL = Regex("""^\s*:?-+:?\s*${'$'}""")

private class Piece(val text: String, val translate: Boolean)

private class Inline(val pieces: List<Piece>, val openTag: Boolean)

private class Cell(val text: String, val pipe: Boolean)

/**
 * Splits plain text on underscore emphasis markers. An underscore between two word characters
 * (snake_case) is literal text; any other underscore run is a potential emphasis marker and is
 * kept out of the translated text.
 */
private fun splitUnderscoreMarkers(text: String): List<Piece> {
    if (text.indexOf('_') == -1) return listOf(Piece(text, true))
    val out = ArrayList<Piece>()
    val buf = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text[i] != '_') {
            buf.append(text[i])
            i++
            continue
        }
        var j = i
        while (j < text.length && text[j] == '_') j++
        val prev = if (i > 0) text[i - 1] else null
        val next = if (j < text.length) text[j] else null
        val intraword = prev != null && next != null &&
            WORD_CHAR.matches(prev.toString()) && WORD_CHAR.matches(next.toString())
        if (intraword) {
            buf.append(text, i, j)
        } else {
            if (buf.isNotEmpty()) {
                out.add(Piece(buf.toString(), true))
                buf.setLength(0)
            }
            out.add(Piece(text.substring(i, j), false))
        }
        i = j
    }
    if (buf.isNotEmpty()) out.add(Piece(buf.toString(), true))
    return out
}

/** Cuts one line's content into alternating translatable / protected pieces. */
private fun segmentInline(content: String, defs: Set<String>): Inline {
    val pieces = ArrayList<Piece>()
    var openTag = false
    var last = 0
    var from = 0
    val m = PROTECTED.matcher(content)

    fun pushText(t: String) {
        if (t.isEmpty()) return
        pieces.addAll(splitUnderscoreMarkers(t))
    }

    while (from <= content.length && m.find(from)) {
        var tok = m.group()
        val start = m.start()
        var next = m.end()
        if (tok.isEmpty()) {
            from = next + 1
            continue
        }
        // `[label]` that points at a `[label]: url` definition is a reference link: its label is an
        // identifier, not prose.
        if (tok == "[" && defs.isNotEmpty()) {
            val close = content.indexOf(']', start + 1)
            if (close != -1 && defs.contains(content.substring(start + 1, close).trim().lowercase())) {
                tok = content.substring(start, close + 1)
                next = close + 1
            }
        }
        pushText(content.substring(last, start))
        pieces.add(Piece(tok, false))
        if (tok[0] == '<' && !tok.endsWith(">")) openTag = true
        last = start + tok.length
        from = next
    }
    pushText(content.substring(last))
    return Inline(pieces, openTag)
}

private fun isTableDelimiterRow(line: String): Boolean {
    val t = line.trim()
    if (!t.contains('-')) return false
    if (!t.contains('|') && !SINGLE_COLUMN_RULE.containsMatchIn(t)) return false
    var s = t
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.substring(0, s.length - 1)
    val cells = s.split("|")
    return cells.isNotEmpty() && cells.all { TABLE_DELIM_CELL.containsMatchIn(it) }
}

private fun splitTableCells(line: String): List<Cell> {
    val out = ArrayList<Cell>()
    val cell = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\\' && i + 1 < line.length && line[i + 1] == '|') {
            cell.append("\\|")
            i++
        } else if (c == '|') {
            out.add(Cell(cell.toString(), false))
            out.add(Cell("", true))
            cell.setLength(0)
        } else {
            cell.append(c)
        }
        i++
    }
    out.add(Cell(cell.toString(), false))
    return out
}

private class Segmenter(private val defs: Set<String>) {
    val parts = ArrayList<MdPart>()

    private var fenceCh: Char? = null // the fence character while inside a fenced code block
    private var fenceLen = 0
    private var comment = false
    private var raw: String? = null // "pre" | "script" | ... while inside a raw HTML block
    private var openTag = 0 // lines left to look for the closing > of a multi-line tag
    private var inTable = false
    private var inList = false
    private var prevBlank = true
    private var indentCode = false

    /** True when the last line processed was a blank line outside any code, comment or HTML block. */
    var plainBlank = false
        private set

    /** True while the last line processed belongs to a list (a blank line after it may be inside it). */
    val listOpen: Boolean get() = inList

    fun literal(text: String) = lit(text)

    private fun lit(text: String) {
        if (text.isEmpty()) return
        val last = parts.lastOrNull()
        if (last != null && !last.translate) last.text += text else parts.add(MdPart(text, false))
    }

    private fun emitContent(content: String, atStart: Boolean, cell: Boolean) {
        val inline = segmentInline(content, defs)
        inline.pieces.forEachIndexed { idx, p ->
            if (p.translate) parts.add(MdPart(p.text, true, atStart && idx == 0, cell)) else lit(p.text)
        }
        if (inline.openTag) openTag = 12
    }

    fun process(line: String, nextLine: String?) {
        plainBlank = false
        val wasBlank = prevBlank
        prevBlank = line.isBlank()

        // A column-0 line after a blank line ends any open list; a list marker (re)starts one. Done
        // up front so fences, tables and HTML blocks also end a list, otherwise a later indented
        // code block is mistaken for list continuation text.
        if (fenceCh == null && line.isNotBlank()) {
            if (LIST_START.containsMatchIn(line)) {
                inList = true
            } else if (!INDENT2.containsMatchIn(line) && wasBlank) {
                inList = false
            }
        }

        // Fenced code block: everything inside, fences included, is literal.
        val openFence = fenceCh
        if (openFence != null) {
            val m = FENCE_CLOSE.find(line)
            if (m != null) {
                val run = m.groupValues[1]
                if (run[0] == openFence && run.length >= fenceLen) fenceCh = null
            }
            lit(line)
            return
        }
        val fo = FENCE_OPEN.find(line)
        if (fo != null) {
            val run = fo.groupValues[2]
            val rest = fo.groupValues[3]
            if (!(run[0] == '`' && rest.contains('`'))) {
                fenceCh = run[0]
                fenceLen = run.length
                lit(line)
                return
            }
        }

        if (comment) {
            if (line.contains("-->")) comment = false
            lit(line)
            return
        }
        if (line.contains("<!--") && !COMMENT_FULL.containsMatchIn(line)) {
            comment = true
            lit(line)
            return
        }

        val openRaw = raw
        if (openRaw != null) {
            if (Regex("</" + openRaw + "\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(line)) raw = null
            lit(line)
            return
        }
        val rawOpen = RAW_OPEN.find(line)
        if (rawOpen != null) {
            val tag = rawOpen.groupValues[1]
            if (!Regex("</" + tag + "\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                raw = tag
                lit(line)
                return
            }
        }

        // Continuation of an HTML tag that started on an earlier line.
        if (openTag > 0 && line.isBlank()) openTag = 0 // a tag can't span a blank line
        if (openTag > 0) {
            val gt = line.indexOf('>')
            if (gt == -1) {
                openTag--
                lit(line)
                return
            }
            openTag = 0
            lit(line.substring(0, gt + 1))
            val rest = line.substring(gt + 1)
            if (rest.isNotBlank()) emitContent(rest, atStart = false, cell = false) else lit(rest)
            return
        }

        if (line.isBlank()) {
            plainBlank = true
            lit(line)
            inTable = false
            return
        }

        if (REF_DEF.containsMatchIn(line) || HR.containsMatchIn(line)) {
            lit(line)
            return
        }

        val indented = INDENTED.containsMatchIn(line)
        if (indented && !inList && (wasBlank || indentCode)) {
            indentCode = true
            lit(line)
            return
        }
        indentCode = false

        // GFM table
        if (isTableDelimiterRow(line)) {
            lit(line)
            inTable = true
            return
        }
        if (line.contains('|') && (inTable || (nextLine != null && isTableDelimiterRow(nextLine)))) {
            inTable = true
            for (c in splitTableCells(line)) {
                if (c.pipe) lit("|") else emitContent(c.text, atStart = true, cell = true)
            }
            return
        }
        inTable = false

        // Everything else: strip block markers (quote, list, task box, heading hashes) as literal,
        // translate what's left.
        val footnote = FOOTNOTE_DEF.find(line)
        val prefix = footnote?.value ?: (PREFIX.find(line)?.value ?: "")
        var content = line.substring(prefix.length)
        var suffix = ""
        if (HEADING_PREFIX_END.containsMatchIn(prefix)) {
            val hm = HEADING_CLOSE.find(content)
            if (hm != null) {
                content = hm.groupValues[1]
                suffix = hm.groupValues[2]
            }
        }
        lit(prefix)
        emitContent(content, atStart = true, cell = false)
        lit(suffix)
    }
}

/** Cuts markdown into parts. Invariant: the parts' text, joined, is exactly [markdown]. */
internal fun segmentMarkdown(
    markdown: String,
    defs: Set<String> = collectReferenceLabels(markdown),
): List<MdPart> {

    val lines = ArrayList<String>()
    val separators = ArrayList<String>()
    var idx = 0
    for (m in NEWLINE.findAll(markdown)) {
        lines.add(markdown.substring(idx, m.range.first))
        separators.add(m.value)
        idx = m.range.last + 1
    }
    lines.add(markdown.substring(idx))
    separators.add("")

    val segmenter = Segmenter(defs)
    for (k in lines.indices) {
        segmenter.process(lines[k], lines.getOrNull(k + 1))
        segmenter.literal(separators[k])
    }
    return segmenter.parts
}

// ---------------------------------------------------------------------------------------------
// 2. Turning text parts into translation units
// ---------------------------------------------------------------------------------------------

/** Longest single string sent to a backend (MyMemory caps at ~500 bytes). */
private const val MAX_UNIT_CHARS = 420

private val EN_STOPWORDS = (
    "the and to of a in is for with you this that on it are be as use can or an by from your not will if at " +
        "when which have has more all but so how what into also"
    ).split(" ").toSet()

private val EN_HEADING_WORDS = (
    "installation install usage license licence features feature contributing contribution contributors " +
        "documentation docs requirements examples example getting started project title table of contents " +
        "overview quick start configuration credits roadmap support api reference faq changelog build run " +
        "test tests testing development deployment demo screenshots screenshot acknowledgements " +
        "acknowledgments authors prerequisites introduction about description notes note usage setup options " +
        "commands download downloads contact links resources sponsors star history todo status"
    ).split(" ").toSet()

private val TOKEN_ONLY = Regex("""^[\w.\-/@:#]+${'$'}""")
private val HAS_PUNCT_OR_DIGIT = Regex("""[._/\-\d@:#]""")
private val ACRONYM = Regex("""^[A-Z0-9][A-Z0-9 _\-./+]*${'$'}""")
private val ENGLISHY_CHARS = Regex("""^[\x00-\x7F\u2018\u2019\u201C\u201D\u2013\u2014\u2026]*${'$'}""")
private val ALL_ASCII = Regex("""^[\x00-\x7F]*${'$'}""")
private val EN_WORD = Regex("""[a-z']+""")
private val LATIN_WORD = Regex("""[a-z]+""")
private val SENTENCE = Regex("""[\s\S]*?(?:[。！？；]+|[.!?;]+(?=\s|${'$'}))\s*|[\s\S]+${'$'}""")

private fun isEnglishTarget(target: String) = ENGLISH_TARGET.containsMatchIn(target)

private fun looksLikeEnglish(core: String): Boolean {
    if (!ENGLISHY_CHARS.matches(core)) return false
    val words = EN_WORD.findAll(core.lowercase()).map { it.value }.toList()
    if (words.size < 4) return false
    val hits = words.count { it in EN_STOPWORDS }
    return hits.toDouble() / words.size >= 0.2
}

/** Strings not worth a network call. */
private fun shouldSkip(core: String, target: String): Boolean {
    // identifiers, versions, paths, flags: a single token with punctuation or digits
    if (TOKEN_ONLY.containsMatchIn(core) && HAS_PUNCT_OR_DIGIT.containsMatchIn(core)) return true
    if (isEnglishTarget(target)) {
        if (ACRONYM.containsMatchIn(core)) return true // ACRONYMS, CONSTANTS
        if (looksLikeEnglish(core)) return true // already English: save the quota
        val hw = LATIN_WORD.findAll(core.lowercase()).map { it.value }.toList()
        if (hw.isNotEmpty() && hw.size <= 3 && ALL_ASCII.matches(core) && hw.all { it in EN_HEADING_WORDS }) {
            return true
        }
    }
    return false
}

private fun hardSplit(str: String, max: Int): List<String> {
    val out = ArrayList<String>()
    var s = str
    while (s.length > max) {
        var cut = -1
        for (ch in listOf(" ", "，", ",", "、", "；", ";")) {
            val idx = s.lastIndexOf(ch, max)
            if (idx > max * 0.4 && idx > cut) cut = idx + 1
        }
        if (cut == -1) cut = max
        out.add(s.substring(0, cut))
        s = s.substring(cut)
    }
    if (s.isNotEmpty()) out.add(s)
    return out
}

private class SplitUnit(val text: String, val ws: String)

/** Splits an over-long run at sentence boundaries into units a backend accepts. */
private fun splitCore(core: String): List<SplitUnit> {
    if (core.length <= MAX_UNIT_CHARS) return listOf(SplitUnit(core, ""))
    val sentences = SENTENCE.findAll(core).map { it.value }.toList().ifEmpty { listOf(core) }
    val packed = ArrayList<String>()
    var cur = ""
    for (s in sentences) {
        if (cur.isNotEmpty() && (cur + s).length > MAX_UNIT_CHARS) {
            packed.add(cur)
            cur = s
        } else {
            cur += s
        }
    }
    if (cur.isNotEmpty()) packed.add(cur)
    val units = ArrayList<SplitUnit>()
    for (p in packed) {
        val pieces = if (p.length > MAX_UNIT_CHARS) hardSplit(p, MAX_UNIT_CHARS) else listOf(p)
        for (piece in pieces) {
            val body = piece.trimEnd()
            if (body.isNotEmpty()) units.add(SplitUnit(body, piece.substring(body.length)))
        }
    }
    return units
}

/**
 * A flat list of slots: literal strings and translatable units. Joining every slot (units replaced
 * by their translation, or by their original text when there isn't one) rebuilds the markdown.
 */
internal fun planSlots(parts: List<MdPart>, target: String): MarkdownPlan {
    val slots = ArrayList<Slot>()
    val order = ArrayList<String>() // distinct unit texts, in document order
    val seen = HashSet<String>()
    val cjk = CJK_TARGET.containsMatchIn(target)
    for (part in parts) {
        if (!part.translate) {
            slots.add(Slot.Literal(part.text))
            continue
        }
        val trimmedStart = part.text.trimStart()
        val lead = part.text.substring(0, part.text.length - trimmedStart.length)
        val core = trimmedStart.trimEnd()
        val trail = trimmedStart.substring(core.length)
        if (core.isEmpty() || !LETTER.containsMatchIn(core) || shouldSkip(core, target)) {
            slots.add(Slot.Literal(part.text))
            continue
        }
        slots.add(Slot.Literal(lead))
        val units = splitCore(core)
        units.forEachIndexed { idx, u ->
            slots.add(Slot.Translatable(u.text, part.atStart && idx == 0, part.cell))
            if (seen.add(u.text)) order.add(u.text)
            // Sentence-split pieces from CJK sources have no whitespace between them.
            val isLast = idx == units.lastIndex
            slots.add(Slot.Literal(if (!isLast && u.ws.isEmpty() && !cjk) " " else u.ws))
        }
        slots.add(Slot.Literal(trail))
    }
    return MarkdownPlan(slots, order)
}

// ---------------------------------------------------------------------------------------------
// 3. Putting translations back
// ---------------------------------------------------------------------------------------------

private val LINE_BREAKS = Regex("""\r?\n+""")
private val TAG_OPEN = Regex("""<(?=[A-Za-z/!?])""")
private val CELL_PIPE = Regex("""\\?\|""")
private val LIST_NUMBER = Regex("""^(\d{1,9})([.)])(\s)""")
private val BLOCK_MARKER = Regex("""^([-+*>#])""")

/** Translated text lands inside markdown, so make sure it can't change the structure around it. */
private fun sanitizeTranslated(out: String, slot: Slot.Translatable): String? {
    var s = LINE_BREAKS.replace(out, " ").trim()
    if (s.isEmpty()) return null
    s = TAG_OPEN.replace(s, "&lt;")
    if (slot.cell) s = CELL_PIPE.replace(s) { "\\|" }
    if (slot.atStart) {
        s = LIST_NUMBER.replace(s) { m -> m.groupValues[1] + "\\" + m.groupValues[2] + m.groupValues[3] }
        s = BLOCK_MARKER.replace(s) { m -> "\\" + m.groupValues[1] }
    }
    return s
}

internal fun assembleMarkdown(slots: List<Slot>, results: Map<String, String>): String {
    val out = StringBuilder()
    for (slot in slots) {
        when (slot) {
            is Slot.Literal -> out.append(slot.text)
            is Slot.Translatable -> {
                val r = results[slot.text]
                if (r == null || r == slot.text) {
                    out.append(slot.text)
                } else {
                    out.append(sanitizeTranslated(r, slot) ?: slot.text)
                }
            }
        }
    }
    return out.toString()
}
