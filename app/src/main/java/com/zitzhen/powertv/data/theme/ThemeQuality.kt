package com.zitzhen.powertv.data.theme

/** Lightweight, user-facing quality checks that do not block an import. */
object ThemeQuality {

    private val textPairs = listOf(
        ThemeColor.ON_SURFACE to ThemeColor.SURFACE,
        ThemeColor.ON_SURFACE_VARIANT to ThemeColor.SURFACE_CONTAINER,
        ThemeColor.ON_PRIMARY to ThemeColor.PRIMARY,
        ThemeColor.ON_PRIMARY_CONTAINER to ThemeColor.PRIMARY_CONTAINER,
        ThemeColor.ON_SECONDARY to ThemeColor.SECONDARY,
        ThemeColor.ON_TERTIARY to ThemeColor.TERTIARY,
        ThemeColor.ON_ERROR to ThemeColor.ERROR,
        ThemeColor.ON_ERROR_CONTAINER to ThemeColor.ERROR_CONTAINER,
    )

    fun lowContrastPairs(theme: ResolvedTheme): List<Pair<ThemeColor, ThemeColor>> =
        textPairs.filter { (foreground, background) ->
            ColorMath.contrast(theme[foreground], theme[background]) < 4.5
        }

    fun hasLowContrast(theme: ResolvedTheme): Boolean = lowContrastPairs(theme).isNotEmpty()
}

/** Tiny semantic-version comparison for `minAppVersion`. */
object ThemeVersion {

    fun isNewerThan(required: String?, current: String): Boolean {
        if (required.isNullOrBlank()) return false
        val left = parts(required)
        val right = parts(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(value: String): List<Int> = value.split('.')
        .mapNotNull { it.toIntOrNull() }
        .ifEmpty { listOf(0) }
}
