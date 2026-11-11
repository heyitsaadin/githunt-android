package com.githunt.android.data.translate

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** The website's translation endpoint: POST /api/translate { texts, target }. */
interface TranslateApi {
    @POST("api/translate")
    suspend fun translate(@Body body: TranslateRequest): Response<TranslateResponse>
}

@Serializable
data class TranslateRequest(
    val texts: List<String>,
    val target: String,
)

/**
 * Per-string arrays, in the same order as the request:
 * [translated] the translation (the original when it failed or was already the target language),
 * [sameLanguage] true when there was nothing to translate, [failed] true when no backend could do it.
 */
@Serializable
data class TranslateResponse(
    val translated: List<String> = emptyList(),
    val sameLanguage: List<Boolean> = emptyList(),
    val failed: List<Boolean> = emptyList(),
    val partial: Boolean = false,
    val error: String? = null,
)
