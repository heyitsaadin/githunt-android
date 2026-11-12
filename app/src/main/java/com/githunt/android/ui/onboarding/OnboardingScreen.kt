package com.githunt.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.ui.components.CookieShape
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
import com.githunt.android.ui.theme.GhMotion
import java.util.Locale

/**
 * First-run setup, shown once right after sign-in, as four pages with a dot indicator:
 * greeting, favourite languages, repos to react to, all set.
 *
 * [onSaved] fires once the picks are saved (the last page is showing), so the caller can remember
 * the setup is done even if the app is closed before the last button is tapped. [onFinished] fires
 * when the person taps through the last page, or skips.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    userName: String,
    onSaved: () -> Unit = {},
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val step = state.step

    BackHandler(
        enabled = (step == OnboardingStep.Languages || step == OnboardingStep.Repos) && !state.saving,
    ) { viewModel.back() }

    LaunchedEffect(step) {
        if (step == OnboardingStep.Done) onSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(size = 26.sp)
            if (step != OnboardingStep.Done) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant.copy(alpha = if (state.saving) 0.5f else 1f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !state.saving) { viewModel.skip(onFinished) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(GhMotion.defaultSpatial()) { full -> full * dir / 3 } +
                    fadeIn(GhMotion.defaultEffects())) togetherWith
                    (slideOutHorizontally(GhMotion.defaultSpatial()) { full -> -full * dir / 3 } +
                        fadeOut(GhMotion.defaultEffects()))
            },
            label = "onboarding-step",
        ) { page ->
            when (page) {
                OnboardingStep.Welcome -> HeroPage(
                    title = "Hi, $userName!",
                    body = "Welcome to githunt. Two quick questions and your feed will start " +
                        "showing repos you'll actually like.",
                    glyph = {
                        Text(
                            text = "</>",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            color = scheme.onPrimaryContainer,
                        )
                    },
                )
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
                OnboardingStep.Done -> HeroPage(
                    title = "You're all set, $userName!",
                    body = doneBody(state.languages),
                    glyph = {
                        PathIcon(
                            pathData = NavIcons.Check,
                            tint = scheme.onPrimaryContainer,
                            iconSize = 76.dp,
                        )
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(current = step.ordinal, count = OnboardingStep.values().size)
            Spacer(Modifier.height(20.dp))
            when (step) {
                OnboardingStep.Welcome -> PrimaryButton(
                    text = "Get started",
                    onClick = viewModel::start,
                )
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
                        onClick = viewModel::finish,
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
                OnboardingStep.Done -> PrimaryButton(
                    text = "Let's go",
                    onClick = { viewModel.complete(onFinished) },
                )
            }
        }
    }
}

private fun doneBody(languages: Set<String>): String {
    if (languages.isEmpty()) return "Your feed is ready. Happy hunting."
    val shown = languages.take(3)
    val list = when (shown.size) {
        1 -> shown[0]
        2 -> "${shown[0]} and ${shown[1]}"
        else -> "${shown[0]}, ${shown[1]} and ${shown[2]}"
    }
    val more = if (languages.size > 3) " (and more)" else ""
    return "Your feed is tuned to $list$more. Happy hunting."
}

/** Pager dots: the current one stretches into a pill, on a bouncy spring. */
@Composable
private fun StepDots(current: Int, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val dotWidth by animateDpAsState(
                targetValue = if (active) 28.dp else 8.dp,
                animationSpec = GhMotion.fastSpatial(),
                label = "dot-width",
            )
            val dotColor by animateColorAsState(
                targetValue = if (active) scheme.primary else scheme.outlineVariant,
                animationSpec = GhMotion.defaultEffects(),
                label = "dot-color",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(dotWidth.coerceAtLeast(8.dp))
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

/**
 * The big expressive illustration for the first and last pages: a slowly turning cookie, a
 * counter-turning rounded square and a circle, a few doodle marks, and [glyph] in the middle.
 * It pops in on a spring when the page appears.
 */
@Composable
private fun HeroArt(glyph: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = GhMotion.slowSpatial(),
        label = "hero-entrance",
    )
    val spin by rememberInfiniteTransition(label = "hero-spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "hero-spin-angle",
    )
    Box(
        modifier = Modifier
            .size(250.dp)
            .graphicsLayer {
                scaleX = entrance
                scaleY = entrance
                alpha = ((entrance - 0.6f) / 0.4f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { rotationZ = spin }
                .clip(CookieShape)
                .background(scheme.primaryContainer),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 8.dp)
                .size(88.dp)
                .graphicsLayer { rotationZ = 12f - spin / 3f }
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.tertiaryContainer),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 16.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
        )
        Text(
            text = "+",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 18.dp),
        )
        Text(
            text = "\u00D7",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 24.dp),
        )
        glyph()
    }
}

/** The first and last pages: big hero, big centred title, short body. */
@Composable
private fun HeroPage(
    title: String,
    body: String,
    glyph: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroArt(glyph)
        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Title block for the two working pages: a cookie badge with a glyph, title and subtitle. */
@Composable
private fun StepHeader(
    glyph: String,
    mono: Boolean,
    title: String,
    subtitle: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CookieShape)
                .background(scheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = scheme.onTertiaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        StepHeader(
            glyph = "{ }",
            mono = true,
            title = "What do you code in?",
            subtitle = "Pick up to $MAX_ONBOARDING_LANGUAGES.",
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
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
    val corner = animatedPressCorner(interaction, rest = 22.dp, pressed = 10.dp)
    val tick = rememberTick()
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surface,
        animationSpec = GhMotion.defaultEffects(),
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
        Spacer(Modifier.height(8.dp))
        StepHeader(
            glyph = "\u2605",
            mono = false,
            title = "Anything catch your eye?",
            subtitle = "Tap the ones you like.",
        )
        Spacer(Modifier.height(16.dp))
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
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 22.dp, pressed = 12.dp)
    val container by animateColorAsState(
        targetValue = if (picked) scheme.primaryContainer else scheme.surface,
        animationSpec = GhMotion.defaultEffects(),
        label = "tile-container",
    )
    val circleFill by animateColorAsState(
        targetValue = if (picked) scheme.primary else scheme.surface,
        animationSpec = GhMotion.defaultEffects(),
        label = "tile-check",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
