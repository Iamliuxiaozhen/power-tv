package com.zitzhen.powertv.ui.theme

import android.app.Activity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemePack
import com.zitzhen.powertv.databinding.DialogThemePreviewBinding

object ThemePreviewDialog {

    fun show(
        activity: Activity,
        pack: ThemePack,
        theme: ResolvedTheme,
        onApply: (applyLayout: Boolean) -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        val binding = DialogThemePreviewBinding.inflate(activity.layoutInflater)
        binding.previewApplyLayout.isVisible = pack.layout != null
        ThemePreview.bind(binding, theme)

        var applied = false
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(pack.name())
            .setView(binding.root)
            .setPositiveButton(R.string.theme_apply) { _, _ ->
                applied = true
                onApply(binding.previewApplyLayout.isVisible && binding.previewApplyLayout.isChecked)
            }
            .setNegativeButton(R.string.theme_cancel, null)
            .create()
        dialog.setOnShowListener { ThemeApplier.applyDialog(dialog, theme) }
        dialog.setOnDismissListener {
            if (!applied) onDismiss()
        }
        dialog.show()
    }
}
