package com.zitzhen.powertv.data.theme

import com.zitzhen.powertv.data.settings.DashboardModule
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class ThemePackException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Reads and writes the `.ptheme` / `theme.json` format.
 *
 * The parser is deliberately forgiving about optional fields but strict about
 * anything that could be dangerous (ids, asset paths) or impossible to render
 * (colour syntax, schema version).
 */
object ThemePackCodec {

    const val SCHEMA_VERSION = 1
    const val THEME_FILE = "theme.json"

    private const val MAX_NAME_LENGTH = 64
    private const val MAX_AUTHOR_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 512
    private const val MAX_LOCALES = 8
    private const val MAX_LAYOUT_ENTRIES = 32

    private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$")
    private val VERSION_PATTERN = Regex("^\\d+(\\.\\d+){0,2}$")

    private val FONT_FAMILIES = setOf(
        "sans-serif",
        "sans-serif-medium",
        "sans-serif-light",
        "sans-serif-thin",
        "sans-serif-condensed",
        "serif",
        "monospace",
    )

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun validateId(id: String) {
        if (!ID_PATTERN.matches(id) || id.contains("..")) {
            throw ThemePackException(
                "id must be 3-64 characters using letters, digits, '.', '_' or '-'",
            )
        }
    }

    fun parse(json: String, builtIn: Boolean = false, storageDir: File? = null): ThemePack {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw ThemePackException("not valid JSON: ${error.message}", error)
        }

        val warnings = mutableListOf<String>()

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion != SCHEMA_VERSION) {
            throw ThemePackException(
                "unsupported schemaVersion $schemaVersion (this app supports $SCHEMA_VERSION)",
            )
        }

        val id = root.optString("id", "").trim()
        validateId(id)

        val names = parseLocalizedText(root.opt("name"), "name", MAX_NAME_LENGTH, warnings)
        if (names.isEmpty()) throw ThemePackException("name is required")
        val descriptions = parseLocalizedText(
            root.opt("description"),
            "description",
            MAX_DESCRIPTION_LENGTH,
            warnings,
        )

        val author = root.optString("author", "").trim()
            .takeIf { it.isNotEmpty() }
            ?.let {
                if (it.length > MAX_AUTHOR_LENGTH) {
                    warnings += "author is longer than $MAX_AUTHOR_LENGTH characters; truncated"
                    it.take(MAX_AUTHOR_LENGTH)
                } else {
                    it
                }
            }

        val version = root.optString("version", "1.0.0").trim()
            .takeIf { it.isNotEmpty() }
            ?: "1.0.0"
        if (!VERSION_PATTERN.matches(version)) {
            warnings += "version '$version' is not a simple semantic version"
        }

        val minAppVersion = root.optString("minAppVersion", "").trim()
            .takeIf { it.isNotEmpty() }
            ?.also {
                if (!VERSION_PATTERN.matches(it)) {
                    warnings += "minAppVersion '$it' is not a simple semantic version"
                }
            }

        val seed = root.optString("seed", "").trim().takeIf { it.isNotEmpty() }
            ?.let { raw ->
                ColorMath.parseHex(raw)?.let(ColorMath::toHex) ?: run {
                    warnings += "seed '$raw' is not a valid colour and was ignored"
                    null
                }
            }

        val schemes = root.optJSONObject("schemes")
            ?: throw ThemePackException("schemes is required")
        val lightObject = schemes.optJSONObject("light")
            ?: throw ThemePackException("schemes.light is required")
        val lightRaw = parseColors(lightObject, "light", warnings)
        if (ThemeColor.PRIMARY !in lightRaw) {
            throw ThemePackException("schemes.light.primary is required")
        }
        if (ThemeColor.SURFACE !in lightRaw && ThemeColor.BACKGROUND !in lightRaw) {
            throw ThemePackException("schemes.light.surface is required")
        }
        val lightColors = ThemePaletteDeriver.complete(lightRaw, dark = false)

        val darkObject = schemes.optJSONObject("dark")
        val darkRaw = if (darkObject == null) {
            emptyMap()
        } else {
            parseColors(darkObject, "dark", warnings)
        }
        val darkColors = if (darkObject == null || darkRaw.isEmpty()) {
            ThemePaletteDeriver.autoDark(lightColors)
        } else {
            val base = ThemePaletteDeriver.autoDark(lightColors)
            ThemePaletteDeriver.complete(base + darkRaw, dark = true)
        }

        return ThemePack(
            schemaVersion = schemaVersion,
            id = id,
            names = names,
            descriptions = descriptions,
            author = author,
            version = version,
            minAppVersion = minAppVersion,
            seed = seed,
            light = ThemeScheme(
                colors = lightColors,
                dataViz = resolveDataViz(root, "light", lightColors, dark = false, warnings),
            ),
            dark = ThemeScheme(
                colors = darkColors,
                dataViz = resolveDataViz(root, "dark", darkColors, dark = true, warnings),
            ),
            typography = parseTypography(root.optJSONObject("typography"), warnings),
            shape = parseShape(root.optJSONObject("shape"), warnings),
            backdrop = parseBackdrop(root.optJSONObject("backdrop"), storageDir, warnings),
            layout = parseLayout(root.optJSONObject("layout"), warnings),
            builtIn = builtIn,
            storageDir = storageDir,
            warnings = warnings,
        )
    }

    fun encode(pack: ThemePack): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("id", pack.id)
        putLocalizedText(root, "name", pack.names)
        if (pack.descriptions.isNotEmpty()) putLocalizedText(root, "description", pack.descriptions)
        pack.author?.let { root.put("author", it) }
        root.put("version", pack.version)
        pack.minAppVersion?.let { root.put("minAppVersion", it) }
        pack.seed?.let { root.put("seed", it) }

        root.put("schemes", JSONObject().apply {
            put("light", encodeColors(pack.light.colors))
            put("dark", encodeColors(pack.dark.colors))
        })
        root.put("dataViz", JSONObject().apply {
            put("light", encodeDataViz(pack.light.dataViz))
            put("dark", encodeDataViz(pack.dark.dataViz))
        })

        pack.typography?.let { typography ->
            val obj = JSONObject()
            typography.metricFont?.let { obj.put("metricFont", it) }
            typography.monoFont?.let { obj.put("monoFont", it) }
            if (obj.length() > 0) root.put("typography", obj)
        }
        pack.shape?.cardRadiusDp?.let { radius ->
            root.put("shape", JSONObject().put("cardRadiusDp", radius.toDouble()))
        }
        pack.backdrop?.let { backdrop ->
            root.put(
                "backdrop",
                JSONObject()
                    .put("image", backdrop.image)
                    .put("dim", backdrop.dim.toDouble()),
            )
        }
        pack.layout?.let { layout ->
            val obj = JSONObject()
            obj.put("order", JSONArray(layout.order))
            layout.visible?.let { obj.put("visible", JSONArray(it)) }
            root.put("layout", obj)
        }

        return root.toString(2)
    }

    private fun parseColors(
        obj: JSONObject,
        mode: String,
        warnings: MutableList<String>,
    ): Map<ThemeColor, Int> {
        val colors = mutableMapOf<ThemeColor, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val role = ThemeColor.fromKey(key)
            if (role == null) {
                warnings += "unknown colour role '$key' in schemes.$mode"
                continue
            }
            val raw = obj.optString(key, "")
            val color = ColorMath.parseHex(raw)
                ?: throw ThemePackException(
                    "schemes.$mode.$key is not a valid colour (expected #RGB, #RRGGBB or #AARRGGBB): '$raw'",
                )
            colors[role] = color
        }
        return colors
    }

    private fun resolveDataViz(
        root: JSONObject,
        mode: String,
        colors: Map<ThemeColor, Int>,
        dark: Boolean,
        warnings: MutableList<String>,
    ): DataVizColors {
        val derived = ThemePaletteDeriver.dataViz(colors, dark, null)
        val modeObject = root.optJSONObject("dataViz")?.optJSONObject(mode) ?: return derived
        fun read(key: String): Int? {
            if (!modeObject.has(key)) return null
            val raw = modeObject.optString(key, "")
            return ColorMath.parseHex(raw) ?: throw ThemePackException(
                "dataViz.$mode.$key is not a valid colour: '$raw'",
            )
        }
        return DataVizColors(
            loadLow = read("loadLow") ?: derived.loadLow,
            loadMedium = read("loadMedium") ?: derived.loadMedium,
            loadHigh = read("loadHigh") ?: derived.loadHigh,
            chartGrid = read("chartGrid") ?: derived.chartGrid,
            chartFill = read("chartFill") ?: derived.chartFill,
            liveDot = read("liveDot") ?: derived.liveDot,
        ).also {
            if (modeObject.length() > 0 && listOf("loadLow", "loadMedium", "loadHigh", "chartGrid", "chartFill", "liveDot")
                    .any { key -> !modeObject.has(key) }
            ) {
                warnings += "dataViz.$mode is incomplete; missing accents are derived"
            }
        }
    }

    private fun parseTypography(
        obj: JSONObject?,
        warnings: MutableList<String>,
    ): ThemeTypography? {
        if (obj == null) return null

        fun readFont(key: String): String? {
            val value = obj.optString(key, "").trim()
            if (value.isEmpty()) return null
            if (value !in FONT_FAMILIES) {
                warnings += "typography.$key uses unsupported font '$value'"
                return null
            }
            return value
        }

        val metric = readFont("metricFont")
        val mono = readFont("monoFont")
        return if (metric == null && mono == null) null else ThemeTypography(metric, mono)
    }

    private fun parseShape(obj: JSONObject?, warnings: MutableList<String>): ThemeShape? {
        if (obj == null || !obj.has("cardRadiusDp")) return null
        val raw = obj.optDouble("cardRadiusDp", Double.NaN)
        if (raw.isNaN() || raw < 0 || raw > 32) {
            warnings += "shape.cardRadiusDp must be between 0 and 32; ignored"
            return null
        }
        return ThemeShape(raw.toFloat())
    }

    private fun parseBackdrop(
        obj: JSONObject?,
        storageDir: File?,
        warnings: MutableList<String>,
    ): ThemeBackdrop? {
        if (obj == null) return null
        val image = obj.optString("image", "").trim()
        if (image.isEmpty()) return null
        val normalized = image.replace('\\', '/')
        val segments = normalized.split('/')
        val extension = normalized.substringAfterLast('.', "").lowercase()
        if (!normalized.startsWith("assets/") ||
            segments.any { it.isEmpty() || it == "." || it == ".." } ||
            extension !in IMAGE_EXTENSIONS
        ) {
            throw ThemePackException(
                "backdrop.image must be an image under assets/ (png, jpg or webp): '$image'",
            )
        }
        if (storageDir != null && !File(storageDir, normalized).isFile) {
            warnings += "backdrop image '$normalized' is missing from the archive"
        }
        val dim = obj.optDouble("dim", 0.35).toFloat()
        if (dim.isNaN()) {
            warnings += "backdrop.dim is not a number; using 0.35"
        }
        return ThemeBackdrop(normalized, dim.coerceIn(0f, 0.8f))
    }

    private fun parseLayout(obj: JSONObject?, warnings: MutableList<String>): ThemeLayout? {
        if (obj == null) return null
        val order = readLayoutKeys(obj.optJSONArray("order"), "order", warnings)
        if (order.isEmpty()) {
            warnings += "layout.order is empty; layout preset ignored"
            return null
        }
        val visible = readLayoutKeys(obj.optJSONArray("visible"), "visible", warnings)
        return ThemeLayout(
            order = order,
            visible = visible.takeIf { it.isNotEmpty() },
        )
    }

    private fun readLayoutKeys(
        array: JSONArray?,
        field: String,
        warnings: MutableList<String>,
    ): List<String> {
        if (array == null) return emptyList()
        val keys = mutableListOf<String>()
        for (index in 0 until minOf(array.length(), MAX_LAYOUT_ENTRIES)) {
            val key = array.optString(index, "").trim()
            if (key.isEmpty()) continue
            if (DashboardModule.fromKey(key) == null) {
                warnings += "layout.$field contains unknown module '$key'"
                continue
            }
            if (key !in keys) keys += key
        }
        if (array.length() > MAX_LAYOUT_ENTRIES) {
            warnings += "layout.$field is limited to $MAX_LAYOUT_ENTRIES modules"
        }
        return keys
    }

    private fun parseLocalizedText(
        value: Any?,
        field: String,
        maxLength: Int,
        warnings: MutableList<String>,
    ): Map<String, String> {
        if (value == null || value == JSONObject.NULL) return emptyMap()
        val result = linkedMapOf<String, String>()
        when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }?.let { result["default"] = it }
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val text = value.optString(key, "").trim()
                    if (text.isEmpty()) continue
                    if (result.size >= MAX_LOCALES) {
                        warnings += "$field has more than $MAX_LOCALES localisations; extra values ignored"
                        break
                    }
                    result[key] = text
                }
            }
            else -> throw ThemePackException("$field must be a string or an object of locale strings")
        }
        val tooLong = result.filterValues { it.length > maxLength }
        if (tooLong.isNotEmpty()) {
            warnings += "$field values are limited to $maxLength characters; truncated"
            tooLong.keys.forEach { key -> result[key] = result.getValue(key).take(maxLength) }
        }
        return result
    }

    private fun putLocalizedText(root: JSONObject, field: String, values: Map<String, String>) {
        if (values.size == 1 && values.containsKey("default")) {
            root.put(field, values.getValue("default"))
        } else {
            root.put(field, JSONObject().apply { values.forEach { (key, text) -> put(key, text) } })
        }
    }

    private fun encodeColors(colors: Map<ThemeColor, Int>): JSONObject {
        val obj = JSONObject()
        ThemeColor.entries.forEach { role ->
            colors[role]?.let { obj.put(role.key, ColorMath.toHex(it)) }
        }
        return obj
    }

    private fun encodeDataViz(colors: DataVizColors): JSONObject = JSONObject()
        .put("loadLow", ColorMath.toHex(colors.loadLow))
        .put("loadMedium", ColorMath.toHex(colors.loadMedium))
        .put("loadHigh", ColorMath.toHex(colors.loadHigh))
        .put("chartGrid", ColorMath.toHex(colors.chartGrid))
        .put("chartFill", ColorMath.toHex(colors.chartFill))
        .put("liveDot", ColorMath.toHex(colors.liveDot))
}
