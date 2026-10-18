package com.zitzhen.powertv.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuSysfsParserTest {

    @Test
    fun `parses adreno busy pair`() {
        assertEquals(25f, GpuSysfsParser.parseBusyPair("250 1000"))
        assertNull(GpuSysfsParser.parseBusyPair("0 0"))
        assertNull(GpuSysfsParser.parseBusyPair("not a number"))
    }

    @Test
    fun `parses percentage formats`() {
        assertEquals(42f, GpuSysfsParser.parsePercentage("42"))
        assertEquals(42f, GpuSysfsParser.parsePercentage("42 %"))
        assertEquals(50f, GpuSysfsParser.parsePercentage("0.5"))
        assertEquals(100f, GpuSysfsParser.parsePercentage("150"))
    }

    @Test
    fun `normalizes clocks to megahertz`() {
        assertEquals(578, GpuSysfsParser.toMhz(578_000_000))
        assertEquals(578, GpuSysfsParser.toMhz(578_000))
        assertEquals(578, GpuSysfsParser.toMhz(578))
        assertNull(GpuSysfsParser.toMhz(null))
    }
}
