package com.githunt.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Edit profile, inside the app. It mirrors the website's page: the cover and avatar sit exactly
 * where they do on the profile, and name, username and bio are edited in place.
 *
 * Everything goes through the same `PATCH /api/auth/me` call the website uses. Connecting GitHub
 * is the one thing that stays on the website, because it needs GitHub's own sign-in page.
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

    // After "Connect with GitHub" sends the person to the website, re-read the account when they
    // come back so the connected account shows up here.
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

    fun requestClose() {
        if (state.saving) return
        if (hasChanges) confirmDiscard = true else onBack()
    }

    BackHandler { requestClose() }

    // Same geometry as the profile page, so the editor lines up with what you'll see afterwards.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val coverHeight = 210.dp + statusTop
    val sheetTop = coverHeight - 24.dp
    val avatarTop = sheetTop - 84.dp
    val fieldShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Cover: the whole thing is the tap target.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight)
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
                } else {
                    Text(
                        text = "Tap to add a cover photo",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusTop + 72.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x73000000), Color.Transparent))),
                )
                if (state.coverBusy) BusyOverlay()
                CameraBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 36.dp),
                )
            }

            // Sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sheetTop)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(scheme.background),
            ) {
                Spacer(Modifier.height(72.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    InlineField(
                        value = state.displayName,
                        onValueChange = vm::setEditName,
                        placeholder = user.username,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onBackground,
                            textAlign = TextAlign.Center,
                        ),
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "@", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                        InlineField(
                            value = state.username,
                            onValueChange = vm::setEditUsername,
                            placeholder = "username",
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = scheme.onBackground,
                                textAlign = TextAlign.Start,
                            ),
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.width((maxOf(state.username.length, 8) * 9).dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${user.followerCount} ${if (user.followerCount == 1) "follower" else "followers"}" +
                            "  \u00B7  ${user.followingCount} following",
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        fontSize = 13.sp,
                        color = scheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                // About
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp),
                ) {
                    Text(
                        text = "About",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                    )
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(scheme.surface.copy(alpha = 0.6f))
                            .border(1.dp, scheme.outline, fieldShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        BasicTextField(
                            value = state.bio,
                            onValueChange = vm::setEditBio,
                            enabled = !busy,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = scheme.onBackground,
                            ),
                            cursorBrush = SolidColor(scheme.onBackground),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (state.bio.isEmpty()) {
                                        Text(
                                            text = "Say something about yourself",
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        Text(
                            text = "${state.bio.length}/160",
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }

                    val github = user.githubUsername
                    if (github != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(fieldShape)
                                    .clickable { openWeb("https://github.com/$github") }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PathIcon(pathData = NavIcons.Code, tint = scheme.onBackground, iconSize = 24.dp)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "@$github",
                                    modifier = Modifier.weight(1f, fill = false),
                                    fontSize = 16.sp,
                                    color = scheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                PathIcon(
                                    pathData = NavIcons.ChevronRight,
                                    tint = scheme.onSurfaceVariant,
                                    iconSize = 20.dp,
                                )
                            }
                            Text(
                                text = if (state.disconnecting) "Disconnecting\u2026" else "Disconnect",
                                modifier = Modifier
                                    .clip(fieldShape)
                                    .clickable(enabled = !busy) { vm.disconnectGithubFromProfile() }
                                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(fieldShape)
                                    .clickable {
                                        awaitingGithub = true
                                        openSite("profile/edit")
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PathIcon(pathData = NavIcons.Code, tint = scheme.onBackground, iconSize = 24.dp)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "Connect with GitHub",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onBackground,
                                )
                                PathIcon(
                                    pathData = NavIcons.ChevronRight,
                                    tint = scheme.onSurfaceVariant,
                                    iconSize = 20.dp,
                                )
                            }
                            Text(
                                text = "Connect your GitHub account so people can find your work. " +
                                    "This opens the GitHunt website to finish connecting.",
                                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(32.dp))
            }

            // Avatar, straddling the seam between cover and sheet.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = avatarTop)
                    .size(136.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !busy && !state.avatarBusy) { photoTapped(PhotoTarget.Avatar) }
                    .semantics { contentDescription = "Change profile photo" },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.background)
                        .padding(4.dp)
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
                        PathIcon(pathData = NavIcons.Person, tint = scheme.onSurfaceVariant, iconSize = 56.dp)
                    }
                    if (state.avatarBusy) BusyOverlay()
                }
                CameraBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp),
                    small = true,
                )
            }

            // Close and Done float on the cover.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable(enabled = !state.saving) { requestClose() }
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    PathIcon(pathData = NavIcons.Close, tint = Color.White, iconSize = 20.dp)
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !busy && !photoBusy) {
                            if (hasChanges) vm.saveProfile() else onBack()
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black,
                        )
                    } else {
                        Text(
                            text = "Done",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                    }
                }
            }
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

/** A single-line text field that looks like plain text with a dashed underline, as on the website. */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(scheme.onBackground),
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .drawBehind {
                val y = size.height
                drawLine(
                    color = line,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
            .padding(bottom = 4.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = scheme.onSurfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun CameraBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(if (small) 36.dp else 36.dp)
            .clip(CircleShape)
            .background(if (small) scheme.surface else Color.White.copy(alpha = 0.9f))
            .then(if (small) Modifier.border(2.dp, scheme.background, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PathIcon(
            pathData = CameraPath,
            tint = if (small) scheme.onSurface else Color.Black,
            iconSize = 18.dp,
        )
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
