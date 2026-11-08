package com.zitzhen.powertv.data.theme

import android.content.Context
import android.content.res.Configuration
import com.zitzhen.powertv.data.settings.DashboardModule
import com.zitzhen.powertv.data.settings.ModuleSettingsRepository
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Owns installed theme packs and the active selection.
 *
 * Built-in packs live in the `assets/themes` directory; imported packs live under
 * `filesDir/themes/<id>/`. Nothing is ever executed from a pack — only JSON
 * and image files are copied.
 */
class ThemeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val themesDir = File(appContext.filesDir, "themes")
    private var builtInCache: List<ThemePack>? = null

    /** Bumped whenever the active pack or the installed list changes. */
    var revision: Int = 0
        private set

    fun all(): List<ThemePack> {
        val builtIns = builtIns()
        val installed = installed(builtIns.map { it.id }.toSet())
        return builtIns + installed
    }

    fun find(id: String): ThemePack? = all().firstOrNull { it.id == id }

    fun activeId(): String {
        val stored = prefs.getString(KEY_ACTIVE, null)
        if (stored != null && find(stored) != null) return stored
        return builtIns().firstOrNull()?.id ?: DEFAULT_ID
    }

    fun activePack(): ThemePack =
        find(activeId()) ?: builtIns().firstOrNull() ?: fallbackPack()

    fun activeTheme(): ResolvedTheme {
        val pack = activePack()
        return resolve(pack, isDark(appContext))
    }

    fun resolve(pack: ThemePack, dark: Boolean): ResolvedTheme {
        val scheme = if (dark) pack.dark else pack.light
        return ResolvedTheme(
            pack = pack,
            dark = dark,
            colors = scheme.colors,
            dataViz = scheme.dataViz,
            typography = pack.typography,
            shape = pack.shape,
            backdrop = pack.backdrop,
            layout = pack.layout,
        )
    }

    fun setActive(id: String): Boolean {
        if (find(id) == null) return false
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        revision += 1
        return true
    }

    /**
     * Copies [sourceDir] into the app's private theme storage and writes a
     * canonical `theme.json`. Returns the installed pack.
     */
    fun install(pack: ThemePack, sourceDir: File? = null): ThemePack {
        if (pack.builtIn || pack.id.startsWith(BUILT_IN_PREFIX)) {
            throw ThemePackException("built-in theme packs cannot be replaced")
        }
        ThemePackCodec.validateId(pack.id)
        themesDir.mkdirs()
        val destination = File(themesDir, pack.id)
        val rootPath = themesDir.canonicalPath + File.separator
        if (!destination.canonicalPath.startsWith(rootPath)) {
            throw ThemePackException("invalid theme id")
        }

        val temporary = File(themesDir, ".tmp-${UUID.randomUUID()}")
        try {
            if (!temporary.mkdirs()) throw ThemePackException("unable to create theme storage")
            if (sourceDir != null && sourceDir.isDirectory) {
                sourceDir.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                    if (relative == ThemePackCodec.THEME_FILE) return@forEach
                    if (!relative.startsWith("assets/")) return@forEach
                    val target = File(temporary, relative)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }
            val stored = pack.copy(builtIn = false, storageDir = destination)
            File(temporary, ThemePackCodec.THEME_FILE)
                .writeText(ThemePackCodec.encode(stored), Charsets.UTF_8)
            if (destination.exists() && !destination.deleteRecursively()) {
                throw ThemePackException("unable to replace the existing theme pack")
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyRecursively(destination, overwrite = true)
                temporary.deleteRecursively()
            }
            revision += 1
            return stored.copy(storageDir = destination)
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
    }

    fun delete(pack: ThemePack): Boolean {
        if (pack.builtIn || pack.id.startsWith(BUILT_IN_PREFIX)) return false
        val directory = File(themesDir, pack.id)
        val rootPath = themesDir.canonicalPath + File.separator
        if (!directory.canonicalPath.startsWith(rootPath)) return false
        val deleted = !directory.exists() || directory.deleteRecursively()
        if (deleted && activeId() == pack.id) {
            prefs.edit().putString(KEY_ACTIVE, builtIns().firstOrNull()?.id ?: DEFAULT_ID).apply()
        }
        if (deleted) revision += 1
        return deleted
    }

    fun export(pack: ThemePack, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            val json = ThemePackCodec.encode(pack).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(ThemePackCodec.THEME_FILE))
            zip.write(json)
            zip.closeEntry()

            val root = pack.storageDir ?: return@use
            val assets = File(root, "assets")
            if (!assets.isDirectory) return@use
            assets.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (!relative.startsWith("assets/")) return@forEach
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun applyLayout(pack: ThemePack, moduleSettings: ModuleSettingsRepository): Boolean {
        val layout = pack.layout ?: return false
        val requested = layout.order.mapNotNull { DashboardModule.fromKey(it) }
        if (requested.isEmpty()) return false
        val order = requested + DashboardModule.DEFAULT_ORDER.filter { it !in requested }
        val visible = layout.visible
            ?.mapNotNull { DashboardModule.fromKey(it) }
            ?.toSet()
            ?: moduleSettings.loadVisible()
        moduleSettings.save(order, visible)
        return true
    }

    private fun builtIns(): List<ThemePack> {
        builtInCache?.let { return it }
        val loaded = mutableListOf<ThemePack>()
        val names = try {
            appContext.assets.list(ASSET_DIRECTORY).orEmpty()
        } catch (_: Exception) {
            emptyArray()
        }
        names.filter { it.endsWith(".json") }.sorted().forEach { name ->
            try {
                val json = appContext.assets.open("$ASSET_DIRECTORY/$name")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                loaded += ThemePackCodec.parse(json, builtIn = true)
            } catch (_: Exception) {
                // A broken built-in must not prevent the app from starting.
            }
        }
        val result = loaded.sortedWith(
            compareByDescending<ThemePack> { it.id == DEFAULT_ID }.thenBy { it.id },
        )
        builtInCache = result
        return result
    }

    private fun installed(existingIds: Set<String>): List<ThemePack> {
        if (!themesDir.isDirectory) return emptyList()
        val loaded = mutableListOf<ThemePack>()
        themesDir.listFiles()?.forEach { directory ->
            if (!directory.isDirectory || directory.name.startsWith(".")) return@forEach
            val json = File(directory, ThemePackCodec.THEME_FILE)
            if (!json.isFile) return@forEach
            try {
                val pack = ThemePackCodec.parse(
                    json.readText(Charsets.UTF_8),
                    builtIn = false,
                    storageDir = directory,
                )
                if (pack.id !in existingIds && !pack.id.startsWith(BUILT_IN_PREFIX)) {
                    loaded += pack
                }
            } catch (_: Exception) {
                // Keep going: one corrupt folder must not hide the others.
            }
        }
        return loaded.sortedBy { it.name() }
    }

    private fun fallbackPack(): ThemePack = ThemeFactory.create(
        DEFAULT_ID,
        "Teal",
        "power-tv",
        0xFF006A67.toInt(),
    ).copy(builtIn = true)

    private fun isDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val PREFS_NAME = "theme_packs"
        const val KEY_ACTIVE = "active_id"
        const val ASSET_DIRECTORY = "themes"
        const val BUILT_IN_PREFIX = "builtin."
        const val DEFAULT_ID = "builtin.teal"
    }
}
