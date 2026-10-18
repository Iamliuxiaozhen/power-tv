package com.zitzhen.powertv.data.proc

import org.junit.Assert.assertEquals
import org.junit.Test

class MemInfoParserTest {

    @Test
    fun `converts kB values to bytes`() {
        val parsed = MemInfoParser.parse(
            """
            MemTotal:        8000000 kB
            MemAvailable:    4000000 kB
            SwapTotal:             0 kB
            """.trimIndent(),
        )

        assertEquals(8_000_000L * 1024L, parsed["MemTotal"])
        assertEquals(4_000_000L * 1024L, parsed["MemAvailable"])
        assertEquals(0L, parsed["SwapTotal"])
    }

    @Test
    fun `skips malformed lines`() {
        val parsed = MemInfoParser.parse(
            """
            MemTotal: 1000 kB
            no separator here
            Broken: not-a-number kB
            HugePages_Total: 0
            """.trimIndent(),
        )

        assertEquals(mapOf("MemTotal" to 1_024_000L, "HugePages_Total" to 0L), parsed)
    }
}
