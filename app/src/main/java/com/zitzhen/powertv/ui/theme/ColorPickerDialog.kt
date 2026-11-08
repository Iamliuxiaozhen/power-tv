package com.zitzhen.powertv.ui.theme

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ColorMath
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.databinding.DialogColorPickerBinding
import kotlin.math.roundToInt

object ColorPickerDialog {

    fun show(
        activity: Activity,
        initial: Int,
        theme: ResolvedTheme,
        onPicked: (Int) -> Unit,
    ) {
        val binding = DialogColorPickerBinding.inflate(activity.layoutInflater)
        var color = initial or 0xFF000000.toInt()
        var updating = false

        fun paintPreview() {
            val radius = 14f * activity.resources.displayMetrics.density
            binding.colorPreview.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(color)
            }
        }

        fun syncFromColor() {
            val hsl = ColorMath.rgbToHsl(color)
            updating = true
            binding.hueSeek.progress = hsl[0].roundToInt().coerceIn(0, 360)
            binding.saturationSeek.progress = (hsl[1] * 100).roundToInt().coerceIn(0, 100)
            binding.lightnessSeek.progress = (hsl[2] * 100).roundToInt().coerceIn(0, 100)
            binding.hexInput.setText(ColorMath.toHex(color))
            binding.hexInput.setSelection(binding.hexInput.text?.length ?: 0)
            updating = false
        }

        fun syncFromSeek() {
            if (updating) return
            color = ColorMath.hslToColor(
                binding.hueSeek.progress.toFloat(),
                binding.saturationSeek.progress / 100f,
                binding.lightnessSeek.progress / 100f,
            )
            paintPreview()
            updating = true
            binding.hexInput.setText(ColorMath.toHex(color))
            binding.hexInput.setSelection(binding.hexInput.text?.length ?: 0)
            updating = false
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) syncFromSeek()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        binding.hueSeek.setOnSeekBarChangeListener(seekListener)
        binding.saturationSeek.setOnSeekBarChangeListener(seekListener)
        binding.lightnessSeek.setOnSeekBarChangeListener(seekListener)
        binding.hexInput.doAfterTextChanged { text ->
            if (updating) return@doAfterTextChanged
            val parsed = ColorMath.parseHex(text?.toString().orEmpty()) ?: return@doAfterTextChanged
            color = parsed or 0xFF000000.toInt()
            binding.hexLayout.error = null
            paintPreview()
            val hsl = ColorMath.rgbToHsl(color)
            updating = true
            binding.hueSeek.progress = hsl[0].roundToInt().coerceIn(0, 360)
            binding.saturationSeek.progress = (hsl[1] * 100).roundToInt().coerceIn(0, 100)
            binding.lightnessSeek.progress = (hsl[2] * 100).roundToInt().coerceIn(0, 100)
            updating = false
        }

        paintPreview()
        syncFromColor()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.theme_pick_color)
            .setView(binding.root)
            .setPositiveButton(R.string.theme_apply, null)
            .setNegativeButton(R.string.theme_cancel, null)
            .create()

        dialog.setOnShowListener {
            ThemeApplier.applyDialog(dialog, theme)
            binding.hexLayout.error = null
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = ColorMath.parseHex(binding.hexInput.text?.toString().orEmpty())
                if (parsed == null) {
                    binding.hexLayout.error = activity.getString(R.string.color_invalid)
                } else {
                    onPicked(parsed or 0xFF000000.toInt())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
