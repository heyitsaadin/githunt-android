package com.githunt.android.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import com.githunt.android.ui.components.GhLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.BuildConfig
import com.githunt.android.R
import com.githunt.android.ui.components.AppIcons
import com.githunt.android.ui.components.GoogleG
import com.githunt.android.ui.components.PathIcon
import kotlin.math.PI
import kotlin.math.sin

private val LavInk = Color(0xFF111111)
private val LavMuted = Color(0xFF6B6A7B)
private val LavError = Color(0xFFD93B4B)

// 24 x 24 outline icons, drawn with the app's own PathIcon so no icon library is needed.
private const val MailPath =
    "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z"
private const val LockPath =
    "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM8.9 6c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2H8.9V6zM18 20H6V10h12v10z"
private const val ArrowPath = "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"

/**
 * Login and sign-up share one card on an animated lavender background. The logo floats over a
 * soft pulsing glow; the card fades and slides in when the screen opens.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val signUp = form.mode == AuthMode.SignUp
    val busy = form.busy || form.googleBusy
    var showPassword by remember { mutableStateOf(false) }
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    val logoWidth by animateDpAsState(if (signUp) 92.dp else 124.dp, tween(400), label = "logoWidth")
    val body = MaterialTheme.typography.bodyMedium

    LightSystemBars()

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
            .background(Brush.verticalGradient(listOf(LavBgTop, LavBgBottom))),
    ) {
        LiveBackground()

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
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visibleState = enter,
                    enter = fadeIn(tween(700)) +
                        scaleIn(tween(800, easing = FastOutSlowInEasing), initialScale = 0.85f),
                ) {
                    Header(logoWidth)
                }

                Spacer(Modifier.height(32.dp))

                AnimatedVisibility(
                    visibleState = enter,
                    enter = fadeIn(tween(700, delayMillis = 150)) +
                        slideInVertically(tween(800, delayMillis = 150, easing = FastOutSlowInEasing)) { it / 5 },
                ) {
                    val cardShape = RoundedCornerShape(36.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .shadow(28.dp, cardShape, ambientColor = LavShadow, spotColor = LavShadow)
                            .clip(cardShape)
                            .background(Color.White.copy(alpha = 0.94f))
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GoogleButton(
                            enabled = !form.busy,
                            loading = form.googleBusy,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.signInWithGoogle(context)
                            },
                        )

                        Spacer(Modifier.height(14.dp))
                        OrDivider()
                        Spacer(Modifier.height(14.dp))

                        AnimatedVisibility(
                            visible = signUp,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    PillField(
                                        value = form.firstName,
                                        onValueChange = viewModel::onFirstNameChange,
                                        placeholder = "First name",
                                        modifier = Modifier.weight(1f),
                                        enabled = !busy,
                                        capitalization = KeyboardCapitalization.Words,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    PillField(
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

                        PillField(
                            value = form.email,
                            onValueChange = viewModel::onEmailChange,
                            placeholder = "Email",
                            leadingIcon = MailPath,
                            enabled = !busy,
                            keyboardType = KeyboardType.Email,
                        )
                        Spacer(Modifier.height(10.dp))
                        PillField(
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { showPassword = !showPassword },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PathIcon(
                                        pathData = if (showPassword) AppIcons.EyeOff else AppIcons.Eye,
                                        tint = LavMuted,
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
                                color = LavError,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        form.notice?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = body.copy(textAlign = TextAlign.Center),
                                color = LavMuted,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(
                            label = if (signUp) "Create account" else "Log in",
                            enabled = !form.googleBusy,
                            loading = form.busy,
                            onClick = { submit() },
                        )
                        Spacer(Modifier.height(18.dp))

                        AnimatedContent(
                            targetState = signUp,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                            label = "authLinks",
                        ) { isSignUp ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isSignUp) {
                                    val legal = remember {
                                        buildAnnotatedString {
                                            append("Signing up for a GitHunt account means you agree to the ")
                                            pushStringAnnotation("page", "privacy")
                                            withStyle(SpanStyle(color = LavInk, textDecoration = TextDecoration.Underline)) {
                                                append("Privacy Policy")
                                            }
                                            pop()
                                            append(" and ")
                                            pushStringAnnotation("page", "terms")
                                            withStyle(SpanStyle(color = LavInk, textDecoration = TextDecoration.Underline)) {
                                                append("Terms of Service")
                                            }
                                            pop()
                                            append(".")
                                        }
                                    }
                                    ClickableText(
                                        text = legal,
                                        style = body.copy(fontSize = 14.sp, color = LavMuted, textAlign = TextAlign.Center),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { offset ->
                                            legal.getStringAnnotations("page", offset, offset).firstOrNull()
                                                ?.let { openLegal(it.item) }
                                        },
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    LinkRow(
                                        prefix = "Have an account? ",
                                        link = "Log in here",
                                        enabled = !busy,
                                        onClick = { viewModel.setMode(AuthMode.LogIn) },
                                    )
                                } else {
                                    LinkRow(
                                        prefix = "Request a ",
                                        link = "New Password",
                                        enabled = !busy,
                                        onClick = { viewModel.requestPasswordReset() },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    LinkRow(
                                        prefix = "New here? ",
                                        link = "Create an account",
                                        enabled = !busy,
                                        onClick = { viewModel.setMode(AuthMode.SignUp) },
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

/** Logo floating over a pulsing glow, then the wordmark and tagline. */
@Composable
private fun Header(logoWidth: Dp) {
    val transition = rememberInfiniteTransition(label = "logo")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "bob",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(logoWidth * 1.55f)
                    .graphicsLayer {
                        val s = 0.92f + 0.10f * (0.5f + 0.5f * sin(bob.value * 2f * PI.toFloat()))
                        scaleX = s
                        scaleY = s
                        alpha = 0.55f
                    }
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFD9D7F0), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.githunt_logo),
                contentDescription = "Githunt logo",
                modifier = Modifier
                    .width(logoWidth)
                    .aspectRatio(420f / 396f)
                    .graphicsLayer { translationY = sin(bob.value * 2f * PI.toFloat()) * 5.dp.toPx() },
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Githunt",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 54.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
            ),
            color = LavInk,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Discover  •  Explore  •  Opensource",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = LavMuted,
        )
    }
}

@Composable
private fun GoogleButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "googleScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, LavHairline, CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LavInk)
        } else {
            GoogleG(Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                color = LavInk,
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(LavHairline))
        Text(
            text = "or",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = LavMuted,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(LavHairline))
    }
}

@Composable
private fun PillField(
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
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = LavInk),
        cursorBrush = SolidColor(LavInk),
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
                    .clip(CircleShape)
                    .background(LavField)
                    .padding(start = 18.dp, end = if (trailing != null) 12.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    PathIcon(pathData = leadingIcon, tint = LavMuted, iconSize = 22.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = LavMuted.copy(alpha = 0.85f),
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

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "buttonScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (enabled) LavInk else LavInk.copy(alpha = 0.45f))
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else {
            AnimatedContent(targetState = label, label = "buttonLabel") { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            PathIcon(pathData = ArrowPath, tint = Color.White, iconSize = 22.dp)
        }
    }
}

/** Muted words followed by an underlined, tappable link. */
@Composable
private fun LinkRow(prefix: String, link: String, enabled: Boolean, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = prefix, style = style, color = LavMuted)
        Text(
            text = link,
            style = style.copy(fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
            color = LavInk,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 2.dp),
        )
    }
}

/** The screen is light in both themes, so keep the status and navigation bar icons dark while it shows. */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(darkTheme) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
