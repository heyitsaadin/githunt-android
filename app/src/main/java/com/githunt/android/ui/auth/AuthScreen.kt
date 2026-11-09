package com.githunt.android.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.BuildConfig
import com.githunt.android.ui.components.AppIcons
import com.githunt.android.ui.components.GhLoadingIndicator
import com.githunt.android.ui.components.GhSegmentedPicker
import com.githunt.android.ui.components.GoogleG
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.Wordmark
import com.githunt.android.ui.components.animatedPressCorner
import com.githunt.android.ui.components.polarShape
import com.githunt.android.ui.components.rememberTick
import com.githunt.android.ui.theme.GhMotion
import kotlin.math.cos
import kotlin.math.sin

// 24 x 24 outline icons, drawn with the app's own PathIcon so no icon library is needed.
private const val MailPath =
    "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z"
private const val LockPath =
    "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM8.9 6c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2H8.9V6zM18 20H6V10h12v10z"

/** A soft, slightly lopsided blob: the one decorative shape on the page. */
private val BlobShape = polarShape { t -> 0.82f + 0.10f * cos(3f * t) + 0.06f * sin(2f * t + 1f) }

/**
 * Login and sign-up on one calm, minimal page that follows the app theme (light, dark or pure
 * black): a drifting soft blob behind the wordmark, a connected Log in / Sign up switch, two or
 * four filled pill fields, one pill button, and Google underneath. No card, no gradient.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val signUp = form.mode == AuthMode.SignUp
    val busy = form.busy || form.googleBusy
    var showPassword by remember { mutableStateOf(false) }
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    val body = MaterialTheme.typography.bodyMedium

    fun submit() {
        focusManager.clearFocus()
        viewModel.submit()
    }

    fun openLegal(page: String) {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        runCatching { uriHandler.openUri("$base/$page") }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visibleState = enter,
                    enter = fadeIn(GhMotion.slowEffects()),
                ) {
                    Hero()
                }

                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(
                    visibleState = enter,
                    enter = fadeIn(GhMotion.slowEffects()) +
                        slideInVertically(GhMotion.slowSpatial()) { it / 6 },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedContent(
                            targetState = signUp,
                            transitionSpec = { fadeIn(GhMotion.defaultEffects()) togetherWith fadeOut(GhMotion.fastEffects()) },
                            label = "authTitle",
                        ) { isSignUp ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isSignUp) "Create your account" else "Welcome back",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onBackground,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isSignUp) {
                                        "Clip repos, share projects, follow people."
                                    } else {
                                        "Log in to pick up where you left off."
                                    },
                                    style = body,
                                    color = scheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        GhSegmentedPicker(
                            items = listOf(AuthMode.LogIn, AuthMode.SignUp),
                            selected = form.mode,
                            onSelect = { viewModel.setMode(it) },
                            label = { if (it == AuthMode.SignUp) "Sign up" else "Log in" },
                        )
                        Spacer(Modifier.height(20.dp))

                        AnimatedVisibility(
                            visible = signUp,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    AuthField(
                                        value = form.firstName,
                                        onValueChange = viewModel::onFirstNameChange,
                                        placeholder = "First name",
                                        modifier = Modifier.weight(1f),
                                        enabled = !busy,
                                        capitalization = KeyboardCapitalization.Words,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    AuthField(
                                        value = form.lastName,
                                        onValueChange = viewModel::onLastNameChange,
                                        placeholder = "Last name",
                                        modifier = Modifier.weight(1f),
                                        enabled = !busy,
                                        capitalization = KeyboardCapitalization.Words,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }

                        AuthField(
                            value = form.email,
                            onValueChange = viewModel::onEmailChange,
                            placeholder = "Email",
                            leadingIcon = MailPath,
                            enabled = !busy,
                            keyboardType = KeyboardType.Email,
                        )
                        Spacer(Modifier.height(10.dp))
                        AuthField(
                            value = form.password,
                            onValueChange = viewModel::onPasswordChange,
                            placeholder = "Password",
                            leadingIcon = LockPath,
                            enabled = !busy,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            onDone = { submit() },
                            obscured = !showPassword,
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { showPassword = !showPassword },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PathIcon(
                                        pathData = if (showPassword) AppIcons.EyeOff else AppIcons.Eye,
                                        tint = scheme.onSurfaceVariant,
                                        iconSize = 22.dp,
                                        evenOdd = !showPassword,
                                    )
                                }
                            },
                        )

                        form.error?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = body.copy(textAlign = TextAlign.Center),
                                color = scheme.error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        form.notice?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = body.copy(textAlign = TextAlign.Center),
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        PrimaryButton(
                            text = if (signUp) "Create account" else "Log in",
                            onClick = { submit() },
                            enabled = !form.googleBusy,
                            loading = form.busy,
                        )

                        Spacer(Modifier.height(16.dp))
                        OrDivider()
                        Spacer(Modifier.height(16.dp))

                        GoogleButton(
                            enabled = !form.busy,
                            loading = form.googleBusy,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.signInWithGoogle(context)
                            },
                        )

                        Spacer(Modifier.height(20.dp))

                        AnimatedContent(
                            targetState = signUp,
                            transitionSpec = { fadeIn(GhMotion.defaultEffects()) togetherWith fadeOut(GhMotion.fastEffects()) },
                            label = "authLinks",
                        ) { isSignUp ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isSignUp) {
                                    val legal = remember {
                                        buildAnnotatedString {
                                            append("By signing up you agree to the ")
                                            pushStringAnnotation("page", "privacy")
                                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)) {
                                                append("Privacy Policy")
                                            }
                                            pop()
                                            append(" and ")
                                            pushStringAnnotation("page", "terms")
                                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)) {
                                                append("Terms of Service")
                                            }
                                            pop()
                                            append(".")
                                        }
                                    }
                                    ClickableText(
                                        text = legal,
                                        style = body.copy(
                                            fontSize = 13.sp,
                                            color = scheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { offset ->
                                            legal.getStringAnnotations("page", offset, offset).firstOrNull()
                                                ?.let { openLegal(it.item) }
                                        },
                                    )
                                } else {
                                    LinkRow(
                                        prefix = "Forgot it? ",
                                        link = "Request a new password",
                                        enabled = !busy,
                                        onClick = { viewModel.requestPasswordReset() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The wordmark on a slowly turning soft blob, with the tagline under it. */
@Composable
private fun Hero() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "hero")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 48000, easing = LinearEasing)),
        label = "blob-turn",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = turn }
                    .clip(BlobShape)
                    .background(scheme.surfaceVariant),
            )
            Wordmark(size = 42.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Discover  \u2022  Explore  \u2022  Opensource",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrDivider() {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outline))
        Text(
            text = "or",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outline))
    }
}

/** Tonal Google button: a pill that springs squarer under your finger. */
@Composable
private fun GoogleButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(animatedPressCorner(source, rest = 28.dp, pressed = 14.dp))
    val tick = rememberTick()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(scheme.surfaceVariant)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
                onClick = {
                    tick()
                    onClick()
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            GhLoadingIndicator(diameter = 28.dp, color = scheme.onSurface)
        } else {
            GoogleG(Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Continue with Google",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }
    }
}

/** Filled pill field with a hairline that turns solid while focused. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    obscured: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(
        targetValue = if (focused) scheme.primary else scheme.outline,
        animationSpec = GhMotion.defaultEffects(),
        label = "field-outline",
    )
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.onSurface),
        visualTransformation = if (obscured) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(scheme.surface)
                    .border(if (focused) 1.5.dp else 1.dp, outline, shape)
                    .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    PathIcon(pathData = leadingIcon, tint = scheme.onSurfaceVariant, iconSize = 22.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/** Muted words followed by a bold underlined link. */
@Composable
private fun LinkRow(prefix: String, link: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = prefix, style = style, color = scheme.onSurfaceVariant)
        Text(
            text = link,
            style = style.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
            color = scheme.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 6.dp),
        )
    }
}
