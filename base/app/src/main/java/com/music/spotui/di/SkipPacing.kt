/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.di

/**
 * SoundScream: how far apart skips are sent to Spotify. Each song a skip lands on asks Spotify for
 * an audio licence, and the page asks for the next song's too. A free account skipping through
 * five songs in nine seconds was answered 429 (too many requests) and playback was held for a
 * minute. The more skips were sent lately, the longer the gap; taps in between still add up and
 * show on screen, and go out as one jump to the song they add up to.
 */
internal class SkipPacing(private val windowMs: Long = WINDOW_MS) {
    private val sent = ArrayDeque<Long>()

    /** A skip went to Spotify at [now] (elapsed ms). */
    fun sent(now: Long) {
        trim(now)
        sent.addLast(now)
    }

    /** The gap the next skip keeps after the last one sent, given the skips sent in the window. */
    fun gap(now: Long): Long {
        trim(now)
        return when {
            sent.size >= BUSY -> BUSY_GAP_MS
            sent.size >= QUICK -> QUICK_GAP_MS
            else -> GAP_MS
        }
    }

    private fun trim(now: Long) {
        while (sent.isNotEmpty() && now - sent.first() > windowMs) sent.removeFirst()
    }

    companion object {
        const val WINDOW_MS = 30_000L
        const val GAP_MS = 2_000L
        const val QUICK = 3
        const val QUICK_GAP_MS = 4_000L
        const val BUSY = 6
        const val BUSY_GAP_MS = 6_000L
    }
}
