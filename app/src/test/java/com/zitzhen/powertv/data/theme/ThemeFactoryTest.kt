package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ThemeFactoryTest {

    @Test
    fun `builds accessible light and dark palettes from seeds`() {
        val seeds = listOf(
            0xFF006A67.toInt(),
            0xFF6750A4.toInt(),
            0xFFB3261E.toInt(),
            0xFFB8A000.toInt(),
            0xFF1F7A66.toInt(),
        )

        seeds.forEach { seed ->
            val pack = ThemeFactory.create("user.test", "Test", "tester", seed)
            val lightContrast = ColorMath.contrast(
                pack.light[ThemeColor.PRIMARY],
                pack.light[ThemeColor.SURFACE],
            )
            val darkContrast = ColorMath.contrast(
                pack.dark[ThemeColor.PRIMARY],
                pack.dark[ThemeColor.SURFACE],
            )
            assertTrue("light primary contrast for ${ColorMath.toHex(seed)}", lightContrast >= 4.5)
            assertTrue("dark primary contrast for ${ColorMath.toHex(seed)}", darkContrast >= 4.5)
            assertEquals(ThemeColor.entries.size, pack.light.colors.size)
            assertEquals(ThemeColor.entries.size, pack.dark.colors.size)
            assertTrue(pack.id.startsWith("user."))
        }
    }

    @Test
    fun `keeps the requested locale for the display name`() {
        val pack = ThemeFactory.create(
            "user.test",
            "Nord",
            null,
            0xFF88C0D0.toInt(),
            Locale.SIMPLIFIED_CHINESE,
        )

        assertEquals("Nord", pack.name(Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `generates safe unique ids`() {
        val first = ThemeFactory.newId("Midnight Forest!")
        val second = ThemeFactory.newId("Midnight Forest!")

        assertTrue(first.startsWith("user.midnight-forest."))
        assertTrue(first != second)
        ThemePackCodec.validateId(first)
    }

    @Test
    fun `generates ascii ids for non-latin names`() {
        val id = ThemeFactory.newId("午夜森林")

        ThemePackCodec.validateId(id)
        assertTrue(id.startsWith("user.theme."))
    }
}
