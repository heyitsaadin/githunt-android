package com.githunt.android.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.BuildConfig
import com.githunt.android.ui.components.AppIcons
import com.githunt.android.ui.components.GoogleG
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.theme.AuthBackdrop
import com.githunt.android.ui.theme.AuthBlob
import com.githunt.android.ui.theme.AuthBodyLogin
import com.githunt.android.ui.theme.AuthBodySignUp
import com.githunt.android.ui.theme.AuthBorder
import com.githunt.android.ui.theme.AuthError
import com.githunt.android.ui.theme.AuthFieldLogin
import com.githunt.android.ui.theme.AuthFieldSignUp
import com.githunt.android.ui.theme.AuthGold
import com.githunt.android.ui.theme.AuthHeader
import com.githunt.android.ui.theme.AuthInk
import com.githunt.android.ui.theme.AuthLilac
import com.githunt.android.ui.theme.AuthMuted
import com.githunt.android.ui.theme.AuthPlaceholder
import com.githunt.android.ui.theme.AuthShadow

private val StartRounded = RoundedCornerShape(topStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0, bottomStartPercent = 50)
private val EndRounded = RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 0)

/**
 * Login and sign-up share one card: orange header with the title, a white body that overlaps it,
 * pill fields, and a black button. The two modes only differ in their fields and footer links.
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

    LightSystemBars()

    fun submit() {
        focusManager.clearFocus()
        viewModel.submit()
    }

    fun openLegal(page: String) {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        runCatching { uriHandler.openUri("$base/$page") }
    }

    val bodyColor = if (signUp) AuthBodySignUp else AuthBodyLogin
    val fieldColor = if (signUp) AuthFieldSignUp else AuthFieldLogin
    val headerVisible = if (signUp) 160.dp else 130.dp
    val cardShape = RoundedCornerShape(40.dp)
    val bodyShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val body = MaterialTheme.typography.bodyMedium

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackdrop),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(AuthBlob, radius = size.width * 0.75f, center = Offset(size.width * 1.05f, size.height * 0.04f))
            drawCircle(AuthBlob, radius = size.width * 0.6f, center = Offset(-size.width * 0.12f, size.height * 0.72f))
            drawCircle(AuthBlob, radius = size.width * 0.35f, center = Offset(size.width * 0.95f, size.height * 0.98f))
        }

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
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .shadow(18.dp, cardShape, ambientColor = AuthShadow, spotColor = AuthShadow)
                        .clip(cardShape),
                ) {
                    // Header: orange with two soft circles peeking out from behind the body.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerVisible + 44.dp)
                            .background(AuthHeader),
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            val visible = headerVisible.toPx()
                            drawCircle(AuthGold, radius = 54.dp.toPx(), center = Offset(10.dp.toPx(), visible - 28.dp.toPx()))
                            drawCircle(AuthLilac, radius = 52.dp.toPx(), center = Offset(size.width - 30.dp.toPx(), visible + 12.dp.toPx()))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerVisible),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (signUp) "Create an\naccount" else "Login Here",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                                color = Color.White,
                            )
                        }
                    }

                    // Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = headerVisible)
                            .clip(bodyShape)
                            .background(bodyColor)
                            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 26.dp),
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
                        Text(
                            text = "or",
                            style = body.copy(fontSize = 15.sp, textAlign = TextAlign.Center),
                            color = AuthMuted,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))

                        if (signUp) {
                            Row(Modifier.fillMaxWidth()) {
                                PillField(
                                    value = form.firstName,
                                    onValueChange = viewModel::onFirstNameChange,
                                    placeholder = "First Name",
                                    shape = StartRounded,
                                    color = fieldColor,
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                    capitalization = KeyboardCapitalization.Words,
                                )
                                Spacer(Modifier.width(4.dp))
                                PillField(
                                    value = form.lastName,
                                    onValueChange = viewModel::onLastNameChange,
                                    placeholder = "Last Name",
                                    shape = EndRounded,
                                    color = fieldColor,
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                    capitalization = KeyboardCapitalization.Words,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        PillField(
                            value = form.email,
                            onValueChange = viewModel::onEmailChange,
                            placeholder = "Email",
                            shape = CircleShape,
                            color = fieldColor,
                            enabled = !busy,
                            keyboardType = KeyboardType.Email,
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth()) {
                            PillField(
                                value = form.password,
                                onValueChange = viewModel::onPasswordChange,
                                placeholder = "Password",
                                shape = StartRounded,
                                color = fieldColor,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                                onDone = { submit() },
                                obscured = !showPassword,
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(48.dp)
                                    .clip(EndRounded)
                                    .background(fieldColor)
                                    .clickable { showPassword = !showPassword },
                                contentAlignment = Alignment.Center,
                            ) {
                                PathIcon(
                                    pathData = if (showPassword) AppIcons.EyeOff else AppIcons.Eye,
                                    tint = Color(0xFF3A3A3A),
                                    iconSize = 20.dp,
                                    evenOdd = !showPassword,
                                )
                            }
                        }

                        form.error?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = body.copy(textAlign = TextAlign.Center),
                                color = AuthError,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        form.notice?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = body.copy(textAlign = TextAlign.Center),
                                color = AuthMuted,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(22.dp))
                        BlackButton(
                            text = if (signUp) "Create account" else "Log in",
                            enabled = !form.googleBusy,
                            loading = form.busy,
                            onClick = { submit() },
                        )
                        Spacer(Modifier.height(20.dp))

                        if (signUp) {
                            val legal = remember {
                                buildAnnotatedString {
                                    append("Signing up for a GitHunt account means you agree to the ")
                                    pushStringAnnotation("page", "privacy")
                                    withStyle(SpanStyle(color = AuthInk, textDecoration = TextDecoration.Underline)) {
                                        append("Privacy Policy")
                                    }
                                    pop()
                                    append(" and ")
                                    pushStringAnnotation("page", "terms")
                                    withStyle(SpanStyle(color = AuthInk, textDecoration = TextDecoration.Underline)) {
                                        append("Terms of Service")
                                    }
                                    pop()
                                    append(".")
                                }
                            }
                            ClickableText(
                                text = legal,
                                style = body.copy(fontSize = 14.sp, color = AuthMuted, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { offset ->
                                    legal.getStringAnnotations("page", offset, offset).firstOrNull()?.let { openLegal(it.item) }
                                },
                            )
                            Spacer(Modifier.height(18.dp))
                            LinkRow(
                                prefix = "Have an account?",
                                link = "Log in here",
                                prefixColor = AuthInk,
                                enabled = !busy,
                                onClick = { viewModel.setMode(AuthMode.LogIn) },
                            )
                        } else {
                            LinkRow(
                                prefix = "Request a",
                                link = "New Password",
                                prefixColor = AuthMuted,
                                enabled = !busy,
                                onClick = { viewModel.requestPasswordReset() },
                            )
                            Spacer(Modifier.height(18.dp))
                            LinkRow(
                                prefix = "New here?",
                                link = "Create an account",
                                prefixColor = AuthInk,
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

@Composable
private fun GoogleButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, AuthBorder, CircleShape)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AuthInk)
        } else {
            GoogleG(Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AuthInk,
            )
        }
    }
}

@Composable
private fun BlackButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (enabled) AuthInk else AuthInk.copy(alpha = 0.45f))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    obscured: Boolean = false,
) {
    val base = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = base.copy(color = AuthInk),
        cursorBrush = SolidColor(AuthInk),
        visualTransformation = if (obscured) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = base, color = AuthPlaceholder, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** "Muted words" followed by an underlined, tappable link, centred. */
@Composable
private fun LinkRow(
    prefix: String,
    link: String,
    prefixColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prefix, style = style, color = prefixColor)
        Spacer(Modifier.width(4.dp))
        Text(
            text = link,
            style = style.copy(textDecoration = TextDecoration.Underline),
            color = AuthInk,
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        )
    }
}

/** The card is light in both themes, so keep the status and navigation bar icons dark while it shows. */
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
