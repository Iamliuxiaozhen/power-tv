package com.zitzhen.powertv.data.theme

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Opens a SAF/content URI and turns it into an [ImportedTheme] in the cache. */
class ThemeImporter(private val context: Context) {

    fun import(uri: Uri): ImportedTheme {
        val directory = File(context.cacheDir, "theme-import-${UUID.randomUUID()}").apply { mkdirs() }
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw ThemePackException("unable to open the selected file: ${error.message}", error)
        } ?: run {
            directory.deleteRecursively()
            throw ThemePackException("unable to open the selected file")
        }
        return stream.use { ThemeArchive.read(it, directory) }
    }
}
