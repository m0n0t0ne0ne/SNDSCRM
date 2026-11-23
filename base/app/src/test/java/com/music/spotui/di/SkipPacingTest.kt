package com.music.spotui.di

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipPacingTest {
    @Test fun `a few skips keep the usual gap`() {
        val p = SkipPacing()
        assertEquals(SkipPacing.GAP_MS, p.gap(0))
        p.sent(0); p.sent(2_000)
        assertEquals(SkipPacing.GAP_MS, p.gap(4_000))
    }

    @Test fun `quick skips space out`() {
        val p = SkipPacing()
        listOf(0L, 2_000L, 4_000L).forEach(p::sent)
        assertEquals(SkipPacing.QUICK_GAP_MS, p.gap(6_000))
        listOf(8_000L, 12_000L, 16_000L).forEach(p::sent)
        assertEquals(SkipPacing.BUSY_GAP_MS, p.gap(18_000))
    }

    @Test fun `the gap comes back once the skips are old`() {
        val p = SkipPacing()
        (0 until 6).forEach { p.sent(it * 2_000L) }
        assertEquals(SkipPacing.GAP_MS, p.gap(10_000 + SkipPacing.WINDOW_MS + 1))
    }
}
