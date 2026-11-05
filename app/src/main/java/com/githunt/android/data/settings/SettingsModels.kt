package com.githunt.android.data.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Reads a JSON number or string as a String (Postgres ids can arrive either way). */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return (element as? JsonPrimitive)?.content ?: element.toString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

// ---- Security: signed-in devices ----

@Serializable
data class DeviceSession(
    @Serializable(with = FlexibleStringSerializer::class) val id: String,
    val label: String = "Unknown device",
    val ip: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

@Serializable
data class SessionsResponse(val sessions: List<DeviceSession> = emptyList())

@Serializable
data class RevokeResponse(
    val ok: Boolean = false,
    @SerialName("revoked_current") val revokedCurrent: Boolean = false,
)

// ---- Account ----

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

// ---- Personalization ----

@Serializable
data class PersonalizationState(val enabled: Boolean = true)

@Serializable
data class PersonalizationPatch(val enabled: Boolean)

// ---- Notifications ----

@Serializable
data class Interests(
    val languages: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
)

@Serializable
data class PushToggles(
    @SerialName("hot_alerts") val hotAlerts: Boolean = true,
    @SerialName("daily_digest") val dailyDigest: Boolean = true,
)

@Serializable
data class PushPrefsResponse(
    val configured: Boolean = true,
    val prefs: PushToggles = PushToggles(),
    val interests: Interests = Interests(),
    val devices: Int = 0,
)

@Serializable
data class InterestsResponse(val interests: Interests = Interests())

// One class per PATCH: the server treats any present key as an update, so we never send nulls.
@Serializable
data class HotAlertsPatch(@SerialName("hot_alerts") val hotAlerts: Boolean)

@Serializable
data class DigestPatch(@SerialName("daily_digest") val dailyDigest: Boolean)

@Serializable
data class InterestsPatch(val languages: List<String>, val topics: List<String>)

// ---- GitHub API connection ----

@Serializable
data class RateInfo(
    val limit: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0,
    val reset: Long? = null,
)

@Serializable
data class GithubTokenStatus(
    val connected: Boolean = false,
    val login: String? = null,
    @SerialName("added_at") val addedAt: String? = null,
    val invalid: Boolean = false,
    val rate: RateInfo? = null,
    val search: RateInfo? = null,
    val ok: Boolean = false,
)

@Serializable
data class SaveTokenRequest(val token: String)

// ---- About ----

@Serializable
data class FeatureRequestBody(val message: String, val contact: String)

@Serializable
data class BuildLog(
    val file: String = "",
    val version: String = "",
    val title: String = "",
    val date: String? = null,
    val buildNumber: Int? = null,
    val markdown: String = "",
)

@Serializable
data class BuildLogsResponse(val logs: List<BuildLog> = emptyList())
