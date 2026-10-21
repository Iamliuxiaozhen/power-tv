package com.zitzhen.powertv.data.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessTableCodecTest {

    @Test
    fun `round trips known and unknown uids`() {
        val records = listOf(
            ProcRecord(1, 1000, "S", 42, 900, 1234, "system_server"),
            ProcRecord(2, null, "R", 0, 5, 6789, "kworker"),
        )

        assertEquals(records, ProcessTableCodec.decode(ProcessTableCodec.encode(records)))
    }

    @Test
    fun `decodes empty table`() {
        assertTrue(ProcessTableCodec.decode("").isEmpty())
    }

    @Test
    fun `skips malformed lines`() {
        val text = buildString {
            append("11\t2000\tS\t3\t10\t20\tsh\n")
            append("broken\n")
            append("12\t-1\tR\tnot-a-number\t10\t20\tapp\n")
            append("13\t-1\tR\t1\t2\t3\t\n")
            append("14\t-1\tR\t1\t2\t3\tapp\n")
        }

        val records = ProcessTableCodec.decode(text)

        assertEquals(listOf(11, 14), records.map { it.pid })
        assertEquals(listOf(2000, null), records.map { it.uid })
    }

    @Test
    fun `sanitizes separators in names`() {
        val encoded = ProcessTableCodec.encode(
            listOf(ProcRecord(1, 0, "S", 1, 1, 1, "bad\tname\nwith\rbreaks")),
        )

        val decoded = ProcessTableCodec.decode(encoded).single()
        assertEquals("bad name with breaks", decoded.name)
    }
}
