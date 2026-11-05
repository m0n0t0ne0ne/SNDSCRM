package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Trimmed from Spotui: stream quality, YouTube and web-playback settings are gone (the Web
// Player engine streams Spotify's own audio). Crossfade is ours: a fade, not an overlap.

private const val PREF = "settings_prefs"
private const val KEY_LIBRARY_GRID = "library_grid_view"
private const val KEY_LYRIC_TRANSLATE_LANG = "lyric_translate_lang"
private const val KEY_LYRICS_EXTRA = "lyrics_extra_sources"
private const val KEY_CROSSFADE = "crossfade_seconds"

/** Extra lyrics sources (NetEase, Genius) when Spotify and LRCLIB have none. Off by default. */
fun isLyricsExtraSources(c: Context): Boolean = prefs(c).getBoolean(KEY_LYRICS_EXTRA, false)
fun setLyricsExtraSources(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LYRICS_EXTRA, v).apply()

/** Lyrics timed word by word first (karaoke); off: line by line, Spotify's first. */
fun isLyricsWordSync(c: Context): Boolean = prefs(c).getBoolean("lyrics_word_sync", true)
fun setLyricsWordSync(c: Context, v: Boolean) = prefs(c).edit().putBoolean("lyrics_word_sync", v).apply()

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

/** The bottom bar: fading into the list (the default), or solid black. Read by the bar as state. */
object SolidNavBar {
    private const val KEY = "solid_nav_bar"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, false))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/** "When your music ends, keep playing similar songs" (on by default). Read by the player. */
object AutoplayPref {
    private const val KEY = "autoplay_similar"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/**
 * "Myspoti shuffle" (off by default): with shuffle on, a list plays every one of its songs once, in
 * an order Myspoti draws, before it starts over, instead of Spotify's shuffle (see
 * [com.music.spotui.di.AppShuffle]). Off: Spotify shuffles, as before.
 */
object AppShufflePref {
    private const val KEY = "myspoti_shuffle"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, false))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
        if (!value) com.music.spotui.di.AppShuffle.forget()
    }
}

/**
 * "Count extension plays on Spotify" (on by default): a song an extension streams is played by the
 * Web Player too, unheard, so Spotify counts it and Spotify Connect sees and controls it (see
 * [com.music.spotui.di.ConnectMirror]). Off: the extension's stream alone, as before.
 */
object ConnectMirrorPref {
    private const val KEY = "connect_mirror"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
        com.music.spotui.di.ConnectMirror.settingChanged()
    }
}

/** Screen rotation: portrait (the default), automatic (follows the system), or landscape. */
object OrientationPref {
    private const val KEY = "screen_orientation"
    const val PORTRAIT = "portrait"
    const val AUTO = "auto"
    const val LANDSCAPE = "landscape"
    var mode by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getString(KEY, PORTRAIT) ?: PORTRAIT)
        private set

    fun set(c: Context, value: String) {
        mode = value
        prefs(c).edit().putString(KEY, value).apply()
    }

    /** The [android.content.pm.ActivityInfo] value for the chosen mode. */
    fun requested(): Int = when (mode) {
        AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

/** Library layout: false = rows (default), true = Spotify-style 3-column grid. */
fun isLibraryGridView(c: Context): Boolean = prefs(c).getBoolean(KEY_LIBRARY_GRID, false)
fun setLibraryGridView(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIBRARY_GRID, v).apply()

/**
 * Crossfade length in seconds, 0 = off (the default: nothing changes unless asked for).
 *
 * It is a fade, not an overlap: the page plays one media element, so the next song cannot
 * start before this one ends. The song fades out over its last N seconds, the next one fades
 * in. The value is Compose state, so the settings row redraws when it changes.
 */
object CrossfadePref {
    const val MAX = 12
    /** Crossfade was removed: always off, a length saved before is ignored. */
    var seconds by androidx.compose.runtime.mutableStateOf(0)
        private set

    @Suppress("UNUSED_PARAMETER")
    fun set(c: Context, value: Int) {
        seconds = 0
        applyCrossfade(0)
    }
}

/**
 * "Music videos": the video button in the player, the lookup behind it and a clip that starts
 * by itself on the next song. On by default; off, the player is audio (and Canvas) only.
 */
object MusicVideoPref {
    private const val KEY = "music_video_enabled"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
        if (!value) com.music.spotui.video.MusicVideoController.setOn(false)
    }
}

@Suppress("UNUSED_PARAMETER")
fun getCrossfadeSeconds(c: Context): Int = 0

/** Gives the saved length to the player (at start, and at every change); 0 stops a fade at once. */
fun applyCrossfade(seconds: Int) {
    val runtime = app.myspoti.host.android.MyspotiRuntime
    if (!runtime.isStarted) return
    runtime.spotify.player.crossfadeSeconds = seconds
}

fun getLyricTranslateLang(c: Context): String =
    prefs(c).getString(KEY_LYRIC_TRANSLATE_LANG, "").orEmpty()

fun setLyricTranslateLang(c: Context, code: String) =
    prefs(c).edit().putString(KEY_LYRIC_TRANSLATE_LANG, code.trim()).apply()

