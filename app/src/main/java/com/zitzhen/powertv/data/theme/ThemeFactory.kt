package com.zitzhen.powertv.data.theme

import java.util.Locale
import kotlin.random.Random

/** Builds a coherent light + dark pack from a single seed colour. */
object ThemeFactory {

    fun create(
        id: String,
        name: String,
        author: String?,
        seed: Int,
        locale: Locale = Locale.getDefault(),
    ): ThemePack {
        ThemePackCodec.validateId(id)
        val trimmedName = name.trim().ifEmpty { "Untitled theme" }
        val hsl = ColorMath.rgbToHsl(seed)
        val lightSurface = ColorMath.hslToColor(
            hsl[0],
            (hsl[1] * 0.10f).coerceAtMost(0.10f),
            0.965f,
        )
        val primary = ColorMath.ensureContrast(
            ColorMath.hslToColor(hsl[0], hsl[1].coerceIn(0.25f, 0.95f), 0.42f),
            lightSurface,
            4.5,
            darken = true,
        )
        val light = ThemePaletteDeriver.complete(
            mapOf(ThemeColor.PRIMARY to primary, ThemeColor.SURFACE to lightSurface),
            dark = false,
        )
        val dark = ThemePaletteDeriver.autoDark(light)
        return ThemePack(
            schemaVersion = ThemePackCodec.SCHEMA_VERSION,
            id = id,
            names = mapOf(locale.language.ifEmpty { "default" } to trimmedName),
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            version = "1.0.0",
            seed = ColorMath.toHex(seed),
            light = ThemeScheme(
                colors = light,
                dataViz = ThemePaletteDeriver.dataViz(light, dark = false),
            ),
            dark = ThemeScheme(
                colors = dark,
                dataViz = ThemePaletteDeriver.dataViz(dark, dark = true),
            ),
        )
    }

    fun newId(name: String, random: Random = Random.Default): String {
        val slug = name.lowercase(Locale.US)
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .take(40)
            .ifEmpty { "theme" }
        val suffix = random.nextInt(0x10000).toString(16).padStart(4, '0')
        return "user.$slug.$suffix"
    }
}
