package com.githunt.android.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/** Outcome of one API call. Expected failures come back as [Err] instead of throwing. */
sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>

    data class Err(
        val message: String,
        val kind: Kind,
        val code: Int? = null,
    ) : ApiResult<Nothing>

    enum class Kind { Network, Unauthorized, RateLimited, Unavailable, Server }
}

/** The backend answers errors as `{ "error": "human readable text" }`. */
@Serializable
internal data class ErrorBody(val error: String? = null)

@Suppress("UNCHECKED_CAST")
internal suspend fun <T> apiCall(json: Json, block: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            ApiResult.Ok(response.body() as T)
        } else {
            val code = response.code()
            val serverMessage = parseError(json, response)
            ApiResult.Err(serverMessage ?: defaultMessage(code), kindFor(code), code)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.Err("Can't reach GitHunt. Check your connection and try again.", ApiResult.Kind.Network)
    } catch (e: SerializationException) {
        ApiResult.Err("GitHunt sent something unexpected. Please try again.", ApiResult.Kind.Server)
    }
}

private fun parseError(json: Json, response: Response<*>): String? {
    val raw = try {
        response.errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    if (raw.isNullOrBlank()) return null
    val parsed = try {
        json.decodeFromString(ErrorBody.serializer(), raw).error
    } catch (e: SerializationException) {
        null
    }
    return parsed?.takeIf { it.isNotBlank() }
}

private fun kindFor(code: Int): ApiResult.Kind = when (code) {
    401, 403 -> ApiResult.Kind.Unauthorized
    429 -> ApiResult.Kind.RateLimited
    502, 503, 504 -> ApiResult.Kind.Unavailable
    else -> ApiResult.Kind.Server
}

private fun defaultMessage(code: Int): String = when (code) {
    401, 403 -> "Please sign in again."
    429 -> "Too many attempts. Please try again in a minute."
    502, 503, 504 -> "GitHunt is waking up. Please try again in a moment."
    else -> "Something went wrong (error $code)."
}
