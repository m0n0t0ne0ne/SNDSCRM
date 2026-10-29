package com.music.spotui.ui.transfer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** A song to look up: what Spotify says about it. */
data class TransferSong(val title: String, val artist: String, val durationMs: Int)

/** One YouTube Music search result that can be played (a song or a video). */
data class YtCandidate(
    val videoId: String,
    val title: String,
    /** "Song • Artist • Album • 3:45" or "Video • Channel • 1M views • 4:09". */
    val subtitle: String,
    /** ATV (official audio), OMV (official music video), UGC (upload), or null. */
    val type: String?,
    val durationSec: Int?,
)

/** The match picked for a song, with the score it got (higher is surer). */
data class YtMatch(val candidate: YtCandidate, val score: Double)

/**
 * Finds songs on YouTube Music through its public search (no account, read-only), and picks
 * the result most likely to be the same recording: official audio first, the right artist,
 * the same title words and the same length; covers, karaoke and sped-up edits lose points.
 */
object YtMusicMatcher {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20251020.01.00"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    /** Below this, no result is taken: the song is reported as not found. */
    const val MIN_SCORE = 4.0

    /**
     * Searches [song] and returns the best match, or null when nothing is close enough.
     * Blocking: call off the main thread. Throws [java.io.IOException] when YouTube Music
     * can't be reached, so a lost connection is not mistaken for songs that don't exist.
     */
    fun find(song: TransferSong): YtMatch? {
        val artist = song.artist.split(",").first().trim()
        val query = "${cleanTitle(song.title)} $artist".trim()
        return pick(song, parse(search(query)))
    }

    private fun search(query: String): String {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                }
            }
            put("query", query)
        }.toString()
        val request = okhttp3.Request.Builder()
            .url(SEARCH_URL)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("YouTube Music search answered ${r.code}")
            return r.body.string()
        }
    }

    // ---------- parsing ----------

    /** Every playable result in a search response, in the order YouTube Music shows them. */
    fun parse(body: String): List<YtCandidate> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<YtCandidate>()
        val seen = HashSet<String>()
        walk(root) { key, obj ->
            val c = when (key) {
                "musicResponsiveListItemRenderer" -> listItem(obj)
                "musicCardShelfRenderer" -> card(obj)
                else -> null
            }
            if (c != null && seen.add(c.videoId)) out.add(c)
        }
        return out
    }

    private fun walk(e: JsonElement, visit: (String, JsonObject) -> Unit) {
        when (e) {
            is JsonObject -> for ((k, v) in e) {
                if (v is JsonObject && (k == "musicResponsiveListItemRenderer" || k == "musicCardShelfRenderer")) visit(k, v)
                walk(v, visit)
            }
            is JsonArray -> e.forEach { walk(it, visit) }
            else -> {}
        }
    }

    private fun listItem(o: JsonObject): YtCandidate? {
        val cols = (o["flexColumns"] as? JsonArray).orEmpty().mapNotNull {
            ((it as? JsonObject)?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject)?.get("text")
        }
        val title = runsText(cols.getOrNull(0))
        val subtitle = cols.drop(1).joinToString(" • ") { runsText(it) }
        val videoId = str(o, "playlistItemData", "videoId") ?: firstWatchVideoId(o) ?: return null
        return YtCandidate(videoId, title, subtitle, firstVideoType(o), lastDuration(subtitle))
    }

    private fun card(o: JsonObject): YtCandidate? {
        val title = runsText(o["title"])
        val subtitle = runsText(o["subtitle"])
        val videoId = firstWatchVideoId(o["title"] ?: o) ?: firstWatchVideoId(o["onTap"] ?: o) ?: return null
        return YtCandidate(videoId, title, subtitle, firstVideoType(o), lastDuration(subtitle))
    }

    private fun runsText(e: JsonElement?): String =
        ((e as? JsonObject)?.get("runs") as? JsonArray).orEmpty()
            .joinToString("") { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty() }

    private fun str(o: JsonObject, vararg path: String): String? {
        var cur: JsonElement? = o
        for (p in path) cur = (cur as? JsonObject)?.get(p)
        return (cur as? JsonPrimitive)?.contentOrNull
    }

    private fun firstWatchVideoId(e: JsonElement): String? {
        var found: String? = null
        fun go(x: JsonElement) {
            if (found != null) return
            when (x) {
                is JsonObject -> {
                    val we = x["watchEndpoint"] as? JsonObject
                    val id = (we?.get("videoId") as? JsonPrimitive)?.contentOrNull
                    if (id != null) { found = id; return }
                    x.values.forEach(::go)
                }
                is JsonArray -> x.forEach(::go)
                else -> {}
            }
        }
        go(e)
        return found
    }

    private fun firstVideoType(e: JsonElement): String? {
        var found: String? = null
        fun go(x: JsonElement) {
            if (found != null) return
            when (x) {
                is JsonObject -> {
                    val t = (x["musicVideoType"] as? JsonPrimitive)?.contentOrNull
                    if (t != null) { found = t.removePrefix("MUSIC_VIDEO_TYPE_"); return }
                    x.values.forEach(::go)
                }
                is JsonArray -> x.forEach(::go)
                else -> {}
            }
        }
        go(e)
        return found
    }

    private val DURATION = Regex("""\b(?:(\d+):)?(\d{1,2}):(\d{2})\b""")

    private fun lastDuration(s: String): Int? = DURATION.findAll(s).lastOrNull()?.let { m ->
        val h = m.groupValues[1].toIntOrNull() ?: 0
        h * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
    }

    // ---------- scoring ----------

    /** Words that mark a different recording; each one costs points unless the Spotify title has it too. */
    private val ALTERED = listOf("karaoke", "cover", "instrumental", "slowed", "sped", "nightcore", "8d", "reverb",
        "remix", "live", "acoustic", "piano", "bass boosted", "tribute", "lofi", "lo fi")

    /** The best candidate for [song], or null when none reaches [MIN_SCORE]. */
    fun pick(song: TransferSong, candidates: List<YtCandidate>): YtMatch? =
        candidates.map { YtMatch(it, score(song, it)) }.filter { it.score >= MIN_SCORE }.maxByOrNull { it.score }

    fun score(song: TransferSong, c: YtCandidate): Double {
        val want = tokens(cleanTitle(song.title))
        if (want.isEmpty()) return 0.0
        val got = tokens(c.title)
        val overlap = want.count { it in got }.toDouble() / want.size
        if (overlap < 0.6) return 0.0
        var s = overlap * 4
        val artists = song.artist.split(",").map { norm(it) }.filter { it.isNotBlank() }
        val where = norm(c.subtitle + " " + c.title)
        if (artists.firstOrNull()?.let { where.contains(it) } == true) s += 2
        else if (artists.any { where.contains(it) }) s += 1
        s += when (c.type) { "ATV" -> 2.0; "OMV" -> 1.5; "UGC" -> 0.0; null -> 0.5; else -> 0.5 }
        val dur = c.durationSec
        if (dur != null && song.durationMs > 0) {
            val diff = kotlin.math.abs(dur - song.durationMs / 1000)
            s += when {
                diff <= 3 -> 2.0
                diff <= 10 -> 1.0
                diff <= 30 -> 0.0
                diff <= 90 -> -2.0
                else -> -4.0
            }
        }
        val original = norm(song.title)
        val result = norm(c.title)
        ALTERED.forEach { w -> if (result.contains(w) && !original.contains(w)) s -= 3 }
        return s
    }

    /** The title without "(feat. …)", "- Remastered 2011" and similar tails that YouTube writes differently. */
    fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*\b(remaster(ed)?|version|edit|mono|stereo)\b.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[(\[][^)\]]*\b(remaster(ed)?)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun tokens(s: String): Set<String> =
        norm(s).split(' ').filter { it.isNotBlank() && it !in STOP }.toSet()

    private val STOP = setOf("the", "a", "an", "and", "of", "feat", "ft", "with")

    /** YouTube links that open the matched songs as a playlist, [PER_LINK] songs per link. */
    fun playlistLinks(videoIds: List<String>): List<String> =
        videoIds.chunked(PER_LINK).map { "https://www.youtube.com/watch_videos?video_ids=" + it.joinToString(",") }

    const val PER_LINK = 50

    fun songLink(videoId: String) = "https://music.youtube.com/watch?v=$videoId"
}
