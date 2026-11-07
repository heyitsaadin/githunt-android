package com.githunt.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.githunt.android.data.auth.User
import com.githunt.android.data.profile.ProfileImages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.push.DevicePush
import com.githunt.android.data.settings.AppPreferences
import com.githunt.android.data.settings.BuildLog
import com.githunt.android.data.settings.DeviceSession
import com.githunt.android.data.settings.GithubTokenStatus
import com.githunt.android.data.settings.Interests
import com.githunt.android.data.settings.InboxNotification
import com.githunt.android.data.settings.SettingsRepository
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityState(
    val sessions: List<DeviceSession>? = null,
    val error: String? = null,
    val revokingId: String? = null,
)

data class PasswordState(
    val saving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

data class DeleteState(
    val deleting: Boolean = false,
    val error: String? = null,
)

data class PersonalizationUi(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val cleared: Boolean = false,
)

data class NotificationsUi(
    val loading: Boolean = true,
    val hotAlerts: Boolean = true,
    val dailyDigest: Boolean = true,
    val interests: Interests = Interests(),
    val error: String? = null,
    val interestError: String? = null,
    val testing: Boolean = false,
    val testSent: Boolean = false,
    /** Turning notifications on or off for this phone is in progress. */
    val deviceBusy: Boolean = false,
    val deviceError: String? = null,
    /** The Android permission was refused, so only the phone's own settings can allow it now. */
    val deviceBlocked: Boolean = false,
)

enum class InterestKind { Language, Topic }

enum class TokenPhase { Idle, Checking, Success, Error }

data class GithubUi(
    val loading: Boolean = true,
    /** Null while disconnected. */
    val status: GithubTokenStatus? = null,
    val phase: TokenPhase = TokenPhase.Idle,
    val error: String? = null,
    val removing: Boolean = false,
)

data class NotificationInboxUi(
    val loading: Boolean = true,
    val items: List<InboxNotification> = emptyList(),
    val error: String? = null,
)

data class LogsUi(
    val logs: List<BuildLog>? = null,
    val error: String? = null,
)

data class FeatureUi(
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
)

/** What the editor will do with a photo when saved. */
sealed interface PhotoEdit {
    data object Unchanged : PhotoEdit
    data object Removed : PhotoEdit

    /** [jpeg] is already resized; it is what gets uploaded and what the editor previews. */
    class Picked(val jpeg: ByteArray) : PhotoEdit
}

enum class PhotoTarget { Avatar, Cover }

/** Where the live "is this username free?" check stands for the name typed in the editor. */
enum class UsernameCheck {
    /** Nothing to check: unchanged, or not a valid username yet. */
    Idle,
    Checking,
    Available,
    Taken,

    /** The check couldn't run (offline, or the server doesn't offer it). Saving still verifies. */
    Unknown,
}

/** The edit-profile draft. Lives in the view model so a half-finished edit survives the screen redrawing. */
data class EditProfileUi(
    val usernameCheck: UsernameCheck = UsernameCheck.Idle,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: PhotoEdit = PhotoEdit.Unchanged,
    val cover: PhotoEdit = PhotoEdit.Unchanged,
    val avatarBusy: Boolean = false,
    val coverBusy: Boolean = false,
    val saving: Boolean = false,
    val disconnecting: Boolean = false,
    val error: String? = null,
) {
    fun withBusy(target: PhotoTarget, busy: Boolean): EditProfileUi =
        if (target == PhotoTarget.Avatar) copy(avatarBusy = busy) else copy(coverBusy = busy)

    fun withPhoto(target: PhotoTarget, photo: PhotoEdit): EditProfileUi =
        if (target == PhotoTarget.Avatar) copy(avatar = photo) else copy(cover = photo)
}

/** One-off results the editor screen reacts to. */
sealed interface ProfileEvent {
    data class Saved(val user: User) : ProfileEvent
    data class GithubDisconnected(val user: User) : ProfileEvent
}

private fun JsonObjectBuilder.putPhoto(key: String, photo: PhotoEdit) {
    when (photo) {
        PhotoEdit.Unchanged -> Unit
        // The server treats an empty string as "remove".
        PhotoEdit.Removed -> put(key, "")
        is PhotoEdit.Picked -> put(key, ProfileImages.toDataUrl(photo.jpeg))
    }
}

const val MAX_INTERESTS = 8

/** One view model for every Settings screen; each screen only watches the slice it needs. */
class SettingsViewModel(
    private val repo: SettingsRepository,
    val prefs: AppPreferences,
    private val devicePush: DevicePush,
) : ViewModel() {

    // ---- Security ----

    private val _security = MutableStateFlow(SecurityState())
    val security: StateFlow<SecurityState> = _security.asStateFlow()

    fun loadSessions() {
        viewModelScope.launch {
            when (val result = repo.sessions()) {
                is ApiResult.Ok -> _security.update { it.copy(sessions = result.value.sessions, error = null) }
                is ApiResult.Err -> _security.update {
                    it.copy(sessions = it.sessions ?: emptyList(), error = result.message)
                }
            }
        }
    }

    fun revokeSession(target: DeviceSession, onSignedOut: () -> Unit, onDone: () -> Unit) {
        if (_security.value.revokingId != null) return
        _security.update { it.copy(revokingId = target.id, error = null) }
        viewModelScope.launch {
            when (val result = repo.revokeSession(target.id)) {
                is ApiResult.Ok -> {
                    _security.update { it.copy(revokingId = null) }
                    if (result.value.revokedCurrent) {
                        onSignedOut()
                        return@launch
                    }
                    loadSessions()
                }
                is ApiResult.Err -> _security.update {
                    it.copy(revokingId = null, error = "Could not log out that device. Try again.")
                }
            }
            onDone()
        }
    }

    fun revokeOtherSessions(onDone: () -> Unit) {
        if (_security.value.revokingId != null) return
        _security.update { it.copy(revokingId = ALL_OTHERS, error = null) }
        viewModelScope.launch {
            when (repo.revokeOthers()) {
                is ApiResult.Ok -> {
                    _security.update { it.copy(revokingId = null) }
                    loadSessions()
                }
                is ApiResult.Err -> _security.update {
                    it.copy(revokingId = null, error = "Could not log out other devices. Try again.")
                }
            }
            onDone()
        }
    }

    // ---- Change password ----

    private val _password = MutableStateFlow(PasswordState())
    val password: StateFlow<PasswordState> = _password.asStateFlow()

    fun resetPassword() {
        _password.value = PasswordState()
    }

    fun changePassword(current: String, newPassword: String, confirm: String) {
        if (_password.value.saving) return
        if (newPassword.length < 8) {
            _password.value = PasswordState(error = "New password must be at least 8 characters.")
            return
        }
        if (newPassword != confirm) {
            _password.value = PasswordState(error = "New passwords don't match.")
            return
        }
        _password.value = PasswordState(saving = true)
        viewModelScope.launch {
            _password.value = when (val result = repo.changePassword(current, newPassword)) {
                is ApiResult.Ok -> PasswordState(success = true)
                is ApiResult.Err -> PasswordState(error = result.message)
            }
        }
    }

    // ---- Delete account ----

    private val _delete = MutableStateFlow(DeleteState())
    val delete: StateFlow<DeleteState> = _delete.asStateFlow()

    fun resetDelete() {
        _delete.value = DeleteState()
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        if (_delete.value.deleting) return
        _delete.value = DeleteState(deleting = true)
        viewModelScope.launch {
            when (val result = repo.deleteAccount()) {
                is ApiResult.Ok -> {
                    _delete.value = DeleteState()
                    onDeleted()
                }
                is ApiResult.Err -> _delete.value = DeleteState(error = result.message)
            }
        }
    }

    // ---- Edit profile ----

    private val _edit = MutableStateFlow(EditProfileUi())
    val edit: StateFlow<EditProfileUi> = _edit.asStateFlow()

    private val _profileEvents = Channel<ProfileEvent>(Channel.BUFFERED)
    val profileEvents: Flow<ProfileEvent> = _profileEvents.receiveAsFlow()

    /** Bumped every time the editor opens, so a photo still being processed can't land in a newer draft. */
    private var editSession = 0

    /** Opens a fresh draft filled in from the signed-in account. */
    fun beginEditProfile(user: User) {
        editSession++
        usernameJob?.cancel()
        originalUsername = user.username
        _edit.value = EditProfileUi(
            displayName = user.displayName.orEmpty(),
            username = user.username,
            bio = user.bio.orEmpty(),
        )
    }

    fun setEditName(value: String) {
        _edit.update { it.copy(displayName = value.take(40), error = null) }
    }

    /** The username on the account when the editor opened; keeping it needs no check. */
    private var originalUsername: String = ""
    private var usernameJob: Job? = null

    fun setEditUsername(value: String) {
        val cleaned = value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(20)
        if (cleaned == _edit.value.username) return

        usernameJob?.cancel()
        val needsCheck = cleaned != originalUsername && USERNAME_PATTERN.matches(cleaned)
        // Mark "checking" straight away so Save can't be tapped in the gap before the request starts.
        _edit.update {
            it.copy(
                username = cleaned,
                error = null,
                usernameCheck = if (needsCheck) UsernameCheck.Checking else UsernameCheck.Idle,
            )
        }
        if (!needsCheck) return

        val session = editSession
        usernameJob = viewModelScope.launch {
            // Wait for a pause in typing so we don't ask the server about every keystroke.
            delay(450)
            val result = repo.usernameAvailable(cleaned)
            if (session != editSession || _edit.value.username != cleaned) return@launch
            val check = when (result) {
                is ApiResult.Ok -> if (result.value.available) UsernameCheck.Available else UsernameCheck.Taken
                is ApiResult.Err -> UsernameCheck.Unknown
            }
            _edit.update { it.copy(usernameCheck = check) }
        }
    }

    fun setEditBio(value: String) {
        _edit.update { it.copy(bio = value.take(160), error = null) }
    }

    fun removeEditPhoto(target: PhotoTarget) {
        _edit.update { it.withPhoto(target, PhotoEdit.Removed).copy(error = null) }
    }

    /** Shrinks the picked picture off the main thread, then puts it in the draft. */
    fun pickEditPhoto(target: PhotoTarget, uri: Uri, resolver: ContentResolver) {
        val session = editSession
        _edit.update { it.withBusy(target, true).copy(error = null) }
        viewModelScope.launch {
            val jpeg = ProfileImages.encode(resolver, uri, square = target == PhotoTarget.Avatar)
            if (session != editSession) return@launch
            _edit.update {
                if (jpeg == null) {
                    it.withBusy(target, false).copy(error = "Couldn't read that image. Try a different one.")
                } else {
                    it.withBusy(target, false).withPhoto(target, PhotoEdit.Picked(jpeg))
                }
            }
        }
    }

    fun saveProfile() {
        val draft = _edit.value
        if (draft.saving || draft.disconnecting || draft.avatarBusy || draft.coverBusy) return
        if (draft.usernameCheck == UsernameCheck.Checking || draft.usernameCheck == UsernameCheck.Taken) return

        val username = draft.username.trim()
        if (!USERNAME_PATTERN.matches(username)) {
            _edit.update {
                it.copy(error = "Username must be 3-20 characters: lowercase letters, numbers, underscores.")
            }
            return
        }

        // Only the photos that changed are sent, so an untouched photo is never re-uploaded.
        val body = buildJsonObject {
            put("display_name", draft.displayName.trim())
            put("username", username)
            put("bio", draft.bio.trim())
            putPhoto("avatar_url", draft.avatar)
            putPhoto("background_url", draft.cover)
        }

        _edit.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.updateProfile(body)) {
                is ApiResult.Ok -> {
                    _edit.update { it.copy(saving = false) }
                    _profileEvents.trySend(ProfileEvent.Saved(result.value))
                }
                is ApiResult.Err -> _edit.update {
                    // Somebody took the name between the check and the save.
                    if (result.code == 409) {
                        it.copy(saving = false, usernameCheck = UsernameCheck.Taken, error = null)
                    } else {
                        it.copy(saving = false, error = result.message)
                    }
                }
            }
        }
    }

    /** The website only lets a profile disconnect GitHub from here; connecting needs its OAuth page. */
    fun disconnectGithubFromProfile() {
        val draft = _edit.value
        if (draft.saving || draft.disconnecting) return
        _edit.update { it.copy(disconnecting = true, error = null) }
        viewModelScope.launch {
            val body = buildJsonObject { put("github_username", "") }
            when (val result = repo.updateProfile(body)) {
                is ApiResult.Ok -> {
                    _edit.update { it.copy(disconnecting = false) }
                    _profileEvents.trySend(ProfileEvent.GithubDisconnected(result.value))
                }
                is ApiResult.Err -> _edit.update {
                    it.copy(disconnecting = false, error = result.message)
                }
            }
        }
    }

    // ---- Personalization ----

    private val _personalization = MutableStateFlow(PersonalizationUi())
    val personalization: StateFlow<PersonalizationUi> = _personalization.asStateFlow()

    fun loadPersonalization() {
        viewModelScope.launch {
            val result = repo.personalization()
            _personalization.update {
                if (result is ApiResult.Ok) it.copy(loading = false, enabled = result.value.enabled)
                else it.copy(loading = false)
            }
        }
    }

    fun setPersonalization(enabled: Boolean) {
        if (_personalization.value.saving) return
        _personalization.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (repo.setPersonalization(enabled)) {
                is ApiResult.Ok -> _personalization.update { it.copy(saving = false, enabled = enabled) }
                is ApiResult.Err -> _personalization.update {
                    it.copy(
                        saving = false,
                        error = if (enabled) "Could not turn personalization on." else "Could not turn personalization off.",
                    )
                }
            }
        }
    }

    fun clearPersonalization() {
        if (_personalization.value.saving) return
        _personalization.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (repo.clearPersonalization()) {
                is ApiResult.Ok -> {
                    _personalization.update { it.copy(saving = false, cleared = true) }
                    delay(3000)
                    _personalization.update { it.copy(cleared = false) }
                }
                is ApiResult.Err -> _personalization.update {
                    it.copy(saving = false, error = "Could not clear personalization data.")
                }
            }
        }
    }

    // ---- Notifications ----

    private val _notifications = MutableStateFlow(NotificationsUi())
    val notifications: StateFlow<NotificationsUi> = _notifications.asStateFlow()

    fun loadNotifications() {
        viewModelScope.launch {
            when (val result = repo.pushPrefs()) {
                is ApiResult.Ok -> _notifications.update {
                    it.copy(
                        loading = false,
                        hotAlerts = result.value.prefs.hotAlerts,
                        dailyDigest = result.value.prefs.dailyDigest,
                        interests = result.value.interests,
                        error = null,
                    )
                }
                is ApiResult.Err -> _notifications.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun setHotAlerts(value: Boolean) {
        val previous = _notifications.value.hotAlerts
        _notifications.update { it.copy(hotAlerts = value, error = null) }
        viewModelScope.launch {
            if (repo.setHotAlerts(value) is ApiResult.Err) {
                _notifications.update { it.copy(hotAlerts = previous, error = "Couldn't save that.") }
            }
        }
    }

    fun setDailyDigest(value: Boolean) {
        val previous = _notifications.value.dailyDigest
        _notifications.update { it.copy(dailyDigest = value, error = null) }
        viewModelScope.launch {
            if (repo.setDailyDigest(value) is ApiResult.Err) {
                _notifications.update { it.copy(dailyDigest = previous, error = "Couldn't save that.") }
            }
        }
    }

    fun toggleInterest(kind: InterestKind, value: String) {
        val current = _notifications.value.interests
        val list = if (kind == InterestKind.Language) current.languages else current.topics
        val has = list.contains(value)
        if (!has && list.size >= MAX_INTERESTS) {
            _notifications.update { it.copy(interestError = "You can pick up to $MAX_INTERESTS.") }
            return
        }
        val updated = if (has) list - value else list + value
        saveInterests(
            if (kind == InterestKind.Language) current.copy(languages = updated) else current.copy(topics = updated),
        )
    }

    /** Returns true when the draft was accepted (so the text field can clear). */
    fun addTopic(raw: String): Boolean {
        val topic = raw.trim().lowercase().removePrefix("#").replace(Regex("\\s+"), "-")
        if (topic.isEmpty()) return false
        if (!Regex("^[a-z0-9][a-z0-9-]{0,49}\$").matches(topic)) {
            _notifications.update { it.copy(interestError = "Topics can use letters, numbers and dashes.") }
            return false
        }
        if (!_notifications.value.interests.topics.contains(topic)) {
            toggleInterest(InterestKind.Topic, topic)
        }
        return true
    }

    private fun saveInterests(next: Interests) {
        val previous = _notifications.value.interests
        _notifications.update { it.copy(interests = next, interestError = null) }
        viewModelScope.launch {
            when (val result = repo.setInterests(next)) {
                is ApiResult.Ok -> _notifications.update { it.copy(interests = result.value.interests) }
                is ApiResult.Err -> _notifications.update {
                    it.copy(interests = previous, interestError = result.message)
                }
            }
        }
    }

    fun sendTestNotification() {
        if (_notifications.value.testing) return
        _notifications.update { it.copy(testing = true, error = null, testSent = false) }
        viewModelScope.launch {
            when (val result = repo.pushTest()) {
                is ApiResult.Ok -> {
                    _notifications.update { it.copy(testing = false, testSent = true) }
                    delay(4000)
                    _notifications.update { it.copy(testSent = false) }
                }
                is ApiResult.Err -> _notifications.update { it.copy(testing = false, error = result.message) }
            }
        }
    }

    // ---- GitHub API connection ----

    private val _github = MutableStateFlow(GithubUi())
    val github: StateFlow<GithubUi> = _github.asStateFlow()

    fun loadGithub() {
        viewModelScope.launch {
            val result = repo.githubToken()
            _github.update {
                if (result is ApiResult.Ok && result.value.connected) {
                    it.copy(loading = false, status = result.value)
                } else {
                    it.copy(loading = false, status = null)
                }
            }
        }
    }

    fun clearGithubError() {
        _github.update { if (it.phase == TokenPhase.Error) it.copy(phase = TokenPhase.Idle, error = null) else it }
    }

    fun connectGithub(token: String) {
        val pat = token.trim()
        if (pat.isEmpty() || _github.value.phase == TokenPhase.Checking) return
        _github.update { it.copy(phase = TokenPhase.Checking, error = null) }
        viewModelScope.launch {
            when (val result = repo.saveGithubToken(pat)) {
                is ApiResult.Ok -> {
                    _github.update {
                        it.copy(
                            phase = TokenPhase.Success,
                            status = GithubTokenStatus(
                                connected = true,
                                login = result.value.login,
                                rate = result.value.rate,
                                search = result.value.search,
                            ),
                        )
                    }
                    // Hold the check mark for a beat, then settle into the connected view.
                    delay(1200)
                    _github.update { it.copy(phase = TokenPhase.Idle) }
                }
                is ApiResult.Err -> _github.update { it.copy(phase = TokenPhase.Error, error = result.message) }
            }
        }
    }

    fun removeGithubToken() {
        if (_github.value.removing) return
        _github.update { it.copy(removing = true, error = null) }
        viewModelScope.launch {
            when (repo.removeGithubToken()) {
                is ApiResult.Ok -> _github.update { it.copy(removing = false, status = null) }
                is ApiResult.Err -> _github.update {
                    it.copy(removing = false, error = "Could not remove that token. Try again.")
                }
            }
        }
    }

    // ---- Notification inbox ----

    private val _inbox = MutableStateFlow(NotificationInboxUi())
    val inbox: StateFlow<NotificationInboxUi> = _inbox.asStateFlow()

    fun loadNotificationInbox() {
        if (!_inbox.value.loading && _inbox.value.error == null) return
        viewModelScope.launch {
            when (val result = repo.notifications()) {
                is ApiResult.Ok -> {
                    _inbox.value = NotificationInboxUi(loading = false, items = result.value.notifications)
                    repo.markNotificationsSeen(result.value.notifications.firstOrNull()?.createdAt)
                }
                is ApiResult.Err -> _inbox.value = NotificationInboxUi(loading = false, error = result.message)
            }
        }
    }

    fun retryNotificationInbox() {
        _inbox.value = NotificationInboxUi()
        loadNotificationInbox()
    }

    // ---- About ----

    private val _logs = MutableStateFlow(LogsUi())
    val logs: StateFlow<LogsUi> = _logs.asStateFlow()

    fun loadLogs() {
        if (_logs.value.logs != null) return
        viewModelScope.launch {
            _logs.value = when (val result = repo.buildLogs()) {
                is ApiResult.Ok -> LogsUi(logs = result.value.logs)
                is ApiResult.Err -> LogsUi(error = result.message)
            }
        }
    }

    private val _feature = MutableStateFlow(FeatureUi())
    val feature: StateFlow<FeatureUi> = _feature.asStateFlow()

    fun resetFeature() {
        _feature.value = FeatureUi()
    }

    fun sendFeatureRequest(message: String, contact: String) {
        val text = message.trim()
        if (text.isEmpty() || _feature.value.sending) return
        _feature.value = FeatureUi(sending = true)
        viewModelScope.launch {
            _feature.value = when (val result = repo.featureRequest(text, contact.trim())) {
                is ApiResult.Ok -> FeatureUi(sent = true)
                is ApiResult.Err -> FeatureUi(error = result.message)
            }
        }
    }

    companion object {
        const val ALL_OTHERS = "all_others"

        /** The same rule the server enforces on usernames. */
        private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,20}$")
    }
}

class SettingsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(container.settingsRepository, container.appPreferences) as T
}
