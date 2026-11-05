package com.githunt.android.data.settings

import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json

class SettingsRepository(
    private val api: SettingsApi,
    private val json: Json,
) {
    suspend fun sessions(): ApiResult<SessionsResponse> = apiCall(json) { api.sessions() }

    suspend fun revokeSession(id: String): ApiResult<RevokeResponse> = apiCall(json) { api.revokeSession(id) }

    suspend fun revokeOthers(): ApiResult<Unit> = apiCall(json) { api.revokeOthers() }

    suspend fun changePassword(current: String, newPassword: String): ApiResult<Unit> =
        apiCall(json) { api.changePassword(ChangePasswordRequest(current, newPassword)) }

    suspend fun deleteAccount(): ApiResult<Unit> = apiCall(json) { api.deleteAccount() }

    suspend fun personalization(): ApiResult<PersonalizationState> = apiCall(json) { api.personalization() }

    suspend fun setPersonalization(enabled: Boolean): ApiResult<Unit> =
        apiCall(json) { api.setPersonalization(PersonalizationPatch(enabled)) }

    suspend fun clearPersonalization(): ApiResult<Unit> = apiCall(json) { api.clearPersonalization() }

    suspend fun pushPrefs(): ApiResult<PushPrefsResponse> = apiCall(json) { api.pushPrefs() }

    suspend fun setHotAlerts(value: Boolean): ApiResult<Unit> =
        apiCall(json) { api.setHotAlerts(HotAlertsPatch(value)) }

    suspend fun setDailyDigest(value: Boolean): ApiResult<Unit> =
        apiCall(json) { api.setDailyDigest(DigestPatch(value)) }

    suspend fun setInterests(interests: Interests): ApiResult<InterestsResponse> =
        apiCall(json) { api.setInterests(InterestsPatch(interests.languages, interests.topics)) }

    suspend fun pushTest(): ApiResult<Unit> = apiCall(json) { api.pushTest() }

    suspend fun githubToken(): ApiResult<GithubTokenStatus> = apiCall(json) { api.githubToken() }

    suspend fun saveGithubToken(token: String): ApiResult<GithubTokenStatus> =
        apiCall(json) { api.saveGithubToken(SaveTokenRequest(token)) }

    suspend fun removeGithubToken(): ApiResult<Unit> = apiCall(json) { api.removeGithubToken() }

    suspend fun featureRequest(message: String, contact: String): ApiResult<Unit> =
        apiCall(json) { api.featureRequest(FeatureRequestBody(message, contact)) }

    suspend fun notifications(): ApiResult<NotificationInboxResponse> =
        apiCall(json) { api.notifications() }

    suspend fun markNotificationsSeen(upTo: String?): ApiResult<Unit> =
        apiCall(json) { api.markNotificationsSeen(NotificationSeenRequest(upTo)) }

    suspend fun buildLogs(): ApiResult<BuildLogsResponse> = apiCall(json) { api.buildLogs() }
}
