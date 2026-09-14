package com.githunt.android.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Encodes a picked image as a `data:<mime>;base64,<data>` string — the
 * exact format the web app produces via FileReader.readAsDataURL() in
 * app/compose/page.js's handleImagePick(), which is what POST /api/posts
 * expects in the `image.dataUrl` field. Keeping this identical means no
 * backend changes are needed to accept native uploads.
 */
object ImageEncoder {

    /** Roughly matches typical web upload expectations; adjust if your
     *  deployment enforces a different limit server-side. */
    private const val MAX_BYTES = 8 * 1024 * 1024 // 8MB

    data class EncodedImage(val dataUrl: String, val name: String)

    fun encode(context: Context, uri: Uri): EncodedImage? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var read: Int
            var total = 0
            while (input.read(chunk).also { read = it } != -1) {
                total += read
                if (total > MAX_BYTES) return null // caller should show a "too large" error
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: return null

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$base64"
        val name = queryDisplayName(context, uri) ?: "image"

        return EncodedImage(dataUrl, name)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
