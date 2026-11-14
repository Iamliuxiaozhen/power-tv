package com.zitzhen.powertv.data.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Small, dependency-free colour toolkit used by the theme pack layer.
 *
 * Everything here is plain Kotlin so the parser and palette derivation can be
 * exercised by JVM unit tests without pulling in Android framework stubs.
 */
object ColorMath {

    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLACK: Int = 0xFF000000.toInt()

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun red(color: Int): Int = (color ushr 16) and 0xFF

    fun green(color: Int): Int = (color ushr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    /**
     * Accepts `#RGB`, `#RRGGBB` and `#AARRGGBB`. Returns null for anything else.
     */
    fun parseHex(value: String): Int? {
        val text = value.trim()
        if (!text.startsWith("#")) return null
        val hex = text.substring(1)
        if (hex.isEmpty()) return null
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null

        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16)
                val g = hex[1].digitToInt(16)
                val b = hex[2].digitToInt(16)
                0xFF000000.toInt() or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
            }
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> null
        }
    }

    fun toHex(color: Int): String {
        val rgb = (color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
        return if (alpha(color) == 0xFF) "#$rgb" else "#${alpha(color).toString(16).uppercase().padStart(2, '0')}$rgb"
    }

    /** Blends [overlay] into [base]; [ratio] is the weight of [overlay] (0..1). */
    fun blend(base: Int, overlay: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (base ushr shift) and 0xFF
            val to = (overlay ushr shift) and 0xFF
            return (from + (to - from) * t).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Relative luminance as defined by WCAG 2.1. */
    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color))
    }

    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** Picks black or white, whichever reads better on [background]. */
    fun bestOn(background: Int): Int =
        if (contrast(WHITE, background) >= contrast(BLACK, background)) WHITE else BLACK

    fun isDark(color: Int): Boolean = luminance(color) < 0.5

    /** Returns `[hue, saturation, lightness]` with hue in degrees and the rest in 0..1. */
    fun rgbToHsl(color: Int): FloatArray {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2f
        if (max == min) return floatArrayOf(0f, 0f, lightness)

        val delta = max - min
        val saturation = if (lightness > 0.5f) {
            delta / (2f - max - min)
        } else {
            delta / (max + min)
        }
        val hue = when (max) {
            r -> (g - b) / delta + if (g < b) 6f else 0f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f / 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        if (s == 0f) {
            val gray = (l * 255).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun channel(t: Float): Int {
            var value = t
            if (value < 0f) value += 1f
            if (value > 1f) value -= 1f
            val result = when {
                value < 1f / 6f -> p + (q - p) * 6f * value
                value < 1f / 2f -> q
                value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
                else -> p
            }
            return (result * 255).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(h + 1f / 3f) shl 16) or
            (channel(h) shl 8) or channel(h - 1f / 3f)
    }

    fun hueShift(color: Int, degrees: Float): Int {
        val hsl = rgbToHsl(color)
        return hslToColor(hsl[0] + degrees, hsl[1], hsl[2])
    }

    fun desaturate(color: Int, factor: Float): Int {
        val hsl = rgbToHsl(color)
        return hslToColor(hsl[0], hsl[1] * factor.coerceIn(0f, 1f), hsl[2])
    }

    /**
     * Darkens (or lightens) [color] until it reaches [target] contrast against
     * [background]. Returns the closest achievable colour when the target is out
     * of range.
     */
    fun ensureContrast(color: Int, background: Int, target: Double, darken: Boolean): Int {
        if (contrast(color, background) >= target) return color
        val hsl = rgbToHsl(color)
        var lightness = hsl[2]
        repeat(40) {
            lightness = if (darken) lightness - 0.02f else lightness + 0.02f
            if (lightness !in 0f..1f) return hslToColor(hsl[0], hsl[1], lightness.coerceIn(0f, 1f))
            val candidate = hslToColor(hsl[0], hsl[1], lightness)
            if (contrast(candidate, background) >= target) return candidate
        }
        return hslToColor(hsl[0], hsl[1], if (darken) 0f else 1f)
    }
}
