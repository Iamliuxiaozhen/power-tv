package com.zitzhen.powertv.data.theme

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

/** A theme pack that has been extracted to a temporary directory. */
class ImportedTheme(
    val pack: ThemePack,
    val directory: File,
) {
    fun discard() {
        directory.deleteRecursively()
    }
}

/**
 * Reads a `.ptheme` archive (zip) or a bare `theme.json`.
 *
 * The reader never trusts entry names or declared sizes: extraction is
 * restricted to `theme.json` plus image files under `assets/`, every byte is
 * counted, and the destination is verified to stay inside [directory].
 */
object ThemeArchive {

    const val MAX_ENTRIES = 64
    const val MAX_TOTAL_BYTES = 8L * 1024 * 1024
    const val MAX_JSON_BYTES = 256L * 1024
    const val MAX_ASSET_BYTES = 4L * 1024 * 1024

    private val ASSET_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    private val PREVIEW_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun read(input: InputStream, directory: File): ImportedTheme {
        if (!directory.exists() && !directory.mkdirs()) {
            throw ThemePackException("unable to create a temporary directory")
        }
        try {
            val stream = PushbackInputStream(BufferedInputStream(input), 4)
            val magic = ByteArray(4)
            val count = stream.read(magic)
            stream.unread(magic, 0, count.coerceAtLeast(0))
            val isZip = count >= 4 &&
                magic[0] == 'P'.code.toByte() &&
                magic[1] == 'K'.code.toByte() &&
                magic[2] == 3.toByte() &&
                magic[3] == 4.toByte()
            if (isZip) readZip(stream, directory) else readJson(stream, directory)
            val json = File(directory, ThemePackCodec.THEME_FILE)
            if (!json.isFile) throw ThemePackException("theme.json is missing")
            val pack = ThemePackCodec.parse(
                json.readText(Charsets.UTF_8),
                builtIn = false,
                storageDir = directory,
            )
            pack.backdrop?.let { backdrop ->
                if (!File(directory, backdrop.image).isFile) {
                    throw ThemePackException("backdrop image is missing: ${backdrop.image}")
                }
            }
            return ImportedTheme(pack, directory)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun readJson(input: InputStream, directory: File) {
        val bytes = readLimited(input, MAX_JSON_BYTES, "theme.json")
        File(directory, ThemePackCodec.THEME_FILE).writeBytes(bytes)
    }

    private fun readZip(input: InputStream, directory: File) {
        var entries = 0
        var total = 0L
        var sawTheme = false
        val zip = ZipInputStream(input)
        zip.use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                entries += 1
                if (entries > MAX_ENTRIES) {
                    throw ThemePackException("theme archive has more than $MAX_ENTRIES entries")
                }
                if (entry.isDirectory) continue

                val name = entry.name.replace('\\', '/')
                validateEntryName(name)
                val segments = name.split('/')
                val isTheme = name == ThemePackCodec.THEME_FILE
                val isAsset = segments.firstOrNull() == "assets" && segments.size > 1
                val isPreview = segments.size == 1 &&
                    name.substringAfterLast('.', "").lowercase() in PREVIEW_EXTENSIONS &&
                    name.startsWith("preview.")
                if (!isTheme && !isAsset && !isPreview) {
                    total += skipLimited(stream, total)
                    continue
                }
                if (isPreview) {
                    total += skipLimited(stream, total)
                    continue
                }
                if (isAsset) {
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension !in ASSET_EXTENSIONS) {
                        throw ThemePackException("unsupported asset type: $name")
                    }
                }
                if (isTheme) {
                    if (sawTheme) throw ThemePackException("theme.json appears more than once")
                    sawTheme = true
                }

                val target = File(directory, name)
                val rootPath = directory.canonicalPath + File.separator
                if (!target.canonicalPath.startsWith(rootPath)) {
                    throw ThemePackException("archive entry escapes the pack directory: $name")
                }
                target.parentFile?.mkdirs()

                val limit = if (isTheme) MAX_JSON_BYTES else MAX_ASSET_BYTES
                FileOutputStream(target).use { output ->
                    total += copyLimited(stream, output, limit, name, total)
                }
                if (total > MAX_TOTAL_BYTES) tooLarge()
            }
        }
        if (!sawTheme) throw ThemePackException("theme.json is missing")
    }

    private fun validateEntryName(name: String) {
        if (name.isBlank() || name.startsWith("/") || name.contains('\u0000')) {
            throw ThemePackException("invalid archive entry name")
        }
        if (name.split('/').any { it == ".." || it == "." || it.isEmpty() }) {
            throw ThemePackException("invalid archive entry name: $name")
        }
        if (name.length > 128) throw ThemePackException("archive entry name is too long")
    }

    private fun copyLimited(
        input: InputStream,
        output: FileOutputStream,
        limit: Long,
        name: String,
        alreadyRead: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            written += read
            if (written > limit) {
                throw ThemePackException("$name is larger than the ${limit / 1024} KiB limit")
            }
            if (alreadyRead + written > MAX_TOTAL_BYTES) tooLarge()
            output.write(buffer, 0, read)
        }
        return written
    }

    private fun skipLimited(input: InputStream, alreadyRead: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var skipped = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            skipped += read
            if (alreadyRead + skipped > MAX_TOTAL_BYTES) tooLarge()
        }
        return skipped
    }

    private fun readLimited(input: InputStream, limit: Long, name: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) {
                throw ThemePackException("$name is larger than the ${limit / 1024} KiB limit")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun tooLarge(): Nothing =
        throw ThemePackException("theme archive expands to more than ${MAX_TOTAL_BYTES / 1024 / 1024} MiB")
}
