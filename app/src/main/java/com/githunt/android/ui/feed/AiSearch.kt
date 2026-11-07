package com.githunt.android.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.githunt.android.ui.theme.FocusBlue
import com.githunt.android.ui.theme.SoftBlue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.GitHuntApp
import com.githunt.android.data.ai.AiChatMessage
import com.githunt.android.data.ai.AiRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.ui.components.CardIcons
import com.githunt.android.ui.components.ChatBubble
import com.githunt.android.ui.components.ChatTextField
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.SendCircle
import com.githunt.android.ui.components.TypingBubble
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.launch

/** A repo search the model suggested but the person has not run yet (the MIXED case). */
data class AiPendingSearch(val q: String, val language: String)

/**
 * The Repository / Users / Topic chips under the box. Like the website, this is only a visual
 * selection for now: the AI search is repo search end to end, so only Repository does anything.
 */
enum class AiSearchFilter(val label: String, val icon: String) {
    Repository("Repository", CardIcons.Folder),
    Users("Users", CardIcons.User),
    Topic("Topic", CardIcons.Tag),
}

/** Where one search stands: running, finished with repos, or failed. */
data class AiSearchBlock(
    val loading: Boolean = false,
    val items: List<RepoItem>? = null,
    val error: String? = null,
)

data class AiMessage(
    val id: Long,
    val role: String,
    val content: String,
    val isError: Boolean = false,
    /** The person's text that led to this reply, used to label its results. */
    val query: String? = null,
    val pending: AiPendingSearch? = null,
    val block: AiSearchBlock? = null,
)

/**
 * The Home AI search box, like the website's. Each message goes to /api/ai-search, which reads
 * intent:
 *  - CHAT: just a reply, no search.
 *  - SEARCH: a clear "find me a repo" request; the search runs straight away and its results
 *    show as ordinary repo cards under the box.
 *  - MIXED: a reply plus a "Search for this repo" link; nothing runs until it is tapped.
 * The model only ever picks search terms. Results always come from a real GitHub search.
 */
@Stable
class AiSearchState(
    private val repository: AiRepository?,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    val messages = mutableStateListOf<AiMessage>()
    var input by mutableStateOf("")
    var filter by mutableStateOf(AiSearchFilter.Repository)
    var loading by mutableStateOf(false)
        private set

    private var nextId = 0L

    /** Bumped by [clear] so replies still in flight are dropped instead of reappearing. */
    private var epoch = 0

    val hasConversation: Boolean get() = messages.isNotEmpty()

    fun submit() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        input = ""

        // Earlier turns give follow-ups context. Error bubbles are ours, not the model's.
        val history = messages
            .filter { !it.isError && it.content.isNotBlank() }
            .takeLast(10)
            .map { AiChatMessage(it.role, it.content) }

        messages.add(AiMessage(nextId++, "user", text))

        val repo = repository
        if (repo == null) {
            messages.add(AiMessage(nextId++, "assistant", "AI search isn't available right now.", isError = true))
            return
        }

        loading = true
        val myEpoch = epoch
        scope.launch {
            val result = repo.interpret(text, history)
            if (myEpoch != epoch) return@launch
            when (result) {
                is ApiResult.Ok -> {
                    val reply = result.value
                    val intent = reply.intent.uppercase()
                    val q = reply.q.trim()
                    val wantsSearch = (intent == "SEARCH" || intent == "MIXED") && q.isNotEmpty()
                    val pending = AiPendingSearch(q, reply.language)
                    val id = nextId++
                    messages.add(
                        AiMessage(
                            id = id,
                            role = "assistant",
                            content = reply.reply,
                            query = text,
                            pending = if (intent == "MIXED" && wantsSearch) pending else null,
                        ),
                    )
                    loading = false
                    if (intent == "SEARCH" && wantsSearch) runSearch(id, pending)
                }
                is ApiResult.Err -> {
                    messages.add(AiMessage(nextId++, "assistant", result.message, isError = true))
                    loading = false
                }
            }
        }
    }

    /** Runs the GitHub search for one assistant message and stores the outcome on it. */
    fun runSearch(messageId: Long, pending: AiPendingSearch) {
        val repo = repository ?: return
        val myEpoch = epoch
        update(messageId) { it.copy(block = AiSearchBlock(loading = true)) }
        scope.launch {
            val result = repo.searchRepos(pending.q, pending.language)
            if (myEpoch != epoch) return@launch
            val block = when (result) {
                is ApiResult.Ok -> AiSearchBlock(items = result.value)
                is ApiResult.Err -> AiSearchBlock(error = result.message)
            }
            update(messageId) { it.copy(block = block) }
        }
    }

    fun clear() {
        epoch++
        messages.clear()
        input = ""
        loading = false
    }

    private fun update(id: Long, change: (AiMessage) -> AiMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = change(messages[index])
    }
}

@Composable
fun rememberAiSearchState(): AiSearchState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) {
        (context.applicationContext as? GitHuntApp)?.container?.aiRepository
    }
    return remember(repository) { AiSearchState(repository, scope) }
}

/** The search box and the conversation that grows above it. */
@Composable
fun AiSearchPanel(state: AiSearchState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(state.messages.size, state.loading) {
        scroll.animateScrollTo(Int.MAX_VALUE)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(2.dp, if (focused) scheme.primary else scheme.outline.copy(alpha = 0.7f), shape),
    ) {
        if (state.hasConversation) {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(scroll)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (m in state.messages) {
                    if (m.role == "user") {
                        key(m.id) { ChatBubble(isUser = true, text = m.content) }
                    } else if (m.content.isNotBlank()) {
                        key(m.id) {
                            val pending = m.pending
                            ChatBubble(isUser = false, text = m.content, isError = m.isError) {
                                if (pending != null) {
                                    val busy = m.block != null
                                    Text(
                                        text = when {
                                            m.block?.loading == true -> "Searching\u2026"
                                            busy -> "Searched \u2014 see results below"
                                            else -> "Search for this repo  \u203A"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (busy) 0.6f else 1f),
                                        textDecoration = if (busy) TextDecoration.None else TextDecoration.Underline,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clickable(enabled = !busy) { state.runSearch(m.id, pending) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.loading) TypingBubble()
            }
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathIcon(pathData = CardIcons.Search, tint = scheme.onSurfaceVariant, iconSize = 18.dp, strokeWidth = 1.8f)
            Spacer(Modifier.width(10.dp))
            ChatTextField(
                value = state.input,
                onValueChange = { state.input = it },
                placeholder = if (state.hasConversation) "Ask a follow-up\u2026" else "Search repos or ask GitHunt AI",
                enabled = !state.loading,
                onSend = { state.submit() },
                modifier = Modifier.weight(1f),
                textModifier = Modifier.onFocusChanged { focused = it.isFocused },
                maxLength = 300,
            )
            if (state.hasConversation) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { state.clear() },
                    contentAlignment = Alignment.Center,
                ) {
                    PathIcon(pathData = CardIcons.Close, tint = scheme.onSurfaceVariant, iconSize = 16.dp, strokeWidth = 2f)
                }
                Spacer(Modifier.width(4.dp))
            }
            SendCircle(enabled = !state.loading && state.input.isNotBlank(), onClick = { state.submit() })
        }
    }
}

/**
 * Search results as ordinary repo cards, straight into the feed's list right under the box.
 * Newest search first, so a follow-up's results are never pushed off the bottom of the phone.
 */
internal fun LazyListScope.aiSearchResults(state: AiSearchState) {
    val searched = state.messages.filter { it.block != null }.reversed()
    searched.forEachIndexed { index, m ->
        val block = m.block ?: return@forEachIndexed
        if (index == 0 && searched.size > 1) {
            item(key = "ai-res-${m.id}-label") {
                Text(
                    text = "Results for \u201C${m.query ?: "this search"}\u201D",
                    fontFamily = LocalGhMono.current,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            block.loading -> item(key = "ai-res-${m.id}-loading") { RepoCardSkeleton() }
            block.error != null -> item(key = "ai-res-${m.id}-error") {
                Text(
                    text = block.error,
                    fontFamily = LocalGhMono.current,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            block.items.isNullOrEmpty() -> item(key = "ai-res-${m.id}-empty") {
                Text(
                    text = "Nothing matched. Try describing it differently.",
                    fontFamily = LocalGhMono.current,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
            else -> items(block.items.orEmpty(), key = { "ai-res-${m.id}-${it.fullName}" }) { repo ->
                RepoCard(item = repo)
            }
        }
    }
}
