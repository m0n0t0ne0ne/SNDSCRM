package com.music.spotui.ui.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtMusicMatcherTest {
    private val body = javaClass.classLoader!!.getResource("ytmusic/search_get_lucky.json")!!.readText()
    private val getLucky = TransferSong("Get Lucky (feat. Pharrell Williams & Nile Rodgers)", "Daft Punk, Pharrell Williams, Nile Rodgers", 369_000)

    @Test fun parsesPlayableResultsOnly() {
        val all = YtMusicMatcher.parse(body)
        assertTrue(all.isNotEmpty())
        // Artist rows have no video and are left out.
        assertTrue(all.none { it.title == "Daft Punk" })
        assertTrue(all.any { it.videoId == "5glDAaCaazc" && it.type == "OMV" })
    }

    @Test fun picksTheOfficialVideoOverUploads() {
        val m = YtMusicMatcher.pick(getLucky, YtMusicMatcher.parse(body))
        assertNotNull(m)
        assertEquals("5glDAaCaazc", m!!.candidate.videoId)
    }

    @Test fun karaokeCoverLosesToTheOriginal() {
        val karaoke = YtCandidate("k", "Daft Punk - Get Lucky - Slowed Down Piano Karaoke Instrumental Cover", "Video • PianoNest", "UGC", null)
        assertTrue(YtMusicMatcher.score(getLucky, karaoke) < YtMusicMatcher.MIN_SCORE)
    }

    @Test fun officialAudioWithSameLengthWins() {
        val atv = YtCandidate("a", "Get Lucky", "Song • Daft Punk • Random Access Memories • 6:09", "ATV", 369)
        val omv = YtCandidate("o", "Get Lucky (Official Video)", "Video • Daft Punk • 4:08", "OMV", 248)
        assertEquals("a", YtMusicMatcher.pick(getLucky, listOf(omv, atv))!!.candidate.videoId)
    }

    @Test fun unrelatedTitleIsNotFound() {
        val other = YtCandidate("x", "Around the World", "Song • Daft Punk • 7:09", "ATV", 429)
        assertNull(YtMusicMatcher.pick(getLucky, listOf(other)))
    }

    @Test fun cleansFeaturesAndRemasters() {
        assertEquals("Get Lucky", YtMusicMatcher.cleanTitle("Get Lucky (feat. Pharrell Williams & Nile Rodgers)"))
        assertEquals("Come Together", YtMusicMatcher.cleanTitle("Come Together - Remastered 2009"))
        assertEquals("Heroes", YtMusicMatcher.cleanTitle("Heroes (2017 Remaster)"))
    }

    @Test fun linksHoldFiftySongsEach() {
        val links = YtMusicMatcher.playlistLinks(List(120) { "id$it" })
        assertEquals(3, links.size)
        assertEquals(50, links[0].substringAfter("video_ids=").split(",").size)
        assertEquals(20, links[2].substringAfter("video_ids=").split(",").size)
    }
}
