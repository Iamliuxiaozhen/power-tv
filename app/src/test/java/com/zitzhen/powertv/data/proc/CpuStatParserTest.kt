package com.zitzhen.powertv.data.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuStatParserTest {

    private val sample = """
        cpu  100 0 100 800 0 0 0 0 0 0
        cpu0 50 0 50 400 0 0 0 0 0 0
        cpu1 50 0 50 400 0 0 0 0 0 0
        intr 12345 0 0
    """.trimIndent()

    @Test
    fun `parses aggregate and per-core totals`() {
        val parsed = CpuStatParser.parse(sample)

        assertEquals(setOf("cpu", "cpu0", "cpu1"), parsed.keys)
        assertEquals(CpuStatParser.CpuTimes(total = 1_000, idle = 800), parsed["cpu"])
        assertEquals(CpuStatParser.CpuTimes(total = 500, idle = 400), parsed["cpu0"])
    }

    @Test
    fun `idle time includes iowait`() {
        val parsed = CpuStatParser.parse("cpu 10 0 10 70 5 0 0 0")

        assertEquals(75, parsed.getValue("cpu").idle)
    }

    @Test
    fun `ignores malformed and non-cpu lines`() {
        val parsed = CpuStatParser.parse(
            """
            cpu 1 2 3
            cpufreq 1 2 3 4
            cpu0 1 1 1 1 1 1 1 1
            """.trimIndent(),
        )

        assertEquals(setOf("cpu0"), parsed.keys)
    }

    @Test
    fun `computes busy percentage from deltas`() {
        val before = CpuStatParser.CpuTimes(total = 1_000, idle = 800)
        val after = CpuStatParser.CpuTimes(total = 2_000, idle = 1_500)

        assertEquals(30f, CpuStatParser.usage(before, after))
    }

    @Test
    fun `returns null without a usable delta`() {
        val sample = CpuStatParser.CpuTimes(total = 1_000, idle = 800)

        assertNull(CpuStatParser.usage(previous = null, current = sample))
        assertNull(CpuStatParser.usage(previous = sample, current = sample))
    }
}
