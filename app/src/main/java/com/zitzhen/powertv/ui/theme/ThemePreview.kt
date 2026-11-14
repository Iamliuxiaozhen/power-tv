package com.zitzhen.powertv.ui.theme

import android.content.Context
import androidx.core.view.isVisible
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemeColor
import com.zitzhen.powertv.data.theme.ThemeQuality
import com.zitzhen.powertv.data.theme.ThemeVersion
import com.zitzhen.powertv.databinding.DialogThemePreviewBinding

/** Fills the shared sample layout used by the editor and the preview dialog. */
object ThemePreview {

    fun bind(
        binding: DialogThemePreviewBinding,
        theme: ResolvedTheme,
        showWarnings: Boolean = true,
    ) {
        val context = binding.root.context
        binding.previewHeroMeta.text = context.getString(
            R.string.dashboard_hero_meta,
            "16",
            "arm64-v8a",
            "3h 12m",
        )
        binding.previewChart.setValues(sampleValues(), 100f)
        binding.previewProgress.setProgressCompat(42, false)

        if (showWarnings) {
            binding.previewWarning.isVisible = ThemeQuality.hasLowContrast(theme)
            val newer = ThemeVersion.isNewerThan(theme.pack.minAppVersion, packageVersion(context))
            binding.previewCompatWarning.isVisible = newer
            if (newer) {
                binding.previewCompatWarning.text = context.getString(
                    R.string.theme_newer_app,
                    theme.pack.minAppVersion,
                )
            }
        } else {
            binding.previewWarning.isVisible = false
            binding.previewCompatWarning.isVisible = false
        }

        ThemeApplier.applyTree(binding.root, theme)
        ThemeApplier.markThemed(binding.root)
        // ProgressIndicator stores its colours separately from text colours.
        binding.previewProgress.setIndicatorColor(theme[ThemeColor.PRIMARY])
        binding.previewProgress.setTrackColor(theme[ThemeColor.SURFACE_CONTAINER_HIGHEST])
    }

    private fun sampleValues(): List<Float> {
        val values = MutableList(60) { index ->
            (26f + (index * 37 % 23)).coerceAtMost(78f)
        }
        values[values.lastIndex] = 42f
        return values
    }

    private fun packageVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }
}
