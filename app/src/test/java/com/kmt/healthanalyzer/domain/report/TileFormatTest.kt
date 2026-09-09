package com.kmt.healthanalyzer.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileFormatTest {

    @Test
    fun `formats a decimal number with a comma, never a locale-dependent point`() {
        assertEquals("30,0", TileFormat.number(30.0, 1))
        assertEquals("95,3", TileFormat.number(95.3, 1))
        assertEquals("54", TileFormat.number(54.0, 0))
    }

    @Test
    fun `groups thousands with a thin non-breaking space`() {
        assertEquals("5${' '}450", TileFormat.number(5450.0, 0))
        assertEquals("18${' '}079", TileFormat.number(18079.0, 0))
    }

    @Test
    fun `keeps a negative sign in front of the grouped digits`() {
        assertEquals("-2,5", TileFormat.number(-2.5, 1))
    }

    @Test
    fun `returns null for a missing or non-finite value`() {
        assertNull(TileFormat.number(null, 1))
        assertNull(TileFormat.number(Double.NaN, 1))
    }

    @Test
    fun `formats a duration as hours and zero-padded minutes`() {
        assertEquals("5 h 48", TileFormat.duration(5.8))
        assertEquals("8 h 00", TileFormat.duration(8.0))
    }

    @Test
    fun `formats a relative hour as a clock time, never with a colon`() {
        assertEquals("0h44", TileFormat.clock(0.7333333))
        assertEquals("23h10", TileFormat.clock(-0.8333333))
        assertEquals("0h00", TileFormat.clock(0.0))
    }

    @Test
    fun `wraps a clock time that rounds up to the next hour or past midnight`() {
        // 23.9999 h -> arrondi à 24h00, qui redevient 0h00.
        assertEquals("0h00", TileFormat.clock(-0.0001))
    }

    @Test
    fun `formats a date in full french, without a leading zero on the day`() {
        assertEquals("23 juin 2025", TileFormat.date("2025-06-23"))
        assertEquals("1 janvier 2026", TileFormat.date("2026-01-01"))
    }

    @Test
    fun `returns a dash for a missing date`() {
        assertEquals("—", TileFormat.date(null))
    }
}
