package com.githunt.android.ui.components

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.githunt.android.ui.theme.GhMotion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.githunt.android.BuildConfig
import com.githunt.android.GitHuntApp
import com.githunt.android.data.engagement.EngagementEvent
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.text
import com.githunt.android.data.settings.TranslationLanguages
import com.githunt.android.data.translate.TranslatedCard
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * One repo, as the website draws it. Used by Home (the feed), Discover and the profile tabs, so
 * all of them render identically.
 * Pass [onClipToggle] to show the bookmark; [clipped] says whether it is filled.
 *
 * Text uses the website's two faces: Plus Jakarta Sans for normal text and Inter (the site's
 * `font-mono` class) for stats, times, topic chips and pill buttons.
 *
 * "Show README" expands the README inside the card, like the website. If the app container
 * isn't available (previews, tests) it opens the repo's README on GitHub instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepoCard(
    item: RepoItem,
    modifier: Modifier = Modifier,
    clipped: Boolean = false,
    onClipToggle: (() -> Unit)? = null,
    /** Shows the card's "..." menu with Not interested (Home feed). The card is hidden by the feed. */
    notInterestedEnabled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val mono = LocalGhMono.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    val githubUrl = item.url
    val repoName = item.fullName.substringAfter('/')
    val readmeRepository = remember(context) {
        (context.applicationContext as? GitHuntApp)?.container?.readmeRepository
    }

    // Translation. The language from Settings ("off" = show originals) decides the target. The
    // text is translated for display only: `item` is untouched, so clipping still sends the repo
    // exactly as the server gave it. Results are cached, so scrolling back is instant.
    val appContainer = remember(context) { (context.applicationContext as? GitHuntApp)?.container }
    val translateRepository = appContainer?.translateRepository
    val targetLanguage by (appContainer?.appPreferences?.language ?: NoTranslation).collectAsState()
    // Which engine is selected (Settings > Translation language). It is a key below, so switching
    // engines translates the cards on screen again with the new one.
    val engine by (appContainer?.appPreferences?.engine ?: NoEngine).collectAsState()
    var translated by remember(item.fullName, item.description, item.readmeSnippet, targetLanguage, engine) {
        mutableStateOf<TranslatedCard?>(null)
    }
    var showOriginal by rememberSaveable(item.fullName + "#original") { mutableStateOf(false) }
    LaunchedEffect(item.fullName, item.description, item.readmeSnippet, item.topics, targetLanguage, engine) {
        if (translateRepository == null || targetLanguage == "off") return@LaunchedEffect
        val result = translateRepository.translateCard(
            description = item.description,
            snippet = item.readmeSnippet,
            topics = item.topics,
            target = targetLanguage,
        )
        // Only keep it when something really changed, so cards already in the target language
        // don't show a "Translated" line.
        if (result.changed) translated = result
    }
    val shownTranslation = if (showOriginal) null else translated
    val shownDescription = shownTranslation?.description ?: item.description
    val shownSnippet = shownTranslation?.snippet ?: item.readmeSnippet
    val shownTopics = shownTranslation?.topics ?: item.topics

    // README state. `expanded` survives scrolling the card off screen and back; the text itself
    // comes from the repository's in-memory cache when the card is reopened.
    var expanded by rememberSaveable(item.fullName) { mutableStateOf(false) }
    var markdown by remember(item.fullName) { mutableStateOf<String?>(null) }
    var loadingReadme by remember(item.fullName) { mutableStateOf(false) }
    var readmeError by remember(item.fullName) { mutableStateOf<String?>(null) }

    // What the person does with this card is reported to the backend (like the website's
    // recordEngagement) so Personalization can learn from it. `dwell` times how long the card is
    // in view; `track` reports one deliberate action and marks the look as engaged, so a quick
    // "open the README, scroll on" is never counted as a skip.
    val tracker = remember(context) { (context.applicationContext as? GitHuntApp)?.container?.engagementTracker }
    val dwell = rememberCardDwell(item, tracker)
    fun track(event: String) {
        dwell.engaged = true
        tracker?.record(item, event)
    }

    // "Ask AI" panel. Like the website, closing it throws the conversation away.
    var askOpen by rememberSaveable(item.fullName + "#ask") { mutableStateOf(false) }

    // Closing a long README or a long Ask AI chat shrinks this card by a lot. If the person has
    // scrolled down inside it, the list is then left pointing past the end of the shrunken card and
    // snaps to whatever repo comes next. So before a panel closes, scroll the top of this card back
    // into view (a no-op when it is already visible) and only then collapse it, which keeps the
    // card, and the person's place in the feed, exactly where they opened the panel.
    val scope = rememberCoroutineScope()
    val topRequester = remember { BringIntoViewRequester() }
    val topGapPx = with(LocalDensity.current) { 12.dp.toPx() }
    fun closeKeepingCardInView(close: () -> Unit) {
        scope.launch {
            topRequester.bringIntoView(Rect(left = 0f, top = -topGapPx, right = 1f, bottom = 1f))
            close()
        }
    }

    LaunchedEffect(expanded, item.fullName) {
        if (expanded && markdown == null && readmeRepository != null) {
            loadingReadme = true
            readmeError = null
            readmeRepository.load(item.owner, repoName)
                .onSuccess { markdown = it }
                .onFailure {
                    readmeError = it.message ?: "Could not load the README."
                    expanded = false
                }
            loadingReadme = false
        }
    }

    // README translation is an explicit action. "Show README" always opens the full original at
    // once; "Translate README" then turns every block that has words in it into a skeleton, and each
    // skeleton is replaced by plain translated text as soon as that block arrives, top to bottom.
    // Closing the card cancels it; reopening is instant for what was already translated, because
    // the repository caches each piece.
    val defaultBranch = item.raw?.text("default_branch")
    var readmeTranslate by rememberSaveable(item.fullName + "#readme-translate") { mutableStateOf(false) }
    var readmeShowOriginal by rememberSaveable(item.fullName + "#readme-original") { mutableStateOf(false) }
    var readmeAttempt by remember(item.fullName, targetLanguage) { mutableIntStateOf(0) }
    var readmeBlocks by remember(item.fullName, targetLanguage, markdown) { mutableStateOf<TranslatingReadme?>(null) }
    var readmeBusy by remember(item.fullName, targetLanguage) { mutableStateOf(false) }
    var readmeFinished by remember(item.fullName, targetLanguage, markdown) { mutableStateOf(false) }
    var readmeIncomplete by remember(item.fullName, targetLanguage) { mutableStateOf(false) }
    var readmeChanged by remember(item.fullName, targetLanguage) { mutableStateOf(false) }
    LaunchedEffect(expanded, markdown, targetLanguage, readmeTranslate, readmeAttempt) {
        val source = markdown
        if (!expanded || !readmeTranslate || source == null || translateRepository == null || targetLanguage == "off") {
            return@LaunchedEffect
        }
        if (readmeFinished && !readmeIncomplete) return@LaunchedEffect
        readmeBusy = true
        readmeIncomplete = false
        readmeChanged = false
        try {
            val prepared = translateRepository.prepareReadmeBlocks(
                markdown = withContext(Dispatchers.Default) {
                    prepareReadme(source, item.owner, repoName, defaultBranch, MaxReadmeChars)
                },
                target = targetLanguage,
            )
            val state = TranslatingReadme(prepared.blocks, prepared.needsTranslation)
            readmeBlocks = state
            val result = translateRepository.translateReadmeBlocks(prepared, targetLanguage) { index, text ->
                state.translated[index] = text
                state.waiting[index] = false
            }
            readmeIncomplete = result.incomplete
            readmeChanged = result.changedBlocks > 0
            readmeFinished = true
        } finally {
            readmeBusy = false
        }
    }

    // No whole-card click: it would fight the README text and links. Like the website,
    // GitHub opens from the button and the preview image.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(topRequester)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape)
            .trackDwell(dwell),
    ) {
        // The website's full-bleed README media preview. One picture, or a swipeable carousel with
        // a dot pager and auto-scroll when the README has several. Shows a shimmer while each
        // image loads and drops images that can't be loaded, like the website.
        if (item.readmeImages.isNotEmpty() || item.readmeVideos.isNotEmpty()) {
            RepoImageCarousel(
                images = item.readmeImages,
                videos = item.readmeVideos,
                contentDescription = "${item.fullName} README preview",
                onOpenGithub = {
                    runCatching { uriHandler.openUri(githubUrl ?: item.readmeImages.first()) }
                },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepoAvatar(url = item.avatarUrl, size = 36.dp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Web: font-semibold text-[15px] leading-snug
                    Text(
                        text = repoName,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Web: text-[12.5px] text-dim (regular weight, Plus Jakarta Sans)
                        Text(
                            text = item.owner,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Normal,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val ago = relativeTime(item.updatedAt)
                        if (ago.isNotEmpty()) {
                            // Web: text-[12px] font-mono (Inter)
                            Text(
                                text = " \u00B7 $ago",
                                fontFamily = mono,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                RepoMoreMenu(
                    onOpenRepo = {
                        track(EngagementEvent.GITHUB_CLICK)
                        githubUrl?.let { runCatching { uriHandler.openUri(it) } }
                    },
                    onOpenOwner = {
                        runCatching { uriHandler.openUri("https://github.com/${item.owner}") }
                    },
                    onNotInterested = if (notInterestedEnabled && tracker != null) {
                        ({ tracker.notInterested(item) })
                    } else {
                        null
                    },
                )
            }

            shownDescription?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The short README excerpt is replaced by the full README once it is open.
            if (!expanded) {
                shownSnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (shownTopics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    shownTopics.take(3).forEach { topic ->
                        Text(
                            text = topic,
                            fontFamily = mono,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .border(1.dp, scheme.outline.copy(alpha = 0.65f), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // While the README is open this line is its translate button: "Translate README to X",
            // then the progress, then a switch between the translation and the original. Otherwise
            // (and only when something was really translated) one tap flips the card text
            // (description, excerpt, topics) between the two languages.
            val readmeLine = expanded && translateRepository != null && targetLanguage != "off"
            if (readmeLine) {
                val readmeSource = markdown
                if (readmeSource != null) {
                    val languageLabel = TranslationLanguages.label(targetLanguage)
                    val working = readmeBusy || (readmeTranslate && !readmeFinished)
                    val alreadyInLanguage = readmeTranslate && readmeFinished && !readmeIncomplete && !readmeChanged
                    val blocksState = readmeBlocks
                    val label = when {
                        working -> if (blocksState != null && blocksState.total > 0) {
                            "Translating README\u2026 ${blocksState.finished}/${blocksState.total}"
                        } else {
                            "Translating README\u2026"
                        }
                        !readmeTranslate -> "Translate README to $languageLabel"
                        readmeIncomplete -> "Some parts could not be translated \u00B7 Retry"
                        alreadyInLanguage -> "README is already in $languageLabel"
                        readmeShowOriginal -> "Original \u00B7 Show translation"
                        else -> "Translated to $languageLabel \u00B7 Show original"
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontFamily = mono,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !working && !alreadyInLanguage) {
                                when {
                                    !readmeTranslate -> {
                                        readmeShowOriginal = false
                                        readmeTranslate = true
                                    }
                                    readmeIncomplete -> {
                                        readmeFinished = false
                                        readmeAttempt++
                                    }
                                    else -> readmeShowOriginal = !readmeShowOriginal
                                }
                            }
                            .padding(vertical = 2.dp),
                    )
                }
            } else if (translated != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (showOriginal) {
                        "Original \u00B7 Show translation"
                    } else {
                        "Translated to ${TranslationLanguages.label(targetLanguage)} \u00B7 Show original"
                    },
                    fontFamily = mono,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showOriginal = !showOriginal }
                        .padding(vertical = 2.dp),
                )
            }

            // While the README downloads, a skeleton holds its place so the card feels alive.
            AnimatedVisibility(
                visible = loadingReadme,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(10.dp))
                    ReadmeSkeleton()
                }
            }

            // The README opens here, above the buttons, like the website.
            val readmeText = markdown
            AnimatedVisibility(
                visible = expanded && readmeText != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (readmeText != null) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
                        Spacer(Modifier.height(10.dp))
                        val blockState = readmeBlocks
                        if (readmeTranslate && !readmeShowOriginal && blockState != null && targetLanguage != "off") {
                            // Skeletons that turn into translated text, one block at a time.
                            TranslatedReadmeBlocks(state = blockState)
                        } else {
                            // The full original, straight away.
                            ReadmeView(
                                markdown = readmeText,
                                owner = item.owner,
                                repo = repoName,
                                branch = defaultBranch,
                            )
                        }
                    }
                }
            }

            readmeError?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            // Ask AI opens here, above the buttons, the same place the README opens.
            AnimatedVisibility(
                visible = askOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    RepoAskAiPanel(item = item)
                    Spacer(Modifier.height(12.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(
                    text = when {
                        loadingReadme -> "Loading\u2026"
                        expanded -> "Show less"
                        else -> "Show README"
                    },
                    modifier = Modifier.weight(1f),
                    chevronUp = if (loadingReadme) null else expanded,
                    enabled = !loadingReadme,
                    onClick = {
                        if (readmeRepository == null) {
                            // No container available: fall back to GitHub's own README view.
                            githubUrl?.trimEnd('/')?.plus("#readme")
                                ?.let { runCatching { uriHandler.openUri(it) } }
                        } else {
                            readmeError = null
                            if (!expanded) {
                                track(EngagementEvent.README)
                                expanded = true
                            } else {
                                closeKeepingCardInView { expanded = false }
                            }
                        }
                    },
                )
                ActionPill(
                    text = "Ask AI",
                    modifier = Modifier.weight(1f),
                    leadingIcon = CardIcons.Sparkles,
                    chevronUp = askOpen,
                    active = askOpen,
                    onClick = {
                        if (!askOpen) {
                            track(EngagementEvent.ASK_AI)
                            askOpen = true
                        } else {
                            closeKeepingCardInView { askOpen = false }
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatChip(icon = CardIcons.Star, value = formatCount(item.stars), tint = scheme.onSurfaceVariant)
                StatChip(icon = CardIcons.Fork, value = formatCount(item.forks), tint = scheme.onSurfaceVariant)
                val language = item.language
                if (language != null) {
                    // Takes the free space, so the GitHub pill ends up flush right.
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        languageColor(language)?.let { dot ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dot),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = language,
                            fontFamily = mono,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                GitHubPill(
                    onClick = {
                        track(EngagementEvent.GITHUB_CLICK)
                        githubUrl?.let { runCatching { uriHandler.openUri(it) } }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardAction(
                    icon = CardIcons.Share,
                    label = "Share",
                    tint = scheme.onSurfaceVariant,
                    onClick = {
                        githubUrl?.let { link ->
                            track(EngagementEvent.SHARE)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, link)
                            }
                            context.startActivity(Intent.createChooser(send, "Share repository"))
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                if (onClipToggle != null) {
                    CardAction(
                        icon = CardIcons.Clip,
                        filled = clipped,
                        label = if (clipped) "Clipped" else "Clip",
                        tint = if (clipped) scheme.onBackground else scheme.onSurfaceVariant,
                        onClick = {
                            track(if (clipped) EngagementEvent.UNCLIP else EngagementEvent.CLIP)
                            onClipToggle()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(icon: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PathIcon(pathData = icon, tint = tint, iconSize = 14.dp, strokeWidth = 1.8f)
        Spacer(Modifier.width(4.dp))
        Text(text = value, fontFamily = LocalGhMono.current, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun CardAction(
    icon: String,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(animatedPressCorner(interaction, rest = 20.dp, pressed = 10.dp))
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathIcon(pathData = icon, tint = tint, iconSize = 18.dp, strokeWidth = 1.8f, filled = filled)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = LocalGhMono.current,
            fontSize = 12.sp,
            color = tint,
        )
    }
}

/**
 * Web: `px-3.5 py-1.5 rounded-full border text-[11.5px] font-mono font-medium`.
 * [chevronUp] null = no chevron; false = pointing down; true = pointing up (open).
 */
@Composable
private fun ActionPill(
    text: String,
    modifier: Modifier = Modifier,
    chevronUp: Boolean? = null,
    enabled: Boolean = true,
    leadingIcon: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The Ask AI pill inverts (light fill, dark text) while its panel is open.
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 22.dp, pressed = 10.dp)
    val container by animateColorAsState(
        targetValue = if (active) scheme.onSurface else scheme.surfaceVariant,
        animationSpec = GhMotion.defaultEffects(),
        label = "action-pill-container",
    )
    val content = if (active) scheme.surface else scheme.onSurface
    val chevronTurn by animateFloatAsState(
        targetValue = if (chevronUp == true) 180f else 0f,
        animationSpec = GhMotion.defaultSpatial(),
        label = "action-pill-chevron",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                PathIcon(pathData = leadingIcon, tint = content, iconSize = 13.dp, filled = true)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontFamily = LocalGhMono.current,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chevronUp != null) {
                Spacer(Modifier.width(6.dp))
                PathIcon(
                    pathData = CardIcons.ChevronDown,
                    tint = content,
                    iconSize = 12.dp,
                    strokeWidth = 2f,
                    modifier = Modifier.rotate(chevronTurn),
                )
            }
        }
    }
}

/** Small "GitHub ›" pill at the right end of the stats row (replaces the old Open GitHub button). */
@Composable
private fun GitHubPill(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(animatedPressCorner(interaction, rest = 18.dp, pressed = 8.dp))
    Row(
        modifier = Modifier
            .clip(shape)
            .background(scheme.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GitHub",
            fontFamily = LocalGhMono.current,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        PathIcon(
            pathData = CardIcons.ChevronRight,
            tint = scheme.onSurfaceVariant,
            iconSize = 12.dp,
            strokeWidth = 2f,
        )
    }
}

/** Stands in for the language setting when no app container exists (previews, tests). */
private val NoTranslation: StateFlow<String> = MutableStateFlow("off")

/** The same for the translation engine setting. */
private val NoEngine: StateFlow<com.githunt.android.data.settings.TranslationEngine> =
    MutableStateFlow(com.githunt.android.data.settings.TranslationEngine.Server)

/** Hosts the website's /api/img-proxy accepts. Anything else is loaded directly. */
private val ProxyHosts = setOf(
    "raw.githubusercontent.com",
    "user-images.githubusercontent.com",
    "camo.githubusercontent.com",
    "github.com",
    "avatars.githubusercontent.com",
)

/**
 * Routes GitHub-hosted images through the backend's /api/img-proxy, the same as the website,
 * so they come from one cached origin instead of hotlinking GitHub on every view.
 */
internal fun proxiedImageUrl(src: String): String {
    val host = runCatching { Uri.parse(src).host }.getOrNull()
    if (host == null || host !in ProxyHosts) return src
    return "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/img-proxy?src=${Uri.encode(src)}"
}

// Shape of the picture area before a picture has loaded, and the limits it is kept within after:
// at most 1/0.6 (about 1.7x) as tall as wide, and at most 3x as wide as tall. A picture outside
// that range still shows whole, it just has a little empty space beside or above it.
private const val DEFAULT_IMAGE_RATIO = 16f / 9f

// A YouTube slide is the key "yt:<id>" in the carousel's slide list; a real picture url never starts with it.
// Videos are always a 16:9 player, so they use DEFAULT_IMAGE_RATIO.
private const val VIDEO_KEY_PREFIX = "yt:"
private const val MIN_IMAGE_RATIO = 0.6f
private const val MAX_IMAGE_RATIO = 3f

/** Picture url -> width / height, for the whole session. Only touched from the main thread. */
private val ImageRatioCache = HashMap<String, Float>()

// A calmer slide interval, with a longer pause after direct touch so browsing never feels rushed.
private const val AUTOPLAY_INTERVAL_MS = 5000L
private const val RESUME_AFTER_INTERACTION_MS = 8000L

/**
 * The card's README picture area: the website's ImageCarousel.
 *
 * - Every picture is shown whole, never cropped or stretched, and the area takes the picture's own
 *   shape (width / height), so the card is exactly as tall as the image needs. Very tall or very
 *   wide pictures are limited to [MIN_IMAGE_RATIO]..[MAX_IMAGE_RATIO] and fit inside it.
 * - Tapping a picture opens a full-screen preview (swipe, pinch to zoom); GitHub opens from there.
 * - One image: just the picture. Several: swipe sideways, with a small dot pager pill at the
 *   bottom (the active dot stretches into a bar).
 * - With several images it auto-advances every 5s (looping back to the first), but only while
 *   the card is mostly on screen (60%+), the app is in the foreground, and the system's
 *   animations aren't switched off. Touching or holding the image pauses autoplay while the
 *   finger is down, then keeps it paused for 8s after release.
 * - README images are often SVG, so the SVG decoder is on.
 * - The 16:9 box is reserved up front so nothing jumps; a shimmer fills each image while it loads.
 *   An image that can't be loaded is dropped from the carousel, and if none load the whole
 *   block disappears (the website's SmartImage does the same).
 */
@Composable
private fun RepoImageCarousel(
    images: List<String>,
    videos: List<String>,
    contentDescription: String,
    onOpenGithub: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var failed by remember(images) { mutableStateOf(setOf<String>()) }
    val slides = remember(images, failed) { images.distinct().filter { it !in failed } }
    val pagerState = rememberPagerState(pageCount = { slides.size })

    // Each picture's own width / height, learned as it loads. Also kept for the whole session,
    // so a card scrolled back into view starts at the right height instead of jumping again.
    val ratios = remember(images) {
        mutableStateMapOf<String, Float>().apply {
            images.forEach { url -> ImageRatioCache[url]?.let { put(url, it) } }
        }
    }
    val currentUrl = slides.getOrNull(pagerState.currentPage)
    val targetRatio = (currentUrl?.let { ratios[it] } ?: DEFAULT_IMAGE_RATIO)
        .coerceIn(MIN_IMAGE_RATIO, MAX_IMAGE_RATIO)
    val boxRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 250),
        label = "carousel-ratio",
    )

    // Index of the picture open in the full-screen preview, or null when it is closed.
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    var onScreen by remember { mutableStateOf(false) }
    var pausedUntil by remember { mutableLongStateOf(0L) }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Keep autoplay paused for the entire time a finger is on the image, including long presses.
    // After release, leave an extra pause so the person has time to inspect the selected image.
    val touchPauseModifier = Modifier.pointerInput(pagerState) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
            pausedUntil = Long.MAX_VALUE
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            pausedUntil = SystemClock.elapsedRealtime() + RESUME_AFTER_INTERACTION_MS
        }
    }

    // Auto-advance. Restarts whenever the slide count, on-screen state or motion setting changes,
    // and stops while the app is in the background.
    LaunchedEffect(slides.size, onScreen, reduceMotion) {
        if (slides.size <= 1 || !onScreen || reduceMotion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(AUTOPLAY_INTERVAL_MS)
                if (SystemClock.elapsedRealtime() < pausedUntil || pagerState.isScrollInProgress) continue
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % slides.size)
            }
        }
    }

    if (slides.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(boxRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(touchPauseModifier)
            .onGloballyPositioned { coordinates ->
                // "On screen" = at least 60% of the carousel is inside the window.
                val bounds = coordinates.boundsInWindow()
                val total = bounds.height
                val visible = minOf(bounds.bottom, view.height.toFloat()) - maxOf(bounds.top, 0f)
                onScreen = total > 0f && visible / total >= 0.6f
            },
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = slides.size > 1,
            modifier = Modifier.matchParentSize(),
        ) { page ->
            val url = slides[page]
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(proxiedImageUrl(url))
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(350)
                    .build()
            }
            ShimmerImage(
                model = request,
                contentDescription = contentDescription,
                // Fit, not Crop: the whole picture is always visible.
                contentScale = ContentScale.Fit,
                onFailed = { failed = failed + url },
                onLoaded = { width, height ->
                    val ratio = width / height
                    ImageRatioCache[url] = ratio
                    ratios[url] = ratio
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        previewIndex = page
                        // Keep the carousel still behind the preview.
                        pausedUntil = Long.MAX_VALUE
                    },
            )
        }

        previewIndex?.let { start ->
            ImagePreviewDialog(
                images = slides,
                startIndex = start,
                contentDescription = contentDescription,
                onDismiss = {
                    previewIndex = null
                    pausedUntil = SystemClock.elapsedRealtime() + RESUME_AFTER_INTERACTION_MS
                },
                onOpenGithub = {
                    previewIndex = null
                    pausedUntil = SystemClock.elapsedRealtime() + RESUME_AFTER_INTERACTION_MS
                    onOpenGithub()
                },
            )
        }

        if (slides.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = pagerState.currentPage
                repeat(slides.size) { index ->
                    val selected = index == current
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 14.dp else 6.dp,
                        animationSpec = tween(durationMillis = 150),
                        label = "carousel-dot",
                    )
                    Box(
                        Modifier
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 1f else 0.5f)),
                    )
                }
            }
        }
    }
}

/** Round avatar. A shimmer circle shows until the picture has loaded, then fades into it. */
@Composable
fun RepoAvatar(url: String?, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(proxiedImageUrl(url))
                    .crossfade(250)
                    .build()
            }
            ShimmerImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** "950", "1.2k", "19k", "1.5M". Drops a trailing ".0" like the website does. */
fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1f", n / 1_000_000.0).removeSuffix(".0") + "M"
    n >= 1_000 -> String.format(Locale.US, "%.1f", n / 1_000.0).removeSuffix(".0") + "k"
    else -> n.toString()
}

/** "now", "5m", "3h", "2d", "4mo" from an ISO timestamp. Empty when it can't be read. */
fun relativeTime(value: String?): String {
    if (value == null) return ""
    return try {
        val elapsed = Duration.between(Instant.parse(value), Instant.now())
        when {
            elapsed.toMinutes() < 1 -> "now"
            elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m"
            elapsed.toDays() < 1 -> "${elapsed.toHours()}h"
            elapsed.toDays() < 30 -> "${elapsed.toDays()}d"
            else -> "${elapsed.toDays() / 30}mo"
        }
    } catch (_: DateTimeParseException) {
        ""
    }
}

private val LanguageColors = mapOf(
    "JavaScript" to 0xFFF1E05A,
    "TypeScript" to 0xFF3178C6,
    "Python" to 0xFF3572A5,
    "Java" to 0xFFB07219,
    "Go" to 0xFF00ADD8,
    "Rust" to 0xFFDEA584,
    "C++" to 0xFFF34B7D,
    "C" to 0xFF555555,
    "C#" to 0xFF178600,
    "PHP" to 0xFF4F5D95,
    "Ruby" to 0xFF701516,
    "Swift" to 0xFFF05138,
    "Kotlin" to 0xFFA97BFF,
    "Dart" to 0xFF00B4AB,
    "HTML" to 0xFFE34C26,
    "CSS" to 0xFF563D7C,
    "Shell" to 0xFF89E051,
)

/** GitHub's own colour for a language, or null when we don't have one. */
fun languageColor(language: String?): Color? =
    language?.let { name -> LanguageColors[name]?.let { Color(it) } }
