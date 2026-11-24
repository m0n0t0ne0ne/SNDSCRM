package app.myspoti.spotify

import app.myspoti.api.Repeat
import app.myspoti.spotify.bridge.AdBlock
import app.myspoti.spotify.bridge.Bridge
import app.myspoti.spotify.bridge.BridgeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** SoundScream: a page that takes the user's command and tells nothing is loaded again (watchLanding). */
class LandingWatchTest {
    private class FakeHost : BridgeHost {
        override val messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val calls = CopyOnWriteArrayList<Pair<String, String>>()
        override suspend fun evaluate(script: String) {
            val m = Regex("""Myspoti\.call\((\d+), "([^"]+)", (.*)\)\.catch""", RegexOption.DOT_MATCHES_ALL).find(script) ?: return
            calls += m.groupValues[2] to m.groupValues[3]
            // The page's media element: standing still, this phone the active device (or not).
            val value = if (m.groupValues[2] == "media.state") """{"paused":${mediaPaused},"muted":false,"level":1,"own":1,"time":0,"active":${mediaActive}}""" else "null"
            messages.emit("""{"type":"result","id":${m.groupValues[1]},"ok":true,"value":$value}""")
        }
        suspend fun post(json: String) = messages.emit(json)
        @Volatile var mediaActive = true
        @Volatile var mediaPaused = false
    }

    private lateinit var scope: CoroutineScope
    private lateinit var host: FakeHost
    private lateinit var player: SpotifyPlayer
    private lateinit var bridge: Bridge

    @BeforeEach fun up() {
        AdBlock.ahead.value = null
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        host = FakeHost()
        bridge = Bridge(host, scope)
        player = SpotifyPlayer(bridge, scope)
        // A message posted before the bridge listens is lost (a slow CI machine): wait for it.
        waitFor("bridge listening") { host.messages.subscriptionCount.value > 0 }
    }

    @AfterEach fun down() {
        AdBlock.ahead.value = null
        scope.cancel()
    }

    private fun track(name: String) =
        """{"kind":"track","uri":"spotify:track:$name","uid":"u$name","name":"$name","durationMs":200000}"""

    private fun state(item: String, paused: Boolean = false, repeat: Int = 0) =
        """{"type":"state","state":{"track":$item,"contextUri":"spotify:playlist:C","next":${track("N")},"isPaused":$paused,"isBuffering":false,
            |"repeat":$repeat,"positionMs":0,"timestamp":${System.currentTimeMillis()},"restrictions":{}}}""".trimMargin()

    private val ad = """{"kind":"ad","uri":"spotify:ad:1","name":"ad","durationMs":30000}"""

    private fun waitFor(what: String, ms: Long = 4_000, cond: () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            delay(20)
        }
    }

    private fun called(method: String) = host.calls.count { it.first == method }

    @Test fun `a pick the page says nothing about is played again on a new page`() = runBlocking {
        host.post("""{"type":"ready"}""")
        host.post(state(track("A"), paused = true))
        var reloads = 0
        player.reloadHost = {
            reloads++
            bridge.pageGone()
            host.post("""{"type":"ready"}""")
            host.post(state(track("A"), paused = true))
        }
        player.play("spotify:playlist:C", trackUri = "spotify:track:B")
        val plays = called("player.play")
        // The page answers ok and goes quiet.
        waitFor("reloaded", ms = SpotifyPlayer.LANDING_WATCH_MS + 6_000) { reloads == 1 }
        waitFor("asked again", ms = 6_000) { called("player.play") > plays }
        assertTrue(host.calls.last { it.first == "player.play" }.second.contains("spotify:track:B"))
        // The replay is not watched again: no loop of reloads.
        delay(SpotifyPlayer.LANDING_WATCH_MS + 1_000)
        assertEquals(1, reloads)
    }

    @Test fun `a pick the page answers with a state is left alone`() = runBlocking {
        host.post("""{"type":"ready"}""")
        host.post(state(track("A"), paused = true))
        var reloads = 0
        player.reloadHost = { reloads++ }
        player.play("spotify:playlist:C", trackUri = "spotify:track:B")
        host.post(state(track("B")))
        delay(SpotifyPlayer.LANDING_WATCH_MS + 1_000)
        assertEquals(0, reloads)
    }

    @Test fun `a page that keeps telling empty states is loaded again too`() = runBlocking {
        host.post("""{"type":"ready"}""")
        var reloads = 0
        player.reloadHost = {
            reloads++
            bridge.pageGone()
            host.post("""{"type":"ready"}""")
        }
        player.play("spotify:playlist:C", trackUri = "spotify:track:B")
        // A broken page: states, but never the song asked for.
        repeat(8) { host.post("""{"type":"state","state":null}"""); delay(500) }
        waitFor("reloaded", ms = SpotifyPlayer.LANDING_WATCH_MS + 6_000) { reloads == 1 }
    }

    @Test fun `a resume the page does not play is played again on a new page`() = runBlocking {
        host.post("""{"type":"ready"}""")
        host.post(state(track("A"), paused = true))
        var reloads = 0
        player.reloadHost = {
            reloads++
            bridge.pageGone()
            host.post("""{"type":"ready"}""")
            host.post(state(track("A"), paused = true))
        }
        waitFor("song known") { player.state.value?.item != null }
        val before = called("player.resume")
        scope.launch { runCatching { player.resume() } }
        waitFor("reloaded", ms = SpotifyPlayer.LANDING_WATCH_MS + 6_000) { reloads == 1 }
        waitFor("resumed again", ms = 6_000) { called("player.resume") >= before + 2 }
    }
}
