package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteDeriverTest {

    private val teal = 0xFF006A67.toInt()
    private val surface = 0xFFF5FAF9.toInt()

    @Test
    fun `fills every material role from two colours`() {
        val colors = ThemePaletteDeriver.complete(
            mapOf(ThemeColor.PRIMARY to teal, ThemeColor.SURFACE to surface),
            dark = false,
        )

        assertEquals(ThemeColor.entries.size, colors.size)
        assertEquals(teal, colors.getValue(ThemeColor.PRIMARY))
        assertTrue(ColorMath.contrast(colors.getValue(ThemeColor.ON_SURFACE), surface) >= 4.5)
    }

    @Test
    fun `keeps explicitly provided roles`() {
        val providedOnSurface = 0xFF112233.toInt()
        val colors = ThemePaletteDeriver.complete(
            mapOf(
                ThemeColor.PRIMARY to teal,
                ThemeColor.SURFACE to surface,
                ThemeColor.ON_SURFACE to providedOnSurface,
            ),
            dark = false,
        )

        assertEquals(providedOnSurface, colors.getValue(ThemeColor.ON_SURFACE))
    }

    @Test
    fun `derives readable text colours for both modes`() {
        val light = ThemePaletteDeriver.complete(
            mapOf(ThemeColor.PRIMARY to teal, ThemeColor.SURFACE to surface),
            dark = false,
        )
        val dark = ThemePaletteDeriver.autoDark(light)

        assertTrue(ColorMath.isDark(dark.getValue(ThemeColor.SURFACE)))
        assertTrue(ColorMath.contrast(dark.getValue(ThemeColor.PRIMARY), dark.getValue(ThemeColor.SURFACE)) >= 4.5)
        assertTrue(ColorMath.contrast(dark.getValue(ThemeColor.ON_SURFACE), dark.getValue(ThemeColor.SURFACE)) >= 4.5)
    }

    @Test
    fun `derived data viz accents stay visible`() {
        val colors = ThemePaletteDeriver.complete(
            mapOf(ThemeColor.PRIMARY to teal, ThemeColor.SURFACE to surface),
            dark = false,
        )
        val accents = ThemePaletteDeriver.dataViz(colors, dark = false)

        assertTrue(ColorMath.contrast(accents.loadLow, surface) >= 3.0)
        assertTrue(ColorMath.contrast(accents.loadHigh, surface) >= 3.0)
        assertTrue(ColorMath.alpha(accents.chartGrid) in 1..255)
    }

    @Test
    fun `auto dark can be built for a grayscale seed`() {
        val light = ThemePaletteDeriver.complete(
            mapOf(ThemeColor.PRIMARY to 0xFF555555.toInt(), ThemeColor.SURFACE to 0xFFF0F0F0.toInt()),
            dark = false,
        )
        val dark = ThemePaletteDeriver.autoDark(light)

        assertTrue(ColorMath.isDark(dark.getValue(ThemeColor.SURFACE)))
        assertTrue(ColorMath.contrast(dark.getValue(ThemeColor.ON_SURFACE), dark.getValue(ThemeColor.SURFACE)) >= 4.5)
    }
}
