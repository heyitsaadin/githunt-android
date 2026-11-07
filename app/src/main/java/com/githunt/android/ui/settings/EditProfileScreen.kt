package com.githunt.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.githunt.android.data.auth.User
import com.githunt.android.data.profile.ProfileImages
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.PathIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CameraPath =
    "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9z" +
        "m3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z" +
        "M12 8.8a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 1 0 0-6.4z"

private val CoverHeight = 150.dp
private val AvatarSize = 104.dp
private val AvatarRing = 4.dp
private val UsernameRule = Regex("^[a-z0-9_]{3,20}$")

/**
 * Edit profile, inside the app.
 *
 * A plain top-to-bottom form with one left edge: a top bar (Close, title, Save), the cover and
 * avatar, then labelled Name, Username and Bio fields and the GitHub connection. Everything goes
 * through the same `PATCH /api/auth/me` call the website uses. Connecting GitHub is the one thing
 * that stays on the website, because it needs GitHub's own sign-in page.
 */
@Composable
fun EditProfileScreen(
    user: User,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onRefreshUser: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val openSite = rememberSiteOpener()
    val openWeb = rememberWebOpener()

    // Seed the draft before the first frame is drawn, so the fields never flash empty.
    remember(user.id) { vm.beginEditProfile(user) }
    val state by vm.edit.collectAsStateWithLifecycle()

    var confirmDiscard by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf<PhotoTarget?>(null) }
    var awaitingGithub by rememberSaveable { mutableStateOf(false) }

    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnUserUpdated by rememberUpdatedState(onUserUpdated)
    val latestOnRefreshUser by rememberUpdatedState(onRefreshUser)

    LaunchedEffect(Unit) {
        vm.profileEvents.collect { event ->
            when (event) {
                is ProfileEvent.Saved -> {
                    latestOnUserUpdated(event.user)
                    latestOnBack()
                }
                is ProfileEvent.GithubDisconnected -> latestOnUserUpdated(event.user)
            }
        }
    }

    // After "Connect GitHub" sends the person to the website, re-read the account when they come
    // back so the connected account shows up here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingGithub) {
                awaitingGithub = false
                latestOnRefreshUser()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val resolver = remember(context) { context.applicationContext.contentResolver }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickEditPhoto(PhotoTarget.Avatar, uri, resolver)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickEditPhoto(PhotoTarget.Cover, uri, resolver)
    }

    fun choosePhoto(target: PhotoTarget) {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (target == PhotoTarget.Avatar) avatarPicker.launch(request) else coverPicker.launch(request)
    }

    val savedAvatar = rememberImageModel(user.avatarUrl)
    val savedCover = rememberImageModel(user.backgroundUrl)
    val avatarImage: Any? = when (val a = state.avatar) {
        is PhotoEdit.Picked -> a.jpeg
        PhotoEdit.Removed -> null
        PhotoEdit.Unchanged -> savedAvatar
    }
    val coverImage: Any? = when (val c = state.cover) {
        is PhotoEdit.Picked -> c.jpeg
        PhotoEdit.Removed -> null
        PhotoEdit.Unchanged -> savedCover
    }

    fun photoTapped(target: PhotoTarget) {
        val has = if (target == PhotoTarget.Avatar) avatarImage != null else coverImage != null
        if (has) photoSheet = target else choosePhoto(target)
    }

    val hasChanges = state.displayName.trim() != (user.displayName ?: "").trim() ||
        state.username != user.username ||
        state.bio.trim() != (user.bio ?: "").trim() ||
        state.avatar !is PhotoEdit.Unchanged ||
        state.cover !is PhotoEdit.Unchanged
    val busy = state.saving || state.disconnecting
    val photoBusy = state.avatarBusy || state.coverBusy
    val usernameValid = UsernameRule.matches(state.username)
    val canSave = hasChanges && usernameValid && !busy && !photoBusy

    fun requestClose() {
        if (state.saving) return
        if (hasChanges) confirmDiscard = true else onBack()
    }

    BackHandler { requestClose() }

    val fieldShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding(),
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.background)
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !state.saving) { requestClose() }
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) {
                PathIcon(pathData = NavIcons.Close, tint = scheme.onBackground, iconSize = 22.dp)
            }
            Text(
                text = "Edit profile",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(scheme.onBackground.copy(alpha = if (canSave || state.saving) 1f else 0.22f))
                    .clickable(enabled = canSave) { vm.saveProfile() }
                    .padding(horizontal = 22.dp)
                    .semantics { contentDescription = "Save profile" },
                contentAlignment = Alignment.Center,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = scheme.background,
                    )
                } else {
                    Text(
                        text = "Save",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.background,
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.5f))

        // ---- Scrolling form ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Cover with the avatar overlapping its bottom-left corner.
            Box(modifier = Modifier.fillMaxWidth().height(CoverHeight + AvatarSize / 2 + AvatarRing)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CoverHeight)
                        .background(Brush.linearGradient(listOf(scheme.outline, scheme.surface)))
                        .clickable(enabled = !busy && !state.coverBusy) { photoTapped(PhotoTarget.Cover) }
                        .semantics { contentDescription = "Change cover photo" },
                ) {
                    if (coverImage != null) {
                        AsyncImage(
                            model = coverImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    PhotoChip(
                        text = if (coverImage != null) "Change cover" else "Add cover photo",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                    if (state.coverBusy) BusyOverlay()
                }

                // The outer box is not clipped, so the camera badge can sit on the avatar's edge
                // without having its corner cut off by the circle.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp)
                        .size(AvatarSize + AvatarRing * 2),
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(scheme.background)
                        .clickable(enabled = !busy && !state.avatarBusy) { photoTapped(PhotoTarget.Avatar) }
                        .semantics { contentDescription = "Change profile photo" }
                        .padding(AvatarRing),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarImage != null) {
                            AsyncImage(
                                model = avatarImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            PathIcon(pathData = NavIcons.Person, tint = scheme.onSurfaceVariant, iconSize = 44.dp)
                        }
                        if (state.avatarBusy) BusyOverlay()
                    }
                }
                CameraBadge(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enabled = !busy && !state.avatarBusy,
                    onClick = { photoTapped(PhotoTarget.Avatar) },
                )
                }

                Text(
                    text = "Tap your photos to change them",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp + AvatarSize + AvatarRing * 2 + 14.dp, end = 16.dp, bottom = 14.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(22.dp),
            ) {
                FormField(label = "Name") {
                    TextBox(
                        value = state.displayName,
                        onValueChange = vm::setEditName,
                        placeholder = "Your name",
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }

                FormField(
                    label = "Username",
                    helper = if (state.username.isEmpty() || !usernameValid) {
                        "3 to 20 characters: lowercase letters, numbers and underscores."
                    } else {
                        "People can find you at @${state.username}."
                    },
                    helperIsError = !usernameValid,
                ) {
                    TextBox(
                        value = state.username,
                        onValueChange = vm::setEditUsername,
                        placeholder = "username",
                        enabled = !busy,
                        prefix = "@",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }

                FormField(label = "Bio") {
                    TextBox(
                        value = state.bio,
                        onValueChange = vm::setEditBio,
                        placeholder = "Say something about yourself",
                        enabled = !busy,
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        counter = "${state.bio.length}/160",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }

                FormField(label = "GitHub") {
                    val github = user.githubUsername
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(scheme.surface.copy(alpha = 0.6f))
                            .border(1.dp, scheme.outline, fieldShape)
                            .clickable {
                                if (github != null) {
                                    openWeb("https://github.com/$github")
                                } else {
                                    awaitingGithub = true
                                    openSite("profile/edit")
                                }
                            }
                            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PathIcon(pathData = NavIcons.Code, tint = scheme.onBackground, iconSize = 24.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (github != null) "@$github" else "Connect GitHub",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (github != null) {
                                    "Connected"
                                } else {
                                    "Opens the GitHunt website to finish connecting"
                                },
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (github != null) {
                            Text(
                                text = if (state.disconnecting) "Disconnecting\u2026" else "Disconnect",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !busy) { vm.disconnectGithubFromProfile() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.error,
                            )
                        } else {
                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                PathIcon(
                                    pathData = NavIcons.ChevronRight,
                                    tint = scheme.onSurfaceVariant,
                                    iconSize = 20.dp,
                                )
                            }
                        }
                    }
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp,
                        color = scheme.error,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }

    photoSheet?.let { target ->
        PhotoSheet(
            title = if (target == PhotoTarget.Avatar) "Profile photo" else "Cover photo",
            onChoose = { choosePhoto(target) },
            onRemove = { vm.removeEditPhoto(target) },
            onDismiss = { photoSheet = null },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to your profile won't be saved.",
            confirmLabel = "Discard",
            cancelLabel = "Keep editing",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** A small label above a field, with optional helper text below it. All fields share one left edge. */
@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    helperIsError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        content()
        if (helper != null) {
            Text(
                text = helper,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (helperIsError) scheme.error else scheme.onSurfaceVariant,
            )
        }
    }
}

/** The one text box style used by every field: rounded, bordered, with a darker border while focused. */
@Composable
private fun TextBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    counter: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = scheme.onBackground,
        textAlign = TextAlign.Start,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.6f))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) scheme.onBackground.copy(alpha = 0.7f) else scheme.outline,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = textStyle.copy(color = scheme.onSurfaceVariant),
                )
                Spacer(Modifier.width(2.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = textStyle,
                cursorBrush = SolidColor(scheme.onBackground),
                keyboardOptions = keyboardOptions,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = scheme.onSurfaceVariant),
                            )
                        }
                        inner()
                    }
                },
            )
        }
        if (counter != null) {
            Text(
                text = counter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Small pill on the cover that says what tapping it does. */
@Composable
private fun PhotoChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x99000000))
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathIcon(pathData = CameraPath, tint = Color.White, iconSize = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun CameraBadge(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        PathIcon(pathData = CameraPath, tint = scheme.background, iconSize = 17.dp)
    }
}

@Composable
private fun BusyOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C000000)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = Color.White,
        )
    }
}

/** "Choose new photo" / "Remove photo", shown when the picture being tapped already has one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSheet(
    title: String,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SettingsRow(
            title = "Choose new photo",
            icon = SettingsIcons.Image,
            onClick = { closeThen(onChoose) },
        )
        SettingsRow(
            title = "Remove photo",
            icon = SettingsIcons.Delete,
            danger = true,
            onClick = { closeThen(onRemove) },
        )
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

/**
 * Photos saved through the website are `data:` strings. Decode those off the main thread; ordinary
 * links pass straight through.
 */
@Composable
private fun rememberImageModel(url: String?): Any? {
    val model by produceState<Any?>(
        initialValue = url.takeUnless { it?.startsWith("data:", ignoreCase = true) == true },
        key1 = url,
    ) {
        value = withContext(Dispatchers.IO) { ProfileImages.decodeDataUri(url) ?: url }
    }
    return model
}
