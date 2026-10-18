package com.zitzhen.powertv.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.zitzhen.powertv.R

/** Shared load-to-colour mapping for bars, charts and pills. */
object LoadPalette {

    fun color(context: Context, percent: Float?): Int {
        val res = when {
            percent == null -> R.color.load_low
            percent >= 85f -> R.color.load_high
            percent >= 60f -> R.color.load_medium
            else -> R.color.load_low
        }
        return ContextCompat.getColor(context, res)
    }
}
