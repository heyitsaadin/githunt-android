package com.githunt.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.GitHuntApp
import com.githunt.android.data.ai.AiChatMessage
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_QUESTION_LEN = 1500

/**
 * "Ask AI" for one repo: a small chat that lives inside the repo card, like the website's
 * RepoAskAI. The card grows to hold it, so asking never leaves the feed. Backed by
 * POST /api/ai-chat. The conversation lives only while the panel is open: closing it and
 * reopening starts fresh, same as the website.
 */
@Composable
fun RepoAskAiPanel(item: RepoItem, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val mono = LocalGhMono.current
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as? GitHuntApp)?.container?.aiRepository
    }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AiChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Index of the reply that is typing in. Older replies show in full.
    var animatedIndex by remember { mutableIntStateOf(-1) }
    val focusRequester = remember { FocusRequester() }

    val repoName = item.fullName.substringAfter('/')
    val starters = remember(item.fullName, item.language) {
        buildList {
            add("What does $repoName do?")
            add("How do I get started?")
            if (!item.language.isNullOrBlank()) add("Why pick this over alternatives?")
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || sending) return
        if (repository == null) {
            error = "Ask AI isn't available right now."
            return
        }
        if (question.length > MAX_QUESTION_LEN) {
            error = "Keep questions under $MAX_QUESTION_LEN characters."
            return
        }
        error = null
        val earlier = messages.toList()
        messages.add(AiChatMessage("user", question))
        input = ""
        sending = true
        scope.launch {
            when (val result = repository.ask(item, earlier, question)) {
                is ApiResult.Ok -> {
                    messages.add(AiChatMessage("assistant", result.value))
                    animatedIndex = messages.lastIndex
                }
                is ApiResult.Err -> error = result.message
            }
            sending = false
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathIcon(pathData = CardIcons.Sparkles, tint = scheme.onSurface, iconSize = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ask AI \u00B7 ${item.fullName}",
                fontFamily = mono,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                Text(
                    text = "Ask anything about this repo \u2014 what it does, how to use it, or how it compares to alternatives.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = scheme.onSurfaceVariant,
                )
                starters.forEach { prompt ->
                    val chip = RoundedCornerShape(12.dp)
                    Text(
                        text = prompt,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(chip)
                            .background(scheme.outline.copy(alpha = 0.2f))
                            .border(1.dp, scheme.outline.copy(alpha = 0.55f), chip)
                            .clickable(enabled = !sending) { send(prompt) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            messages.forEachIndexed { index, m ->
                ChatBubble(
                    isUser = m.role == "user",
                    text = m.content,
                    animate = index == animatedIndex,
                )
            }
            if (sending) TypingBubble()
            error?.let { message ->
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = scheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Composer: rounded pill input with the round send button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.outline.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "Ask about this repo",
                    enabled = !sending,
                    onSend = { send(input) },
                    modifier = Modifier.weight(1f),
                    textModifier = Modifier.focusRequester(focusRequester),
                    maxLength = MAX_QUESTION_LEN,
                )
                Spacer(Modifier.width(6.dp))
                SendCircle(enabled = !sending && input.isNotBlank(), onClick = { send(input) })
            }
        }
    }
}
