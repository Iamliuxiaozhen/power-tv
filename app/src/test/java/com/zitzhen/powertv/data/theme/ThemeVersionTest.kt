package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeVersionTest {

    @Test
    fun `compares numeric versions`() {
        assertTrue(ThemeVersion.isNewerThan("1.5.0", "1.4.0"))
        assertTrue(ThemeVersion.isNewerThan("1.4.1", "1.4"))
        assertTrue(ThemeVersion.isNewerThan("2", "1.9.9"))
        assertFalse(ThemeVersion.isNewerThan("1.4.0", "1.4.0"))
        assertFalse(ThemeVersion.isNewerThan("1.3.9", "1.4.0"))
        assertFalse(ThemeVersion.isNewerThan(null, "1.4.0"))
    }
}
