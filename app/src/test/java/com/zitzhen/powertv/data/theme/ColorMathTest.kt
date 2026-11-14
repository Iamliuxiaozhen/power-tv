package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun `parses short and long hex colours`() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.parseHex("#F00"))
        assertEquals(0xFF006A67.toInt(), ColorMath.parseHex("#006A67"))
        assertEquals(0x80006A67.toInt(), ColorMath.parseHex("#80006A67"))
    }

    @Test
    fun `rejects malformed colours`() {
        assertNull(ColorMath.parseHex("006A67"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex(""))
    }

    @Test
    fun `encodes opaque and translucent colours`() {
        assertEquals("#006A67", ColorMath.toHex(0xFF006A67.toInt()))
        assertEquals("#80006A67", ColorMath.toHex(0x80006A67.toInt()))
    }

    @Test
    fun `blend interpolates alpha and channels`() {
        val blended = ColorMath.blend(ColorMath.BLACK, ColorMath.WHITE, 0.5f)
        assertEquals(0xFF808080.toInt(), blended)
    }

    @Test
    fun `contrast follows the WCAG ratio`() {
        assertEquals(21.0, ColorMath.contrast(ColorMath.BLACK, ColorMath.WHITE), 0.01)
        assertTrue(ColorMath.contrast(0xFF006A67.toInt(), ColorMath.WHITE) > 5.0)
        assertTrue(ColorMath.isDark(ColorMath.BLACK))
        assertFalse(ColorMath.isDark(ColorMath.WHITE))
    }

    @Test
    fun `best on colour picks the readable option`() {
        assertEquals(ColorMath.WHITE, ColorMath.bestOn(0xFF003735.toInt()))
        assertEquals(ColorMath.BLACK, ColorMath.bestOn(0xFFB4F1CE.toInt()))
    }

    @Test
    fun `hsl round trip keeps the colour`() {
        val colors = listOf(0xFF006A67.toInt(), 0xFFBA1A1A.toInt(), 0xFF7BD8C6.toInt())
        colors.forEach { color ->
            val hsl = ColorMath.rgbToHsl(color)
            assertEquals(color, ColorMath.hslToColor(hsl[0], hsl[1], hsl[2]))
        }
    }
}
