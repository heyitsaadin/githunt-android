package com.githunt.android.data.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import coil.map.Mapper
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photos for the profile editor.
 *
 * The website stores avatar_url and background_url as `data:image/...;base64,...` strings, so the
 * app does the same. Unlike the website, which uploads the original file untouched, we shrink the
 * picture first: a phone photo is often 4 to 12 MB, which is more than the server accepts in one
 * request body.
 */
object ProfileImages {
    /** Avatars are cropped to a square this many pixels wide. */
    const val AVATAR_SIZE = 512

    /** Cover photos are scaled so their longest side is at most this many pixels. */
    const val COVER_MAX_SIDE = 1600

    /** If a JPEG is still bigger than this we lower the quality and try again. */
    private const val MAX_BYTES = 1_200_000

    /**
     * Reads the picture at [uri], rotates it upright, shrinks it ([square] = centre-cropped avatar,
     * otherwise a cover) and returns JPEG bytes. Null when the file can't be read as an image.
     */
    suspend fun encode(resolver: ContentResolver, uri: Uri, square: Boolean): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                encodeBlocking(resolver, uri, square)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }

    private fun encodeBlocking(resolver: ContentResolver, uri: Uri, square: Boolean): ByteArray? {
        // 1. Read just the size, so a huge photo is never decoded at full resolution.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = if (square) AVATAR_SIZE else COVER_MAX_SIDE
        val basis = if (square) min(bounds.outWidth, bounds.outHeight) else max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (basis / (sample * 2) >= target) sample *= 2

        // 2. Decode at (roughly) the size we need.
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val pixelsStream = resolver.openInputStream(uri) ?: return null
        val decoded = pixelsStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

        // 3. Phones store "which way is up" in EXIF instead of rotating the pixels.
        val degrees = readRotation(resolver, uri)
        val upright = if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        // 4. Crop (avatar) or scale (cover) to the final size.
        val sized = if (square) {
            val side = min(upright.width, upright.height)
            val cropped = Bitmap.createBitmap(
                upright,
                (upright.width - side) / 2,
                (upright.height - side) / 2,
                side,
                side,
            )
            if (side > AVATAR_SIZE) Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true) else cropped
        } else {
            val longest = max(upright.width, upright.height)
            if (longest > COVER_MAX_SIDE) {
                val factor = COVER_MAX_SIDE.toFloat() / longest
                Bitmap.createScaledBitmap(
                    upright,
                    (upright.width * factor).roundToInt().coerceAtLeast(1),
                    (upright.height * factor).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else {
                upright
            }
        }

        // 5. JPEG has no transparency, so put the picture on white first (a see-through PNG
        //    would otherwise turn black).
        val flat = Bitmap.createBitmap(sized.width, sized.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(sized, 0f, 0f, null)
        }

        var quality = 88
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)
        return bytes
    }

    private fun readRotation(resolver: ContentResolver, uri: Uri): Float {
        val stream = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return 0f
        return try {
            stream.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) {
            0f
        }
    }

    /** The string the server stores for a picked photo. */
    fun toDataUrl(jpeg: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

    /** Bytes of a `data:image/...;base64,...` string, or null if [value] isn't one. */
    fun decodeDataUri(value: String?): ByteArray? {
        if (value == null || !value.startsWith("data:image/", ignoreCase = true)) return null
        val comma = value.indexOf(',')
        if (comma < 0 || !value.substring(0, comma).contains(";base64", ignoreCase = true)) return null
        return try {
            Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Lets every Coil image in the app show a photo that the profile stores as a data URI (which is
 * how the website saves uploads), for example the avatar in the bottom bar and in Settings.
 * Anything that isn't a data URI is left for Coil's normal handling.
 */
class DataUriMapper : Mapper<String, ByteArray> {
    override fun map(data: String, options: Options): ByteArray? = ProfileImages.decodeDataUri(data)
}
