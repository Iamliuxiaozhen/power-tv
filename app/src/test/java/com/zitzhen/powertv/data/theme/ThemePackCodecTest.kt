package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ThemePackCodecTest {

    private val minimal = """
        {
          "schemaVersion": 1,
          "id": "user.sample-theme",
          "name": { "en": "Sample", "zh-CN": "示例" },
          "author": "tester",
          "version": "1.0.0",
          "schemes": {
            "light": { "primary": "#006A67", "surface": "#F5FAF9" }
          }
        }
    """.trimIndent()

    @Test
    fun `parses a minimal pack and derives missing roles`() {
        val pack = ThemePackCodec.parse(minimal)

        assertEquals("user.sample-theme", pack.id)
        assertEquals("Sample", pack.name(Locale.ENGLISH))
        assertEquals("示例", pack.name(Locale.SIMPLIFIED_CHINESE))
        assertEquals(ThemeColor.entries.size, pack.light.colors.size)
        assertEquals(ThemeColor.entries.size, pack.dark.colors.size)
        assertTrue(ColorMath.isDark(pack.dark[ThemeColor.SURFACE]))
        assertTrue(pack.warnings.isEmpty())
    }

    @Test
    fun `ignores unknown colour roles and reports them`() {
        val json = minimal.replace(
            "\"primary\": \"#006A67\"",
            "\"primary\": \"#006A67\", \"brandSparkle\": \"#FF00FF\"",
        )

        val pack = ThemePackCodec.parse(json)

        assertTrue(pack.warnings.any { it.contains("brandSparkle") })
    }

    @Test
    fun `rejects invalid colours`() {
        val json = minimal.replace("#006A67", "not-a-colour")

        val error = assertThrows(ThemePackException::class.java) { ThemePackCodec.parse(json) }
        assertTrue(error.message!!.contains("schemes.light.primary"))
    }

    @Test
    fun `rejects an unsupported schema version`() {
        val json = minimal.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")

        assertThrows(ThemePackException::class.java) { ThemePackCodec.parse(json) }
    }

    @Test
    fun `rejects unsafe ids`() {
        val json = minimal.replace("user.sample-theme", "../escape")

        assertThrows(ThemePackException::class.java) { ThemePackCodec.parse(json) }
    }

    @Test
    fun `accepts a partial dark scheme and fills the rest`() {
        val json = minimal.replace(
            "\"light\": { \"primary\": \"#006A67\", \"surface\": \"#F5FAF9\" }",
            """
            "light": { "primary": "#006A67", "surface": "#F5FAF9" },
            "dark": { "surface": "#000000" }
            """.trimIndent(),
        )

        val pack = ThemePackCodec.parse(json)

        assertEquals(0xFF000000.toInt(), pack.dark[ThemeColor.SURFACE])
        assertFalse(ColorMath.isDark(pack.dark[ThemeColor.PRIMARY]))
    }

    @Test
    fun `parses layout presets and drops unknown modules`() {
        val json = minimal.replace(
            "\"schemes\"",
            "\"layout\": { \"order\": [\"hero\", \"cpu\", \"not_a_module\"], \"visible\": [\"cpu\"] },\n  \"schemes\"",
        )

        val pack = ThemePackCodec.parse(json)

        assertEquals(listOf("hero", "cpu"), pack.layout?.order)
        assertEquals(listOf("cpu"), pack.layout?.visible)
        assertTrue(pack.warnings.any { it.contains("not_a_module") })
    }

    @Test
    fun `rejects backdrop paths outside assets`() {
        val json = minimal.replace(
            "\"schemes\"",
            "\"backdrop\": { \"image\": \"../secret.png\" },\n  \"schemes\"",
        )

        assertThrows(ThemePackException::class.java) { ThemePackCodec.parse(json) }
    }

    @Test
    fun `round trips through encode`() {
        val original = ThemePackCodec.parse(
            minimal.replace(
                "\"schemes\"",
                "\"backdrop\": { \"image\": \"assets/backdrop.webp\", \"dim\": 0.5 },\n  \"schemes\"",
            ),
        )

        val reencoded = ThemePackCodec.parse(ThemePackCodec.encode(original))

        assertEquals(original.id, reencoded.id)
        assertEquals(original.names, reencoded.names)
        assertEquals(original.light.colors, reencoded.light.colors)
        assertEquals(original.dark.colors, reencoded.dark.colors)
        assertEquals(original.light.dataViz, reencoded.light.dataViz)
        assertEquals(original.backdrop, reencoded.backdrop)
        assertNotNull(reencoded)
    }

    @Test
    fun `partial data viz is completed from the palette`() {
        val json = minimal.replace(
            "\"schemes\"",
            "\"dataViz\": { \"light\": { \"loadLow\": \"#112233\" } },\n  \"schemes\"",
        )

        val pack = ThemePackCodec.parse(json)

        assertEquals(0xFF112233.toInt(), pack.light.dataViz.loadLow)
        assertTrue(ColorMath.alpha(pack.light.dataViz.chartGrid) > 0)
    }

    @Test
    fun `sanitises long names`() {
        val longName = "x".repeat(200)
        val json = minimal.replace("\"Sample\"", "\"$longName\"")

        val pack = ThemePackCodec.parse(json)

        assertEquals(64, pack.names.getValue("en").length)
        assertTrue(pack.warnings.any { it.contains("name") })
    }
}
