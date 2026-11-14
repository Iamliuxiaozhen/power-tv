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
import java.util.zip.CRC32
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

    /** Writes a ZIP with two local entries sharing the same name (java.util.zip forbids this). */
    private fun zipWithDuplicateEntries(name: String, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        val entryName = name.toByteArray()
        val crc = CRC32().apply { update(bytes) }.value.toInt()
        repeat(2) {
            val offset = output.size()
            output.writeLe32(0x04034b50)
            output.writeLe16(20)
            output.writeLe16(0)
            output.writeLe16(0)
            output.writeLe16(0)
            output.writeLe16(0)
            output.writeLe32(crc)
            output.writeLe32(bytes.size)
            output.writeLe32(bytes.size)
            output.writeLe16(entryName.size)
            output.writeLe16(0)
            output.write(entryName)
            output.write(bytes)

            central.writeLe32(0x02014b50)
            central.writeLe16(20)
            central.writeLe16(20)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe32(crc)
            central.writeLe32(bytes.size)
            central.writeLe32(bytes.size)
            central.writeLe16(entryName.size)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe16(0)
            central.writeLe32(0)
            central.writeLe32(offset)
            central.write(entryName)
        }
        val centralOffset = output.size()
        output.write(central.toByteArray())
        output.writeLe32(0x06054b50)
        output.writeLe16(0)
        output.writeLe16(0)
        output.writeLe16(2)
        output.writeLe16(2)
        output.writeLe32(central.size())
        output.writeLe32(centralOffset)
        output.writeLe16(0)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        writeLe16(value and 0xFFFF)
        writeLe16((value ushr 16) and 0xFFFF)
    }

    @Test
    fun `rejects duplicate theme json entries`() {
        val dir = tempDir()
        val bytes = zipWithDuplicateEntries("theme.json", themeJson.toByteArray())

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
