package com.zitzhen.powertv.data.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessStatParserTest {

    @Test
    fun `parses normal process stat`() {
        val stat = """
            1234 (com.example.app) S 100 1 1 0 -1 4194560 100 0 0 0 12 34 0 0 20 0 10 0 98765 1000000 512
        """.trimIndent()

        val parsed = ProcessStatParser.parse(stat)!!

        assertEquals(1234, parsed.pid)
        assertEquals("com.example.app", parsed.command)
        assertEquals("S", parsed.state)
        assertEquals(100, parsed.parentPid)
        assertEquals(46L, parsed.cpuTicks)
        assertEquals(512L, parsed.residentPages)
        assertEquals(98_765L, parsed.startTimeTicks)
    }

    @Test
    fun `handles command names containing spaces and parentheses`() {
        val stat = "42 (worker (pool) one) R 1 1 1 0 -1 0 0 0 0 0 5 7 0 0 20 0 1 0 7 0 9"

        val parsed = ProcessStatParser.parse(stat)!!

        assertEquals("worker (pool) one", parsed.command)
        assertEquals(12L, parsed.cpuTicks)
    }

    @Test
    fun `rejects malformed stat`() {
        assertNull(ProcessStatParser.parse("not a stat line"))
        assertNull(ProcessStatParser.parse("123 (short) S 1 2"))
    }
}
