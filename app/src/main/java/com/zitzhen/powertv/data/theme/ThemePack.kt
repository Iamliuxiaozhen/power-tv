package com.zitzhen.powertv.data.theme

import java.io.File
import java.util.Locale

/**
 * Material 3 colour roles that a theme pack can set. The key is the JSON field
 * name and intentionally matches the Material Theme Builder export.
 */
enum class ThemeColor(val key: String) {
    PRIMARY("primary"),
    ON_PRIMARY("onPrimary"),
    PRIMARY_CONTAINER("primaryContainer"),
    ON_PRIMARY_CONTAINER("onPrimaryContainer"),

    SECONDARY("secondary"),
    ON_SECONDARY("onSecondary"),
    SECONDARY_CONTAINER("secondaryContainer"),
    ON_SECONDARY_CONTAINER("onSecondaryContainer"),

    TERTIARY("tertiary"),
    ON_TERTIARY("onTertiary"),
    TERTIARY_CONTAINER("tertiaryContainer"),
    ON_TERTIARY_CONTAINER("onTertiaryContainer"),

    ERROR("error"),
    ON_ERROR("onError"),
    ERROR_CONTAINER("errorContainer"),
    ON_ERROR_CONTAINER("onErrorContainer"),

    BACKGROUND("background"),
    ON_BACKGROUND("onBackground"),
    SURFACE("surface"),
    ON_SURFACE("onSurface"),
    SURFACE_VARIANT("surfaceVariant"),
    ON_SURFACE_VARIANT("onSurfaceVariant"),
    OUTLINE("outline"),
    OUTLINE_VARIANT("outlineVariant"),

    SURFACE_CONTAINER_LOWEST("surfaceContainerLowest"),
    SURFACE_CONTAINER_LOW("surfaceContainerLow"),
    SURFACE_CONTAINER("surfaceContainer"),
    SURFACE_CONTAINER_HIGH("surfaceContainerHigh"),
    SURFACE_CONTAINER_HIGHEST("surfaceContainerHighest"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): ThemeColor? = byKey[key]
    }
}

/** Chart and status accents that are specific to power-tv. */
data class DataVizColors(
    val loadLow: Int,
    val loadMedium: Int,
    val loadHigh: Int,
    val chartGrid: Int,
    val chartFill: Int,
    val liveDot: Int,
)

data class ThemeScheme(
    val colors: Map<ThemeColor, Int>,
    val dataViz: DataVizColors,
) {
    operator fun get(color: ThemeColor): Int = colors.getValue(color)
}

data class ThemeTypography(
    val metricFont: String? = null,
    val monoFont: String? = null,
)

data class ThemeShape(
    val cardRadiusDp: Float? = null,
)

data class ThemeBackdrop(
    val image: String,
    val dim: Float = 0.35f,
)

data class ThemeLayout(
    val order: List<String>,
    val visible: List<String>? = null,
)

/**
 * A declarative theme pack. Instances are immutable; [storageDir] points at the
 * directory that owns the pack's optional assets (null for built-in packs).
 */
data class ThemePack(
    val schemaVersion: Int,
    val id: String,
    val names: Map<String, String>,
    val descriptions: Map<String, String> = emptyMap(),
    val author: String? = null,
    val version: String = "1.0.0",
    val minAppVersion: String? = null,
    val seed: String? = null,
    val light: ThemeScheme,
    val dark: ThemeScheme,
    val typography: ThemeTypography? = null,
    val shape: ThemeShape? = null,
    val backdrop: ThemeBackdrop? = null,
    val layout: ThemeLayout? = null,
    val builtIn: Boolean = false,
    val storageDir: File? = null,
    val warnings: List<String> = emptyList(),
) {

    fun name(locale: Locale = Locale.getDefault()): String = resolveText(names, locale) ?: id

    fun description(locale: Locale = Locale.getDefault()): String? = resolveText(descriptions, locale)

    /** Resolves a path inside the pack and refuses to escape [storageDir]. */
    fun asset(relative: String): File? {
        val root = storageDir ?: return null
        val file = File(root, relative)
        val rootPath = root.canonicalPath + File.separator
        return if (file.canonicalPath.startsWith(rootPath) && file.isFile) file else null
    }

    private companion object {
        fun resolveText(values: Map<String, String>, locale: Locale): String? {
            if (values.isEmpty()) return null
            val language = locale.language
            val country = locale.country
            val keys = buildList {
                if (country.isNotEmpty()) add("$language-$country")
                add(language)
                add("default")
                add("en")
            }
            keys.forEach { key ->
                values[key]?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return values.values.firstOrNull { it.isNotBlank() }
        }
    }
}

/** A theme pack resolved for the current light/dark configuration. */
data class ResolvedTheme(
    val pack: ThemePack,
    val dark: Boolean,
    val colors: Map<ThemeColor, Int>,
    val dataViz: DataVizColors,
    val typography: ThemeTypography?,
    val shape: ThemeShape?,
    val backdrop: ThemeBackdrop?,
    val layout: ThemeLayout?,
) {
    operator fun get(color: ThemeColor): Int = colors.getValue(color)
}
