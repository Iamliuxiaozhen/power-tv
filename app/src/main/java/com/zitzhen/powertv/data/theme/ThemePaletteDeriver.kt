package com.zitzhen.powertv.data.theme

/**
 * Fills in any colour role a pack did not provide. Authors only need to supply
 * a primary and a surface; every other role is derived deterministically.
 */
object ThemePaletteDeriver {

    private const val LIGHT_LOAD_MEDIUM = 0xFFB26A00.toInt()
    private const val DARK_LOAD_MEDIUM = 0xFFFFC857.toInt()

    private const val LIGHT_ERROR = 0xFFBA1A1A.toInt()
    private const val LIGHT_ON_ERROR = 0xFFFFFFFF.toInt()
    private const val LIGHT_ERROR_CONTAINER = 0xFFFFDAD6.toInt()
    private const val LIGHT_ON_ERROR_CONTAINER = 0xFF410002.toInt()

    private const val DARK_ERROR = 0xFFFFB4AB.toInt()
    private const val DARK_ON_ERROR = 0xFF690005.toInt()
    private const val DARK_ERROR_CONTAINER = 0xFF93000A.toInt()
    private const val DARK_ON_ERROR_CONTAINER = 0xFFFFDAD6.toInt()

    fun complete(input: Map<ThemeColor, Int>, dark: Boolean): Map<ThemeColor, Int> {
        val primary = input[ThemeColor.PRIMARY]
            ?: input[ThemeColor.SECONDARY]
            ?: input[ThemeColor.TERTIARY]
            ?: throw IllegalArgumentException("a theme needs at least a primary colour")
        val surface = input[ThemeColor.SURFACE]
            ?: input[ThemeColor.BACKGROUND]
            ?: throw IllegalArgumentException("a theme needs at least a surface colour")
        val onSurface = input[ThemeColor.ON_SURFACE] ?: input[ThemeColor.ON_BACKGROUND]
            ?: ColorMath.bestOn(surface)

        val result = input.toMutableMap()
        fun put(color: ThemeColor, value: Int) {
            result.putIfAbsent(color, value)
        }

        put(ThemeColor.PRIMARY, primary)
        put(ThemeColor.SURFACE, surface)
        put(ThemeColor.BACKGROUND, surface)
        put(ThemeColor.ON_BACKGROUND, onSurface)
        put(ThemeColor.ON_SURFACE, onSurface)
        put(ThemeColor.ON_PRIMARY, ColorMath.bestOn(primary))

        val primaryContainer = putOrGet(
            result,
            ThemeColor.PRIMARY_CONTAINER,
            blend(primary, surface, if (dark) 0.72f else 0.82f),
        )
        put(ThemeColor.ON_PRIMARY_CONTAINER, ColorMath.bestOn(primaryContainer))

        val secondaryBase = result[ThemeColor.SECONDARY] ?: ColorMath.desaturate(
            blend(primary, onSurface, if (dark) 0.30f else 0.22f),
            0.55f,
        )
        put(ThemeColor.SECONDARY, secondaryBase)
        put(ThemeColor.ON_SECONDARY, ColorMath.bestOn(secondaryBase))
        val secondaryContainer = putOrGet(
            result,
            ThemeColor.SECONDARY_CONTAINER,
            blend(secondaryBase, surface, if (dark) 0.72f else 0.82f),
        )
        put(ThemeColor.ON_SECONDARY_CONTAINER, ColorMath.bestOn(secondaryContainer))

        val tertiaryBase = result[ThemeColor.TERTIARY] ?: ColorMath.desaturate(
            ColorMath.hueShift(primary, 55f),
            0.75f,
        )
        put(ThemeColor.TERTIARY, tertiaryBase)
        put(ThemeColor.ON_TERTIARY, ColorMath.bestOn(tertiaryBase))
        val tertiaryContainer = putOrGet(
            result,
            ThemeColor.TERTIARY_CONTAINER,
            blend(tertiaryBase, surface, if (dark) 0.72f else 0.82f),
        )
        put(ThemeColor.ON_TERTIARY_CONTAINER, ColorMath.bestOn(tertiaryContainer))

        put(ThemeColor.ERROR, if (dark) DARK_ERROR else LIGHT_ERROR)
        put(ThemeColor.ON_ERROR, if (dark) DARK_ON_ERROR else LIGHT_ON_ERROR)
        put(ThemeColor.ERROR_CONTAINER, if (dark) DARK_ERROR_CONTAINER else LIGHT_ERROR_CONTAINER)
        put(ThemeColor.ON_ERROR_CONTAINER, if (dark) DARK_ON_ERROR_CONTAINER else LIGHT_ON_ERROR_CONTAINER)

        put(ThemeColor.SURFACE_VARIANT, blend(surface, onSurface, if (dark) 0.16f else 0.07f))
        put(
            ThemeColor.ON_SURFACE_VARIANT,
            ColorMath.ensureContrast(
                blend(onSurface, surface, if (dark) 0.22f else 0.40f),
                result.getValue(ThemeColor.SURFACE_VARIANT),
                4.5,
                darken = !dark,
            ),
        )
        put(ThemeColor.OUTLINE, blend(onSurface, surface, if (dark) 0.42f else 0.55f))
        put(ThemeColor.OUTLINE_VARIANT, blend(onSurface, surface, if (dark) 0.72f else 0.80f))

        if (dark) {
            put(ThemeColor.SURFACE_CONTAINER_LOWEST, blend(surface, ColorMath.BLACK, 0.35f))
            put(ThemeColor.SURFACE_CONTAINER_LOW, blend(surface, onSurface, 0.03f))
            put(ThemeColor.SURFACE_CONTAINER, blend(surface, onSurface, 0.06f))
            put(ThemeColor.SURFACE_CONTAINER_HIGH, blend(surface, onSurface, 0.10f))
            put(ThemeColor.SURFACE_CONTAINER_HIGHEST, blend(surface, onSurface, 0.15f))
        } else {
            put(ThemeColor.SURFACE_CONTAINER_LOWEST, blend(surface, ColorMath.WHITE, 0.65f))
            put(ThemeColor.SURFACE_CONTAINER_LOW, blend(surface, onSurface, 0.015f))
            put(ThemeColor.SURFACE_CONTAINER, blend(surface, onSurface, 0.035f))
            put(ThemeColor.SURFACE_CONTAINER_HIGH, blend(surface, onSurface, 0.06f))
            put(ThemeColor.SURFACE_CONTAINER_HIGHEST, blend(surface, onSurface, 0.09f))
        }

        val missing = ThemeColor.entries.filter { it !in result }
        check(missing.isEmpty()) { "missing derived colours: ${missing.map { it.key }}" }
        return result
    }

    fun dataViz(
        colors: Map<ThemeColor, Int>,
        dark: Boolean,
        provided: DataVizColors? = null,
    ): DataVizColors {
        val primary = colors.getValue(ThemeColor.PRIMARY)
        val error = colors.getValue(ThemeColor.ERROR)
        return DataVizColors(
            loadLow = provided?.loadLow ?: primary,
            loadMedium = provided?.loadMedium ?: if (dark) DARK_LOAD_MEDIUM else LIGHT_LOAD_MEDIUM,
            loadHigh = provided?.loadHigh ?: error,
            chartGrid = provided?.chartGrid
                ?: ColorMath.withAlpha(primary, if (dark) 0x33 else 0x1A),
            chartFill = provided?.chartFill ?: ColorMath.withAlpha(primary, 0x55),
            liveDot = provided?.liveDot ?: primary,
        )
    }

    /**
     * Builds a dark scheme from a light one. Used when a pack only declares a
     * light palette, and by the in-app creator.
     */
    fun autoDark(light: Map<ThemeColor, Int>): Map<ThemeColor, Int> {
        val primary = light.getValue(ThemeColor.PRIMARY)
        val hsl = ColorMath.rgbToHsl(primary)
        val surface = ColorMath.hslToColor(hsl[0], (hsl[1] * 0.18f).coerceAtMost(0.16f), 0.07f)
        val darkPrimary = ColorMath.ensureContrast(
            ColorMath.hslToColor(hsl[0], (hsl[1] * 0.75f).coerceIn(0.15f, 0.9f), 0.76f),
            surface,
            4.5,
            darken = false,
        )
        return complete(
            mapOf(
                ThemeColor.PRIMARY to darkPrimary,
                ThemeColor.SURFACE to surface,
            ),
            dark = true,
        )
    }

    private fun putOrGet(
        target: MutableMap<ThemeColor, Int>,
        color: ThemeColor,
        value: Int,
    ): Int {
        target[color]?.let { return it }
        target[color] = value
        return value
    }

    private fun blend(from: Int, to: Int, ratio: Float): Int = ColorMath.blend(from, to, ratio)
}
