package com.githunt.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.ShimmerBlock
import com.githunt.android.ui.components.ShimmerImage
import com.githunt.android.ui.components.Wordmark
import com.githunt.android.ui.components.animatedPressCorner
import com.githunt.android.ui.components.languageColor
import com.githunt.android.ui.components.rememberTick
import java.util.Locale

/**
 * First-run setup, shown once right after sign-in. Step 1: favourite languages. Step 2: trending
 * repos in those languages to tap on. [onFinished] is called when the person finishes or skips.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    BackHandler(enabled = state.step == OnboardingStep.Repos && !state.saving) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(size = 26.sp)
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (state.saving) scheme.onSurfaceVariant.copy(alpha = 0.5f) else scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !state.saving) { viewModel.skip(onFinished) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState == OnboardingStep.Repos
                (slideInHorizontally(tween(300)) { width -> if (forward) width / 4 else -width / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { width -> if (forward) -width / 4 else width / 4 } + fadeOut(tween(200)))
            },
            label = "onboarding-step",
        ) { step ->
            when (step) {
                OnboardingStep.Languages -> LanguageStep(
                    selected = state.languages,
                    onToggle = viewModel::toggleLanguage,
                )
                OnboardingStep.Repos -> RepoStep(
                    loading = state.loading,
                    error = state.error,
                    repos = state.repos,
                    picked = state.picked,
                    onToggle = viewModel::togglePick,
                    onRetry = viewModel::retry,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            when (state.step) {
                OnboardingStep.Languages -> {
                    val count = state.languages.size
                    PrimaryButton(
                        text = if (count == 0) "Pick at least one" else "Continue ($count)",
                        onClick = viewModel::continueToRepos,
                        enabled = count > 0,
                    )
                }
                OnboardingStep.Repos -> {
                    val count = state.picked.size
                    PrimaryButton(
                        text = if (count == 0) "Finish" else "Finish ($count)",
                        onClick = { viewModel.finish(onFinished) },
                        enabled = !state.loading,
                        loading = state.saving,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        text = "Change languages",
                        onClick = viewModel::back,
                        enabled = !state.saving,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "What do you code in?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick up to $MAX_ONBOARDING_LANGUAGES. We'll tune your feed around them.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingLanguages.forEach { language ->
                LanguageChip(
                    name = language,
                    selected = language in selected,
                    onClick = { onToggle(language) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LanguageChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 22.dp, pressed = 11.dp)
    val tick = rememberTick()
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surface,
        label = "chip-container",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = {
                    tick()
                    onClick()
                },
            )
            .padding(start = 12.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                PathIcon(pathData = NavIcons.Check, tint = scheme.onPrimaryContainer, iconSize = 18.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(languageColor(name) ?: scheme.onSurfaceVariant),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.onPrimaryContainer else scheme.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun RepoStep(
    loading: Boolean,
    error: String?,
    repos: List<RepoItem>,
    picked: Set<String>,
    onToggle: (RepoItem) -> Unit,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Anything catch your eye?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap the repos you find interesting. Leave the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        when {
            loading -> Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(5) {
                    ShimmerBlock(
                        Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }
            }
            error != null && repos.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlineButton(text = "Try again", onClick = onRetry)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(repos, key = { item -> item.key }) { item ->
                    RepoPickTile(
                        item = item,
                        picked = item.fullName in picked,
                        onClick = { onToggle(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoPickTile(
    item: RepoItem,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberTick()
    val container by animateColorAsState(
        targetValue = if (picked) scheme.primaryContainer else scheme.surface,
        label = "tile-container",
    )
    val circleFill by animateColorAsState(
        targetValue = if (picked) scheme.primary else scheme.surface,
        label = "tile-check",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable {
                tick()
                onClick()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ShimmerImage(
            model = item.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = item.description
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val language = item.language
                if (language != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(languageColor(language) ?: scheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = "\u2605 ${onboardingCount(item.stars)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(circleFill)
                .border(1.5.dp, if (picked) scheme.primary else scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (picked) {
                PathIcon(pathData = NavIcons.Check, tint = scheme.onPrimary, iconSize = 18.dp)
            }
        }
    }
}

private fun onboardingCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
    else -> n.toString()
}
