package com.githunt.android.ui.discover

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.discover.SortMode
import com.githunt.android.ui.components.CookieShape
import com.githunt.android.ui.components.GhLoadingIndicator
import com.githunt.android.ui.components.GhPullIndicator
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.Wordmark
import com.githunt.android.ui.components.animatedPressCorner
import com.githunt.android.ui.components.languageColor
import com.githunt.android.ui.settings.SettingsGroup
import com.githunt.android.ui.settings.SettingsRow
import com.githunt.android.ui.theme.GhMotion
import kotlinx.coroutines.launch

private val LanguageOptions = listOf(
    "" to "All languages",
    "JavaScript" to "JavaScript",
    "TypeScript" to "TypeScript",
    "Python" to "Python",
    "Java" to "Java",
    "Go" to "Go",
    "Rust" to "Rust",
    "C++" to "C++",
    "C" to "C",
    "C#" to "C#",
    "PHP" to "PHP",
    "Ruby" to "Ruby",
    "Swift" to "Swift",
    "Kotlin" to "Kotlin",
    "Dart" to "Dart",
    "HTML" to "HTML",
    "CSS" to "CSS",
    "Shell" to "Shell",
)

private fun sortIcon(mode: SortMode): String = when (mode) {
    SortMode.Trending -> NavIcons.TrendingUp
    SortMode.TopRated -> NavIcons.Star
    SortMode.Recent -> NavIcons.Sparkle
    SortMode.Active -> NavIcons.Bolt
    SortMode.Shared -> NavIcons.Share
}

/**
 * The website's Discover page: a Sort and a Language filter, a search box behind the magnifier,
 * and an endless list of repo cards with a clip (bookmark) on each.
 *
 * The two filters open as bottom sheets with segmented choice lists (the Material 3 Expressive
 * way) instead of small floating menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    userId: Long,
    scrollToTopTick: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var handledTick by rememberSaveable { mutableStateOf(scrollToTopTick) }

    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    LaunchedEffect(userId) { viewModel.bind(userId) }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick != handledTick) {
            handledTick = scrollToTopTick
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // Top bar: grey, runs up behind the status bar, magnifier on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceVariant)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(size = 26.sp)
            val searchInteraction = remember { MutableInteractionSource() }
            val searchCorner = animatedPressCorner(searchInteraction, rest = 22.dp, pressed = 12.dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(searchCorner))
                    .background(if (state.searchOpen) scheme.onBackground.copy(alpha = 0.12f) else scheme.onBackground.copy(alpha = 0f))
                    .clickable(
                        interactionSource = searchInteraction,
                        indication = LocalIndication.current,
                        onClick = { viewModel.toggleSearch() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PathIcon(
                    pathData = if (state.searchOpen) NavIcons.Close else NavIcons.Search,
                    tint = scheme.onBackground,
                    iconSize = 22.dp,
                )
            }
        }

        if (state.searchOpen) {
            SearchBar(
                input = state.searchInput,
                onInput = viewModel::onSearchInput,
                onSubmit = {
                    viewModel.submitSearch()
                    scope.launch { listState.scrollToItem(0) }
                },
                onClear = viewModel::clearSearchField,
            )
        }

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            state = pullState,
            indicator = {
                GhPullIndicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "filters") {
                    if (state.search.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SortPill(mode = state.mode) { mode ->
                                viewModel.setMode(mode)
                                scope.launch { listState.scrollToItem(0) }
                            }
                            LanguagePill(language = state.language) { language ->
                                viewModel.setLanguage(language)
                                scope.launch { listState.scrollToItem(0) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Results for \u201C${state.search}\u201D",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            val clearInteraction = remember { MutableInteractionSource() }
                            val clearShape = RoundedCornerShape(
                                animatedPressCorner(clearInteraction, rest = 18.dp, pressed = 9.dp),
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onBackground,
                                modifier = Modifier
                                    .clip(clearShape)
                                    .background(scheme.surfaceVariant)
                                    .clickable(
                                        interactionSource = clearInteraction,
                                        indication = LocalIndication.current,
                                    ) {
                                        viewModel.toggleSearch()
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                if (state.loading && state.items.isEmpty()) {
                    items(3, key = { index -> "skeleton-$index" }) {
                        RepoCardSkeleton()
                    }
                } else if (state.items.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CookieShape)
                                    .background(scheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                PathIcon(
                                    pathData = NavIcons.Search,
                                    tint = scheme.onSurfaceVariant,
                                    iconSize = 32.dp,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = state.error
                                    ?: if (state.search.isNotEmpty()) "No repos matched that search." else "No projects found. Try a different filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (state.error != null) {
                                Spacer(Modifier.height(16.dp))
                                OutlineButton(text = "Try again", onClick = { viewModel.refresh() })
                            }
                        }
                    }
                }

                items(state.items, key = { it.key }) { item ->
                    RepoCard(
                        item = item,
                        clipped = item.fullName in state.clipped,
                        onClipToggle = { viewModel.toggleClip(item) },
                    )
                }

                item(key = "footer") {
                    if (state.hasMore) {
                        // Reaching the footer means the person scrolled to the end: fetch the next page.
                        LaunchedEffect(state.items.size) { viewModel.loadMore() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadingMore) {
                                GhLoadingIndicator(diameter = 32.dp, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.error != null && state.items.isNotEmpty()) {
                        Text(
                            text = "Couldn't load more. Tap to retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.loadMore() }
                                .padding(16.dp),
                        )
                    }
                    if (!state.hasMore && state.items.isNotEmpty() && state.error == null) {
                        Text(
                            text = "That's everything for now.",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    input: String,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val fieldShape = RoundedCornerShape(28.dp)
    val buttonInteraction = remember { MutableInteractionSource() }
    val buttonShape = RoundedCornerShape(
        animatedPressCorner(buttonInteraction, rest = 28.dp, pressed = 14.dp),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Filled, borderless field: the expressive search bar look.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(fieldShape)
                .background(scheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathIcon(pathData = NavIcons.Search, tint = scheme.onSurfaceVariant, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text(
                        text = "Search repos \u2014 name, topic, or keywords",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onBackground),
                    cursorBrush = SolidColor(scheme.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            if (input.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    PathIcon(pathData = NavIcons.Close, tint = scheme.onSurfaceVariant, iconSize = 16.dp)
                }
            }
        }
        Box(
            modifier = Modifier
                .height(56.dp)
                .clip(buttonShape)
                .background(scheme.primary)
                .clickable(
                    interactionSource = buttonInteraction,
                    indication = LocalIndication.current,
                    onClick = onSubmit,
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimary,
            )
        }
    }
}

/**
 * A filter pill. Fully round at rest, springs into a squarer shape while pressed, fills with the
 * container colour when [active] (a filter is on), and turns its arrow while its sheet is [open].
 */
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    active: Boolean = false,
    open: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 22.dp, pressed = 11.dp)
    val container by animateColorAsState(
        targetValue = if (active) scheme.primaryContainer else scheme.surface,
        animationSpec = GhMotion.defaultEffects(),
        label = "pill-container",
    )
    val arrow by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = GhMotion.defaultSpatial(),
        label = "pill-arrow",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) scheme.onPrimaryContainer else scheme.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        PathIcon(
            pathData = NavIcons.ArrowDropDown,
            tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            iconSize = 20.dp,
            modifier = Modifier.rotate(arrow),
        )
    }
}

/** Bottom sheet shell shared by the Sort and Language pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.background,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            content(close)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SortPill(mode: SortMode, onSelect: (SortMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    PillButton(
        label = mode.label,
        onClick = { open = true },
        open = open,
        leading = {
            PathIcon(pathData = sortIcon(mode), tint = scheme.onBackground, iconSize = 18.dp)
        },
    )
    if (open) {
        PickerSheet(title = "Sort by", onDismiss = { open = false }) { close ->
            SettingsGroup {
                SortMode.values().forEach { option ->
                    SettingsRow(
                        title = option.label,
                        subtitle = option.sublabel,
                        icon = sortIcon(option),
                        selected = option == mode,
                        onClick = {
                            onSelect(option)
                            close()
                        },
                        trailing = {
                            if (option == mode) {
                                PathIcon(pathData = NavIcons.Check, tint = scheme.onPrimaryContainer, iconSize = 22.dp)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePill(language: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val label = LanguageOptions.firstOrNull { it.first == language }?.second ?: "Language"
    PillButton(
        label = label,
        onClick = { open = true },
        open = open,
        active = language.isNotEmpty(),
        leading = { LanguageMark(language, 18.dp) },
    )
    if (open) {
        PickerSheet(title = "Language", onDismiss = { open = false }) { close ->
            SettingsGroup {
                LanguageOptions.forEach { (value, name) ->
                    SettingsRow(
                        title = name,
                        selected = value == language,
                        leading = {
                            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                LanguageMark(value, 24.dp)
                            }
                        },
                        onClick = {
                            onSelect(value)
                            close()
                        },
                        trailing = {
                            if (value == language) {
                                PathIcon(pathData = NavIcons.Check, tint = scheme.onPrimaryContainer, iconSize = 22.dp)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** A colour dot for a language, or the code-brackets glyph for "All languages". */
@Composable
private fun LanguageMark(language: String, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val color = languageColor(language)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(size * 0.6f)
                    .clip(CircleShape)
                    .background(color),
            )
        } else {
            PathIcon(pathData = NavIcons.Code, tint = scheme.onBackground, iconSize = size)
        }
    }
}
