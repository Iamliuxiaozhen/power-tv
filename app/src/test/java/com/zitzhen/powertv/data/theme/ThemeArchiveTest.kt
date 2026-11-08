package com.zitzhen.powertv.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeArchiveTest {

    private val themeJson = """
        {
          "schemaVersion": 1,
          "id": "user.archive-test",
          "name": "Archive Test",
          "schemes": {
            "light": { "primary": "#006A67", "surface": "#F5FAF9" }
          }
        }
    """.trimIndent()

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun tempDir(): File = Files.createTempDirectory("theme-archive-test").toFile()

    @Test
    fun `reads a theme archive and its assets`() {
        val dir = tempDir()
        val withBackdrop = themeJson.replace(
            "\"schemes\"",
            "\"backdrop\": { \"image\": \"assets/backdrop.webp\" },\n  \"schemes\"",
        )
        val bytes = zip(
            mapOf(
                "theme.json" to withBackdrop.toByteArray(),
                "assets/backdrop.webp" to byteArrayOf(1, 2, 3),
            ),
        )

        val imported = ThemeArchive.read(ByteArrayInputStream(bytes), dir)

        assertEquals("user.archive-test", imported.pack.id)
        assertEquals("assets/backdrop.webp", imported.pack.backdrop?.image)
        assertTrue(File(dir, "assets/backdrop.webp").isFile)
        imported.discard()
    }

    @Test
    fun `reads a bare json theme`() {
        val dir = tempDir()

        val imported = ThemeArchive.read(ByteArrayInputStream(themeJson.toByteArray()), dir)

        assertEquals("user.archive-test", imported.pack.id)
        imported.discard()
    }

    @Test
    fun `rejects path traversal entries`() {
        val dir = tempDir()
        val bytes = zip(
            mapOf(
                "../escaped.txt" to "nope".toByteArray(),
                "theme.json" to themeJson.toByteArray(),
            ),
        )

        assertThrows(ThemePackException::class.java) {
            ThemeArchive.read(ByteArrayInputStream(bytes), dir)
        }
        assertFalse(File(dir.parentFile, "escaped.txt").exists())
    }

    @Test
    fun `rejects archives without theme json`() {
        val dir = tempDir()
        val bytes = zip(mapOf("assets/backdrop.webp" to byteArrayOf(1)))

        assertThrows(ThemePackException::class.java) {
            ThemeArchive.read(ByteArrayInputStream(bytes), dir)
        }
    }

    @Test
    fun `rejects unsupported asset types`() {
        val dir = tempDir()
        val bytes = zip(
            mapOf(
                "theme.json" to themeJson.toByteArray(),
                "assets/payload.apk" to byteArrayOf(1),
            ),
        )

        assertThrows(ThemePackException::class.java) {
            ThemeArchive.read(ByteArrayInputStream(bytes), dir)
        }
    }

    @Test
    fun `rejects too many entries`() {
        val dir = tempDir()
        val entries = (0..ThemeArchive.MAX_ENTRIES).associate { index ->
            "file$index.txt" to byteArrayOf(1)
        } + mapOf("theme.json" to themeJson.toByteArray())

        assertThrows(ThemePackException::class.java) {
            ThemeArchive.read(ByteArrayInputStream(zip(entries)), dir)
        }
    }

    @Test
    fun `rejects an oversized theme json`() {
        val dir = tempDir()
        val oversized = themeJson + " ".repeat((ThemeArchive.MAX_JSON_BYTES + 1).toInt())
        val bytes = zip(mapOf("theme.json" to oversized.toByteArray()))

        assertThrows(ThemePackException::class.java) {
            ThemeArchive.read(ByteArrayInputStream(bytes), dir)
        }
    }
}
