package com.githunt.android.ui.compose

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.model.*
import com.githunt.android.data.repo.ApiResult
import com.githunt.android.data.repo.FeedRepository
import com.githunt.android.util.ImageEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URI

data class ComposeUiState(
    val text: String = "",
    val image: ImageAttachment? = null,
    val file: FileAttachment? = null,
    val link: LinkAttachment? = null,
    val repo: AttachedRepo? = null,
    val repoLookupInput: String = "",
    val isLookingUpRepo: Boolean = false,
    val repoLookupError: String? = null,
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
    val imageTooLarge: Boolean = false,
    val emailUnverified: Boolean = false,
    val didPost: Boolean = false,
) {
    // Mirrors compose/page.js's `canPost` exactly: some content required,
    // not currently submitting.
    val canPost: Boolean
        get() = !isPosting && (text.isNotBlank() || image != null || file != null || link != null || repo != null)
}

/**
 * Mirrors app/compose/page.js + POST /api/posts, including image, link, and
 * repo attachments (file attachments are metadata-only in the web app too --
 * see FileAttachment -- so there's nothing to upload for those beyond name
 * and size).
 */
class ComposeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FeedRepository.getInstance(application)

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState

    fun setText(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun pickImage(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val encoded = ImageEncoder.encode(app, uri)
            if (encoded == null) {
                _uiState.value = _uiState.value.copy(imageTooLarge = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    image = ImageAttachment(encoded.dataUrl, encoded.name),
                    imageTooLarge = false,
                )
            }
        }
    }

    fun clearImage() {
        _uiState.value = _uiState.value.copy(image = null, imageTooLarge = false)
    }

    /** File attachments are metadata-only in the web app too (name + size,
     *  no actual upload) -- see FileAttachment / handleFilePick in
     *  app/compose/page.js -- so this just records what the user picked. */
    fun setFile(name: String, sizeBytes: Long) {
        _uiState.value = _uiState.value.copy(file = FileAttachment(name, sizeBytes))
    }

    fun clearFile() {
        _uiState.value = _uiState.value.copy(file = null)
    }

    fun addLink(raw: String) {
        var url = raw.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        val domain = try {
            URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
        _uiState.value = _uiState.value.copy(link = LinkAttachment(url, domain))
    }

    fun clearLink() {
        _uiState.value = _uiState.value.copy(link = null)
    }

    fun setRepoLookupInput(value: String) {
        _uiState.value = _uiState.value.copy(repoLookupInput = value)
    }

    fun lookupRepo() {
        val input = _uiState.value.repoLookupInput.trim()
        if (input.isEmpty()) return
        _uiState.value = _uiState.value.copy(isLookingUpRepo = true, repoLookupError = null)
        viewModelScope.launch {
            when (val result = repo.lookupRepo(input)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        repo = result.data,
                        repoLookupInput = "",
                        isLookingUpRepo = false,
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLookingUpRepo = false,
                        repoLookupError = result.message,
                    )
                }
            }
        }
    }

    fun clearRepo() {
        _uiState.value = _uiState.value.copy(repo = null)
    }

    fun post() {
        val state = _uiState.value
        if (!state.canPost) return
        _uiState.value = state.copy(isPosting = true, errorMessage = null)
        viewModelScope.launch {
            val request = CreatePostRequest(
                text = state.text.trim(),
                image = state.image,
                file = state.file,
                link = state.link,
                repo = state.repo,
            )
            when (val result = repo.createPost(request)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isPosting = false, didPost = true)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isPosting = false,
                        errorMessage = result.message,
                        emailUnverified = result.code == "EMAIL_UNVERIFIED",
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = ComposeUiState()
    }
}
