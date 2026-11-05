package com.githunt.android.ui.discover

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.discover.SortMode
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.Wordmark
import com.githunt.android.ui.components.languageColor
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.toggleSearch() },
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

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
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
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onBackground,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.toggleSearch()
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.error
                                    ?: if (state.search.isNotEmpty()) "No repos matched that search." else "No projects found. Try a different filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.onSurfaceVariant,
                                )
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
                                .clip(RoundedCornerShape(12.dp))
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    val fieldShape = RoundedCornerShape(12.dp)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(fieldShape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, fieldShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathIcon(pathData = NavIcons.Search, tint = scheme.onSurfaceVariant, iconSize = 18.dp)
            Spacer(Modifier.width(8.dp))
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
                        .size(24.dp)
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
                .height(44.dp)
                .clip(fieldShape)
                .background(scheme.onBackground)
                .clickable(onClick = onSubmit)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.background,
            )
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        PathIcon(pathData = NavIcons.ArrowDropDown, tint = scheme.onSurfaceVariant, iconSize = 20.dp)
    }
}

@Composable
private fun SortPill(mode: SortMode, onSelect: (SortMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        PillButton(
            label = mode.label,
            onClick = { open = true },
            leading = {
                PathIcon(pathData = sortIcon(mode), tint = scheme.onBackground, iconSize = 18.dp)
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onBackground,
                            )
                            Text(
                                text = option.sublabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        PathIcon(
                            pathData = sortIcon(option),
                            tint = if (option == mode) scheme.onBackground else scheme.onSurfaceVariant,
                            iconSize = 20.dp,
                        )
                    },
                    trailingIcon = {
                        if (option == mode) {
                            PathIcon(pathData = NavIcons.Check, tint = scheme.onBackground, iconSize = 20.dp)
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguagePill(language: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val label = LanguageOptions.firstOrNull { it.first == language }?.second ?: "Language"
    Box {
        PillButton(
            label = label,
            onClick = { open = true },
            leading = { LanguageMark(language, 18.dp) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LanguageOptions.forEach { (value, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onBackground,
                        )
                    },
                    leadingIcon = { LanguageMark(value, 20.dp) },
                    trailingIcon = {
                        if (value == language) {
                            PathIcon(pathData = NavIcons.Check, tint = scheme.onBackground, iconSize = 20.dp)
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** A colour dot for a language, or the code-brackets glyph for "All languages". */
@Composable
private fun LanguageMark(language: String, size: androidx.compose.ui.unit.Dp) {
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
