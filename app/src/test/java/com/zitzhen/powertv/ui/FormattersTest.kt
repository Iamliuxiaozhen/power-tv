package com.zitzhen.powertv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formats percentages and missing values`() {
        assertEquals("42%", Formatters.percent(42.4f))
        assertEquals("42.4%", Formatters.percent(42.4f, decimals = 1))
        assertEquals("—", Formatters.percent(null))
    }

    @Test
    fun `formats binary byte sizes`() {
        assertEquals("512 MB", Formatters.bytes(512L * 1024 * 1024))
        assertEquals("1.50 GB", Formatters.bytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("0 B", Formatters.bytes(0))
    }

    @Test
    fun `formats clock frequencies`() {
        assertEquals("2.40 GHz", Formatters.frequency(2_400))
        assertEquals("800 MHz", Formatters.frequency(800))
        assertEquals("—", Formatters.frequency(null))
    }

    @Test
    fun `formats uptime at useful precision`() {
        assertEquals("2m", Formatters.uptime(125_000))
        assertEquals("3h 5m", Formatters.uptime((3 * 60 + 5) * 60_000L))
        assertEquals("1d 2h", Formatters.uptime((26 * 60) * 60_000L))
    }

    @Test
    fun `formats current with discharge sign`() {
        assertEquals("−420 mA", Formatters.current(-420))
        assertEquals("85 mA", Formatters.current(85))
        assertEquals("—", Formatters.current(null))
    }
}
