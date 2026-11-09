package com.githunt.android.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.githunt.android.BuildConfig
import com.githunt.android.ui.components.AppIcons
import com.githunt.android.ui.components.CookieShape
import com.githunt.android.ui.components.GhLoadingIndicator
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.animatedPressCorner
import com.githunt.android.ui.components.rememberTick
import com.githunt.android.ui.theme.GhMotion
import java.time.Duration
import java.time.Instant

/** Material icon paths (24 x 24) used only by the Settings screens. */
object SettingsIcons {
    const val ArrowBack = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
    const val Lock = "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"
    const val Shield = "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"
    const val Delete = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
    const val Translate = "M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z"
    const val Info = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"
    const val Notifications = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
    const val Brightness = "M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18V6c3.31 0 6 2.69 6 6s-2.69 6-6 6z"
    const val Tune = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
    const val Mail = "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"
    const val Phone = "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"
    const val OpenInNew = "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"
    const val Refresh = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
    const val Send = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"
    const val Book = "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z"
    const val File = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"
    const val Folder = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"
    const val ExpandMore = "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z"
    const val Add = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    const val Heart = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    const val Image = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"
    const val LinkedIn = "M20.45 20.45h-3.56v-5.57c0-1.33-.02-3.03-1.85-3.03-1.85 0-2.14 1.44-2.14 2.94v5.66H9.34V9h3.42v1.56h.05c.48-.9 1.64-1.85 3.38-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.07 2.07 0 1 1 0-4.13 2.07 2.07 0 0 1 0 4.13zM7.12 20.45H3.56V9h3.56v11.45z"
    const val XMark = "M18.9 2H22l-7.6 8.7L23.3 22h-7l-5.5-7.2L4.5 22H1.4l8.1-9.3L1 2h7.2l5 6.6L18.9 2zm-1.2 18h1.7L6.4 3.9H4.6L17.7 20z"
}

val SuccessGreen = Color(0xFF22C55E)

// ---- Opening links ----

@Composable
fun rememberWebOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return remember(handler) {
        val opener: (String) -> Unit = { url -> runCatching { handler.openUri(url) } }
        opener
    }
}

/** Opens a page of the GitHunt website by its path, for example "privacy". */
@Composable
fun rememberSiteOpener(): (String) -> Unit {
    val open = rememberWebOpener()
    val base = remember { BuildConfig.API_BASE_URL.trimEnd('/') }
    return remember(open, base) {
        val opener: (String) -> Unit = { path -> open("$base/$path") }
        opener
    }
}

// ---- Page shell ----

/**
 * Expressive page: a large top app bar that collapses as you scroll, with a tonal back button,
 * then a scrolling column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .semantics { contentDescription = "Back" },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = scheme.surfaceVariant,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        PathIcon(pathData = SettingsIcons.ArrowBack, tint = scheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = scheme.background,
                    scrolledContainerColor = scheme.background,
                    titleContentColor = scheme.onBackground,
                    navigationIconContentColor = scheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }
}

/** A titled block: a heading and description, then whatever rows, fields or buttons belong to it. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                start = 28.dp,
                end = 28.dp,
                top = if (first) 4.dp else 24.dp,
                bottom = 8.dp,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

private val LocalInSettingsGroup = compositionLocalOf { false }

/**
 * A run of [SettingsRow]s drawn as one segmented list: the group has big rounded outer corners,
 * every row inside is its own tile with only a small gap and small corners between them, and a
 * row that is pressed springs rounder. Rows used outside a group are single rounded tiles.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalInSettingsGroup provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(28.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * One list row: tonal icon chip, title, optional supporting text, optional trailing control.
 * [selected] fills the tile with the container colour (used for choice lists).
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    leading: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val inGroup = LocalInSettingsGroup.current
    val tint = if (danger) scheme.error else scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(
        interactionSource = interaction,
        rest = if (inGroup) 6.dp else 24.dp,
        pressed = if (inGroup) 24.dp else 32.dp,
    )
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surface,
        animationSpec = GhMotion.defaultEffects(),
        label = "row-container",
    )
    ListItem(
        headlineContent = { Text(text = title, color = tint, fontWeight = FontWeight.Medium) },
        modifier = modifier
            .then(if (inGroup) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            .clip(RoundedCornerShape(corner))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.5f),
        supportingContent = if (subtitle != null) {
            { Text(text = subtitle) }
        } else {
            null
        },
        leadingContent = if (leading != null || icon != null) {
            {
                if (leading != null) {
                    leading()
                } else if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (danger) scheme.error.copy(alpha = 0.14f) else scheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        PathIcon(pathData = icon, tint = tint, iconSize = 22.dp)
                    }
                }
            }
        } else {
            null
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = container,
            supportingColor = scheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun ChevronTrailing() {
    PathIcon(
        pathData = NavIcons.ChevronRight,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconSize = 22.dp,
    )
}

@Composable
fun OpenTrailing() {
    PathIcon(
        pathData = SettingsIcons.OpenInNew,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconSize = 20.dp,
    )
}

/** Profile picture in a scalloped "cookie" shape. */
@Composable
fun AvatarCircle(url: String?, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CookieShape)
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PathIcon(pathData = NavIcons.Person, tint = scheme.onSurfaceVariant, iconSize = size / 2)
        }
    }
}

// ---- Text blocks ----

@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) scheme.error else scheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    )
}

@Composable
fun SettingsBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
    )
}

/** Rounded message tile with an icon, used for "Password updated" and similar. */
@Composable
fun SettingsCallout(
    iconPath: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onBackground,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(scheme.surface)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PathIcon(pathData = iconPath, tint = iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Inputs and buttons ----

@Composable
fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    password: Boolean = false,
    enabled: Boolean = true,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(if (maxLength != null) typed.take(maxLength) else typed) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        enabled = enabled,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        supportingText = if (hint != null) {
            { Text(hint) }
        } else {
            null
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    PathIcon(
                        pathData = if (visible) AppIcons.EyeOff else AppIcons.Eye,
                        tint = scheme.onSurfaceVariant,
                        iconSize = 22.dp,
                    )
                }
            }
        } else {
            null
        },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        singleLine = singleLine,
        minLines = minLines,
        textStyle = if (mono) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        shape = RoundedCornerShape(18.dp),
    )
}

enum class ButtonKind { Primary, Outlined, Danger }

/** Pill button that springs into a squarer shape while pressed. */
@Composable
fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    loading: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 26.dp, pressed = 14.dp)
    val shape = RoundedCornerShape(corner)
    val sized = modifier.height(52.dp)
    val inner: @Composable () -> Unit = {
        if (loading) {
            GhLoadingIndicator(diameter = 26.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(10.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    when (kind) {
        ButtonKind.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled && !loading,
            shape = shape,
            interactionSource = interaction,
            content = { inner() },
        )
        ButtonKind.Danger -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled && !loading,
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            content = { inner() },
        )
        ButtonKind.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled && !loading,
            shape = shape,
            interactionSource = interaction,
            content = { inner() },
        )
    }
}

/** Buttons stacked full width, the way the website lays out its form actions. */
@Composable
fun SettingsActions(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = scheme.surfaceContainerHigh,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text = text, color = scheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) scheme.error else scheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel, fontWeight = FontWeight.SemiBold) }
        },
    )
}

// ---- Helpers ----

/** "Active now", "Active 5m ago" ... from an ISO timestamp. Empty when it can't be read. */
fun activeAgo(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val mins = Duration.between(then, Instant.now()).toMinutes()
    return when {
        mins < 2 -> "Active now"
        mins < 60 -> "Active ${mins}m ago"
        mins < 60 * 24 -> "Active ${mins / 60}h ago"
        mins < 60 * 24 * 30 -> "Active ${mins / (60 * 24)}d ago"
        else -> "Last active long ago"
    }
}
