package com.githunt.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.ui.theme.FocusBlue
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.delay

// Pieces shared by the repo card's "Ask AI" panel and the Home AI search box, so both chats look
// and behave the same.

private val InlineRegex = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
private val HeadingRegex = Regex("^#{1,6}\\s+(.*)$")
private val BulletRegex = Regex("^(\\s*)[-*]\\s+")

/**
 * The model answers in light markdown. This handles what it actually writes: **bold**, `code`,
 * fenced code blocks, headings (drawn bold) and "-" / "*" bullets. Anything else stays as text.
 */
internal fun chatAnnotated(text: String, mono: FontFamily?, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var inFence = false
        var first = true
        for (raw in text.trim().lines()) {
            if (raw.trimStart().startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (!first) append('\n')
            first = false
            if (inFence) {
                withStyle(SpanStyle(fontFamily = mono, background = codeBackground)) { append(raw) }
                continue
            }
            val heading = HeadingRegex.matchEntire(raw)
            if (heading != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    appendInline(heading.groupValues[1], mono, codeBackground)
                }
            } else {
                appendInline(BulletRegex.replace(raw) { "${it.groupValues[1]}\u2022 " }, mono, codeBackground)
            }
        }
    }

private fun AnnotatedString.Builder.appendInline(line: String, mono: FontFamily?, codeBackground: Color) {
    var last = 0
    for (match in InlineRegex.findAll(line)) {
        append(line.substring(last, match.range.first))
        val bold = match.groups[1]?.value
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
        } else {
            withStyle(SpanStyle(fontFamily = mono, background = codeBackground)) {
                append(match.groups[2]?.value.orEmpty())
            }
        }
        last = match.range.last + 1
    }
    append(line.substring(last))
}

/** One chat bubble. [extra] draws below the text inside the same bubble. */
@Composable
internal fun ChatBubble(
    isUser: Boolean,
    text: String,
    isError: Boolean = false,
    animate: Boolean = false,
    onAnimationDone: () -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val mono = LocalGhMono.current
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )
    val background = when {
        isError -> scheme.error.copy(alpha = 0.1f)
        isUser -> scheme.background
        else -> scheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val textColor = if (isError) scheme.error else scheme.onSurface
    val codeBackground = scheme.onSurface.copy(alpha = 0.1f)
    val rendered = remember(text, isUser, mono, codeBackground) {
        if (isUser) AnnotatedString(text) else chatAnnotated(text, mono, codeBackground)
    }
    // Typewriter: a fresh AI reply is revealed in a quick stream (about 2-3s at most) instead of
    // appearing all at once. Old messages (animate = false) render in full straight away.
    val typewriter = animate && !isUser && !isError
    var shown by remember(text, typewriter) { mutableIntStateOf(if (typewriter) 0 else rendered.length) }
    LaunchedEffect(text, typewriter) {
        if (!typewriter) return@LaunchedEffect
        val total = rendered.length
        val step = maxOf(2, total / 150)
        while (shown < total) {
            delay(16)
            shown = minOf(total, shown + step)
        }
    }
    val finished = shown >= rendered.length
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(background)
                .then(if (isUser) Modifier.border(1.dp, scheme.outline.copy(alpha = 0.55f), shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (finished) rendered else rendered.subSequence(0, shown),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textColor,
            )
            if (finished) extra()
        }
    }
}

/** The "thinking" bubble shown while a reply is on its way. */
@Composable
internal fun TypingBubble() {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The round up-arrow send button. Inverted like the website's (light on dark, dark on light). */
@Composable
internal fun SendCircle(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(scheme.onBackground.copy(alpha = if (enabled) 1f else 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PathIcon(pathData = CardIcons.ArrowUp, tint = scheme.background, iconSize = 16.dp, strokeWidth = 2.2f)
    }
}

/** Chat input: grows up to five lines, keyboard's send key submits. */
@Composable
internal fun ChatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    maxLength: Int = 1500,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(FocusBlue),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.fillMaxWidth().then(textModifier),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 14.5.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
