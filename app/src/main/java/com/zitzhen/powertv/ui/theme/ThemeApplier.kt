package com.zitzhen.powertv.ui.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ColorMath
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemeColor
import com.zitzhen.powertv.ui.widget.SparklineView
import java.io.File

enum class PillTone { NEUTRAL, PRIMARY, WARNING }

/**
 * Applies a [ResolvedTheme] to the existing View hierarchy.
 *
 * Layout colours are compiled as `?attr/...` references, so they cannot be
 * swapped by changing a theme resource at runtime. Instead this pass maps the
 * stock Material colours that the views resolved at inflation time to the
 * palette of the active pack. That keeps every screen on the same tokens while
 * leaving the XML readable.
 */
object ThemeApplier {

    private const val TAG_PILL_PRIMARY = "powertv_pill_primary"
    private const val TAG_PILL_SURFACE = "powertv_pill_surface"
    private const val TAG_PILL_WARNING = "powertv_pill_warning"
    private const val TAG_METRIC = "powertv_metric"
    private const val TAG_MONO = "powertv_mono"
    private const val TAG_NO_REMAP = "powertv_color_preview"
    private const val TAG_THEMED = "powertv_themed"

    private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)

    fun apply(activity: Activity, theme: ResolvedTheme) {
        applyWindow(activity, theme)
        applyChrome(activity, theme)
        Styler(activity, theme).apply(activity.window.decorView)
        applyBackdrop(activity, theme)
    }

    fun styler(context: android.content.Context, theme: ResolvedTheme): Styler =
        Styler(context, theme)

    /**
     * Reusable view styler. Build this once per screen/adapter instead of
     * resolving the stock palette for every recycled row.
     */
    class Styler(context: android.content.Context, private val theme: ResolvedTheme) {
        private val palette = PaletteMap(context, theme)

        fun apply(root: View) {
            walk(root, palette, theme)
        }
    }

    fun applyWindow(activity: Activity, theme: ResolvedTheme) {
        val surface = theme[ThemeColor.SURFACE]
        activity.window.setBackgroundDrawable(ColorDrawable(surface))
        activity.window.decorView.setBackgroundColor(surface)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val lightBars = !ColorMath.isDark(surface)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = surface
        }
    }

    fun applyChrome(activity: Activity, theme: ResolvedTheme) {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            toolbar.setBackgroundColor(theme[ThemeColor.SURFACE])
            toolbar.setTitleTextColor(theme[ThemeColor.ON_SURFACE])
            toolbar.setSubtitleTextColor(theme[ThemeColor.ON_SURFACE_VARIANT])
            toolbar.navigationIcon?.let { icon ->
                DrawableCompat.setTint(icon.mutate(), theme[ThemeColor.ON_SURFACE_VARIANT])
            }
            toolbar.overflowIcon?.let { icon ->
                DrawableCompat.setTint(icon.mutate(), theme[ThemeColor.ON_SURFACE_VARIANT])
            }
            val iconColor = ColorStateList.valueOf(theme[ThemeColor.ON_SURFACE_VARIANT])
            val menu = toolbar.menu
            for (index in 0 until menu.size()) {
                val item = menu.getItem(index)
                val icon = item.icon ?: continue
                if (Build.VERSION.SDK_INT >= 26) {
                    item.iconTintList = iconColor
                } else {
                    DrawableCompat.setTint(icon.mutate(), theme[ThemeColor.ON_SURFACE_VARIANT])
                }
            }
        }

        activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.let { navigation ->
            navigation.setBackgroundColor(theme[ThemeColor.SURFACE_CONTAINER])
            val itemColors = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    theme[ThemeColor.ON_SECONDARY_CONTAINER],
                    theme[ThemeColor.ON_SURFACE_VARIANT],
                ),
            )
            navigation.itemIconTintList = itemColors
            navigation.itemTextColor = itemColors
            navigation.setItemActiveIndicatorColor(
                ColorStateList.valueOf(theme[ThemeColor.SECONDARY_CONTAINER]),
            )
        }
    }

    fun applyTree(root: View, theme: ResolvedTheme) {
        Styler(root.context, theme).apply(root)
    }

    /** Marks a subtree that already received a theme, so dialogs skip it. */
    fun markThemed(root: View) {
        root.tag = TAG_THEMED
    }

    fun applyDialog(dialog: android.app.Dialog, theme: ResolvedTheme) {
        val decor = dialog.window?.decorView ?: return
        applyTree(decor, theme)
        if (dialog is AlertDialog) {
            val color = theme[ThemeColor.PRIMARY]
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(color)
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(color)
        }
    }

    fun applyPill(view: TextView, theme: ResolvedTheme, tone: PillTone) {
        val background: Int
        val foreground: Int
        val stroke: Int?
        when (tone) {
            PillTone.NEUTRAL -> {
                background = theme[ThemeColor.SURFACE_CONTAINER_HIGH]
                foreground = theme[ThemeColor.ON_SURFACE_VARIANT]
                stroke = theme[ThemeColor.OUTLINE_VARIANT]
            }
            PillTone.PRIMARY -> {
                background = theme[ThemeColor.PRIMARY_CONTAINER]
                foreground = theme[ThemeColor.ON_PRIMARY_CONTAINER]
                stroke = null
            }
            PillTone.WARNING -> {
                background = theme[ThemeColor.ERROR_CONTAINER]
                foreground = theme[ThemeColor.ON_ERROR_CONTAINER]
                stroke = null
            }
        }
        val context = view.context
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * context.resources.displayMetrics.density
            setColor(background)
            stroke?.let { setStroke((1f * context.resources.displayMetrics.density).toInt(), it) }
        }
        view.background = drawable
        view.setTextColor(foreground)
    }

    private fun walk(view: View, palette: PaletteMap, theme: ResolvedTheme) {
        if (view.tag == TAG_THEMED) return
        applyView(view, palette, theme)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), palette, theme)
            }
        }
    }

    private fun applyView(view: View, palette: PaletteMap, theme: ResolvedTheme) {
        if (view.tag == TAG_NO_REMAP) return
        remapBackground(view, palette)

        when (view) {
            is SparklineView -> view.applyPalette(
                theme[ThemeColor.PRIMARY],
                theme.dataViz.chartGrid,
                theme.dataViz.chartFill,
            )
            is MaterialCardView -> {
                palette.remap(view.cardBackgroundColor)?.let(view::setCardBackgroundColor)
                theme.shape?.cardRadiusDp?.let { view.radius = dp(view.context, it) }
            }
            is BaseProgressIndicator<*> -> {
                val indicators = view.getIndicatorColor()
                if (indicators.isNotEmpty()) {
                    val mapped = indicators.map(palette::map).toIntArray()
                    if (!mapped.contentEquals(indicators)) view.setIndicatorColor(*mapped)
                }
                val track = view.getTrackColor()
                val mappedTrack = palette.map(track)
                if (mappedTrack != track) view.setTrackColor(mappedTrack)
            }
            is Chip -> {
                view.setChipBackgroundColor(
                    palette.checked(theme[ThemeColor.SECONDARY_CONTAINER], theme[ThemeColor.SURFACE_CONTAINER_LOW]),
                )
                view.setChipStrokeColor(ColorStateList.valueOf(theme[ThemeColor.OUTLINE_VARIANT]))
                view.setChipIconTint(
                    palette.checked(theme[ThemeColor.ON_SECONDARY_CONTAINER], theme[ThemeColor.ON_SURFACE_VARIANT]),
                )
                view.setTextColor(
                    palette.checked(theme[ThemeColor.ON_SECONDARY_CONTAINER], theme[ThemeColor.ON_SURFACE_VARIANT]),
                )
            }
            is SwitchMaterial -> {
                view.thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(theme[ThemeColor.PRIMARY], theme[ThemeColor.OUTLINE]),
                )
                view.trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        ColorMath.withAlpha(theme[ThemeColor.PRIMARY], 0x55),
                        theme[ThemeColor.SURFACE_VARIANT],
                    ),
                )
                palette.remap(view.textColors)?.let(view::setTextColor)
            }
            is SeekBar -> {
                palette.remap(view.progressTintList)?.let {
                    view.progressTintList = ColorStateList.valueOf(it)
                }
                palette.remap(view.thumbTintList)?.let {
                    view.thumbTintList = ColorStateList.valueOf(it)
                }
            }
            is CompoundButton -> {
                palette.remap(view.buttonTintList)?.let {
                    view.buttonTintList = ColorStateList.valueOf(it)
                }
                palette.remap(view.textColors)?.let(view::setTextColor)
            }
            is MaterialButton -> {
                palette.remap(view.backgroundTintList)
                    ?.takeIf { Color.alpha(it) != 0 }
                    ?.let { view.backgroundTintList = palette.enabled(it) }
                palette.remap(view.iconTint)
                    ?.takeIf { Color.alpha(it) != 0 }
                    ?.let { view.iconTint = palette.enabled(it) }
                palette.remap(view.rippleColor)
                    ?.takeIf { Color.alpha(it) != 0 }
                    ?.let { view.setRippleColor(ColorStateList.valueOf(it)) }
                palette.remap(view.strokeColor)?.let {
                    view.strokeColor = ColorStateList.valueOf(it)
                }
                palette.remap(view.textColors)?.let {
                    view.setTextColor(palette.enabled(it))
                }
            }
            is TextView -> {
                palette.remap(view.textColors)?.let(view::setTextColor)
                applyTypeface(view, theme)
                when (view.tag) {
                    TAG_PILL_PRIMARY -> applyPill(view, theme, PillTone.PRIMARY)
                    TAG_PILL_SURFACE -> applyPill(view, theme, PillTone.NEUTRAL)
                    TAG_PILL_WARNING -> applyPill(view, theme, PillTone.WARNING)
                }
            }
            is ImageView -> {
                palette.remap(view.imageTintList)?.let {
                    view.setImageTintList(ColorStateList.valueOf(it))
                }
            }
        }
    }

    private fun applyTypeface(view: TextView, theme: ResolvedTheme) {
        val typography = theme.typography ?: return
        val family = when (view.tag) {
            TAG_METRIC -> typography.metricFont
            TAG_MONO -> typography.monoFont
            else -> null
        } ?: return
        view.typeface = Typeface.create(family, Typeface.NORMAL)
    }

    private fun remapBackground(view: View, palette: PaletteMap) {
        val background = view.background as? GradientDrawable ?: return
        val color = background.color?.defaultColor ?: return
        background.setColor(palette.map(color))
    }

    private fun applyBackdrop(activity: Activity, theme: ResolvedTheme) {
        val image = activity.findViewById<ImageView>(R.id.theme_backdrop) ?: return
        val dim = activity.findViewById<View>(R.id.theme_backdrop_dim)
        val backdrop = theme.backdrop
        val file = backdrop?.let { theme.pack.asset(it.image) }
        if (backdrop == null || file == null) {
            image.setImageDrawable(null)
            image.visibility = View.GONE
            dim?.visibility = View.GONE
            return
        }
        val bitmap = decodeBackdrop(file)
        if (bitmap == null) {
            image.setImageDrawable(null)
            image.visibility = View.GONE
            dim?.visibility = View.GONE
            return
        }
        image.setImageBitmap(bitmap)
        image.visibility = View.VISIBLE
        if (dim != null) {
            dim.setBackgroundColor(ColorMath.withAlpha(Color.BLACK, (backdrop.dim * 255).toInt()))
            dim.visibility = if (backdrop.dim > 0f) View.VISIBLE else View.GONE
        }
    }

    private fun decodeBackdrop(file: File): android.graphics.Bitmap? {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (options.outWidth / sample * (options.outHeight / sample) > MAX_BACKDROP_PIXELS) {
            sample *= 2
        }
        val decode = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath, decode)
    }

    private const val MAX_BACKDROP_PIXELS = 4_000_000

    private fun dp(context: android.content.Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    /** Maps stock Material colours to the active pack's colours. */
    private class PaletteMap(context: android.content.Context, theme: ResolvedTheme) {

        private val exact = HashMap<Int, Int>()
        private val opaque = HashMap<Int, Int>()

        init {
            ThemeColor.entries.forEach { role ->
                val stock = stockColor(context, role) ?: return@forEach
                register(stock, theme[role])
            }
            register(ContextCompat.getColor(context, R.color.load_low), theme.dataViz.loadLow)
            register(ContextCompat.getColor(context, R.color.load_medium), theme.dataViz.loadMedium)
            register(ContextCompat.getColor(context, R.color.load_high), theme.dataViz.loadHigh)
            register(ContextCompat.getColor(context, R.color.chart_grid), theme.dataViz.chartGrid)
            register(ContextCompat.getColor(context, R.color.chart_fill), theme.dataViz.chartFill)
            register(ContextCompat.getColor(context, R.color.live_dot), theme.dataViz.liveDot)
        }

        fun map(argb: Int): Int {
            if (Color.alpha(argb) == 0) return argb
            exact[argb]?.let { return it }
            val opaqueColor = argb or 0xFF000000.toInt()
            val mapped = opaque[opaqueColor] ?: return argb
            return (argb and 0xFF000000.toInt()) or (mapped and 0x00FFFFFF)
        }

        /**
         * Resolves the colour a view uses while enabled. Material selectors put
         * their enabled colour under `state_enabled`, so [ColorStateList.getDefaultColor]
         * would return the disabled fallback (onSurface at 12–38%) and make enabled
         * controls look grey.
         */
        fun remap(colors: ColorStateList?): Int? =
            colors?.let { map(it.getColorForState(ENABLED_STATE, it.defaultColor)) }

        fun enabled(color: Int): ColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(ColorMath.withAlpha(color, 0x61), color),
        )

        fun checked(checked: Int, normal: Int): ColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, normal),
        )

        /**
         * Records stock → theme mappings. Different roles can resolve to the same
         * stock colour (this app's `colorBackground` and `colorSurface` are equal),
         * and an inflated view cannot tell which attribute it used, so the last
         * registration wins. [ThemeColor] declaration order therefore decides the
         * alias, with surface-like roles intentionally coming last.
         */
        private fun register(stock: Int, replacement: Int) {
            exact[stock] = replacement
            if (Color.alpha(stock) == 0xFF && Color.alpha(replacement) == 0xFF) {
                opaque[stock] = replacement
            }
        }

        private fun stockColor(context: android.content.Context, role: ThemeColor): Int? {
            val attr = attrFor(role)
            return runCatching {
                val typed = context.theme.obtainStyledAttributes(intArrayOf(attr))
                try {
                    if (typed.hasValue(0)) typed.getColor(0, 0) else null
                } finally {
                    typed.recycle()
                }
            }.getOrNull()
        }

        private fun attrFor(role: ThemeColor): Int = when (role) {
            ThemeColor.PRIMARY -> com.google.android.material.R.attr.colorPrimary
            ThemeColor.ON_PRIMARY -> com.google.android.material.R.attr.colorOnPrimary
            ThemeColor.PRIMARY_CONTAINER -> com.google.android.material.R.attr.colorPrimaryContainer
            ThemeColor.ON_PRIMARY_CONTAINER -> com.google.android.material.R.attr.colorOnPrimaryContainer
            ThemeColor.SECONDARY -> com.google.android.material.R.attr.colorSecondary
            ThemeColor.ON_SECONDARY -> com.google.android.material.R.attr.colorOnSecondary
            ThemeColor.SECONDARY_CONTAINER -> com.google.android.material.R.attr.colorSecondaryContainer
            ThemeColor.ON_SECONDARY_CONTAINER -> com.google.android.material.R.attr.colorOnSecondaryContainer
            ThemeColor.TERTIARY -> com.google.android.material.R.attr.colorTertiary
            ThemeColor.ON_TERTIARY -> com.google.android.material.R.attr.colorOnTertiary
            ThemeColor.TERTIARY_CONTAINER -> com.google.android.material.R.attr.colorTertiaryContainer
            ThemeColor.ON_TERTIARY_CONTAINER -> com.google.android.material.R.attr.colorOnTertiaryContainer
            ThemeColor.ERROR -> com.google.android.material.R.attr.colorError
            ThemeColor.ON_ERROR -> com.google.android.material.R.attr.colorOnError
            ThemeColor.ERROR_CONTAINER -> com.google.android.material.R.attr.colorErrorContainer
            ThemeColor.ON_ERROR_CONTAINER -> com.google.android.material.R.attr.colorOnErrorContainer
            ThemeColor.BACKGROUND -> android.R.attr.colorBackground
            ThemeColor.ON_BACKGROUND -> com.google.android.material.R.attr.colorOnBackground
            ThemeColor.SURFACE -> com.google.android.material.R.attr.colorSurface
            ThemeColor.ON_SURFACE -> com.google.android.material.R.attr.colorOnSurface
            ThemeColor.SURFACE_VARIANT -> com.google.android.material.R.attr.colorSurfaceVariant
            ThemeColor.ON_SURFACE_VARIANT -> com.google.android.material.R.attr.colorOnSurfaceVariant
            ThemeColor.OUTLINE -> com.google.android.material.R.attr.colorOutline
            ThemeColor.OUTLINE_VARIANT -> com.google.android.material.R.attr.colorOutlineVariant
            ThemeColor.SURFACE_CONTAINER_LOWEST -> com.google.android.material.R.attr.colorSurfaceContainerLowest
            ThemeColor.SURFACE_CONTAINER_LOW -> com.google.android.material.R.attr.colorSurfaceContainerLow
            ThemeColor.SURFACE_CONTAINER -> com.google.android.material.R.attr.colorSurfaceContainer
            ThemeColor.SURFACE_CONTAINER_HIGH -> com.google.android.material.R.attr.colorSurfaceContainerHigh
            ThemeColor.SURFACE_CONTAINER_HIGHEST -> com.google.android.material.R.attr.colorSurfaceContainerHighest
        }
    }
}
