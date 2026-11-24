/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

/**
 * SoundScream: a photo picked for the profile picture, made ready for Spotify. Spotify takes at
 * least 300 × 300 pixels and at most 10 MB: the photo is cut to a centred square (as the round
 * picture shows it), turned the way the camera held it, made 640 pixels at most and sent as JPEG.
 */
internal object ProfilePhoto {
    const val MIN_SIDE = 300
    private const val SIDE = 640

    sealed interface Result {
        class Ready(val jpeg: ByteArray) : Result
        object TooSmall : Result
        object Unreadable : Result
    }

    fun prepare(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return Result.Unreadable
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return Result.Unreadable
        if (minOf(w, h) < MIN_SIDE) return Result.TooSmall
        // Read at a reduced size when the photo is much bigger than needed (a 12 MP photo).
        var sample = 1
        while (minOf(w, h) / (sample * 2) >= SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val full = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return Result.Unreadable
        val turned = rotate(full, orientation(context, uri))
        val side = minOf(turned.width, turned.height)
        val square = Bitmap.createBitmap(turned, (turned.width - side) / 2, (turned.height - side) / 2, side, side)
        val out = if (side > SIDE) Bitmap.createScaledBitmap(square, SIDE, SIDE, true) else square
        val bytes = java.io.ByteArrayOutputStream().use { s -> out.compress(Bitmap.CompressFormat.JPEG, 90, s); s.toByteArray() }
        return Result.Ready(bytes)
    }

    private fun orientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: android.media.ExifInterface.ORIENTATION_NORMAL

    private fun rotate(b: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
