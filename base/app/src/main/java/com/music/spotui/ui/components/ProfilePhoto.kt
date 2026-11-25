/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import app.myspoti.host.android.Logbook

/**
 * SoundScream: a photo picked for the profile picture, made ready for Spotify. Spotify takes at
 * least 300 × 300 pixels and at most 10 MB: the photo is cut to a centred square (as the round
 * picture shows it), made 640 pixels at most and sent as JPEG.
 *
 * Read with [ImageDecoder]: it reads what the photo picker hands over (JPEG, PNG, HEIF, WebP,
 * AVIF...), turns the photo the way the camera held it by itself, and reads a big photo at a
 * reduced size. The BitmapFactory reading it replaced turned some picked photos down with
 * nothing in the log but "Try again" (reported 25/11): every failure now says why.
 */
internal object ProfilePhoto {
    const val MIN_SIDE = 300
    private const val SIDE = 640

    sealed interface Result {
        class Ready(val jpeg: ByteArray) : Result
        object TooSmall : Result
        object Unreadable : Result
    }

    private class TooSmall : Exception()

    fun prepare(context: Context, uri: Uri): Result {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "unknown type"
        val photo = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                if (minOf(w, h) < MIN_SIDE) throw TooSmall()
                // Its short side brought down to the size sent (a 12 MP photo is read at about 640).
                val scale = SIDE.toFloat() / minOf(w, h)
                if (scale < 1f) decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
                // Pixels the app can cut and compress (a hardware bitmap cannot be).
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: TooSmall) {
            Logbook.log("profile picture: photo smaller than $MIN_SIDE px ($type)")
            return Result.TooSmall
        } catch (e: Exception) {
            Logbook.log("profile picture: photo not read ($type): ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            return Result.Unreadable
        }
        val side = minOf(photo.width, photo.height)
        val square = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
        val out = if (side > SIDE) Bitmap.createScaledBitmap(square, SIDE, SIDE, true) else square
        val bytes = java.io.ByteArrayOutputStream().use { s -> out.compress(Bitmap.CompressFormat.JPEG, 90, s); s.toByteArray() }
        Logbook.log("profile picture: photo ready ($type, ${photo.width}x${photo.height} read, ${bytes.size / 1024} KB sent)")
        return Result.Ready(bytes)
    }
}
