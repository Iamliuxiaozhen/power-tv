package com.zitzhen.powertv.ui

import com.zitzhen.powertv.data.theme.ResolvedTheme

/** Shared load-to-colour mapping for bars, charts and pills. */
object LoadPalette {

    fun color(theme: ResolvedTheme, percent: Float?): Int = when {
        percent == null -> theme.dataViz.loadLow
        percent >= 85f -> theme.dataViz.loadHigh
        percent >= 60f -> theme.dataViz.loadMedium
        else -> theme.dataViz.loadLow
    }
}
