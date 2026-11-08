package com.zitzhen.powertv.ui.theme

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.PowerTvApp
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ColorMath
import com.zitzhen.powertv.data.theme.ThemeColor
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemeFactory
import com.zitzhen.powertv.data.theme.ThemeRepository
import com.zitzhen.powertv.databinding.ActivityThemeEditorBinding
import com.zitzhen.powertv.databinding.DialogThemePreviewBinding

class ThemeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeEditorBinding
    private lateinit var repository: ThemeRepository
    private lateinit var theme: ResolvedTheme
    private var seed: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as PowerTvApp).themes
        theme = repository.activeTheme()
        seed = theme[ThemeColor.PRIMARY]

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_theme_save) {
                save()
                true
            } else {
                false
            }
        }
        binding.saveButton.setOnClickListener { save() }
        binding.seedCard.setOnClickListener {
            ColorPickerDialog.show(this, seed, theme) { picked ->
                seed = picked
                updateSeed()
                updatePreview()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ThemeApplier.apply(this, theme)
        updateSeed()
        updatePreview()
    }

    private fun updateSeed() {
        val radius = 12f * resources.displayMetrics.density
        binding.seedSwatch.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(seed)
        }
        binding.seedHex.text = ColorMath.toHex(seed)
    }

    private fun updatePreview() {
        val name = binding.nameInput.text?.toString()?.trim()
            ?.ifEmpty { getString(R.string.theme_preview_title) }
            ?: getString(R.string.theme_preview_title)
        val pack = ThemeFactory.create("user.preview", name, null, seed)
        val preview = DialogThemePreviewBinding.inflate(layoutInflater, binding.previewContainer, false)
        preview.previewApplyLayout.visibility = android.view.View.GONE
        ThemePreview.bind(preview, repository.resolve(pack, theme.dark))
        binding.previewContainer.removeAllViews()
        binding.previewContainer.addView(preview.root)
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.theme_name_required)
            return
        }
        binding.nameLayout.error = null
        val author = binding.authorInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        try {
            var id = ThemeFactory.newId(name)
            while (repository.find(id) != null) {
                id = ThemeFactory.newId(name)
            }
            val pack = ThemeFactory.create(id, name, author, seed)
            repository.install(pack)
            repository.setActive(pack.id)
            Toast.makeText(
                this,
                getString(R.string.theme_saved, pack.name()),
                Toast.LENGTH_SHORT,
            ).show()
            setResult(RESULT_OK)
            finish()
        } catch (error: Exception) {
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_title)
                .setMessage(getString(R.string.theme_save_failed, error.message ?: "unknown"))
                .setPositiveButton(R.string.about_ok, null)
                .create()
            dialog.setOnShowListener { ThemeApplier.applyDialog(dialog, theme) }
            dialog.show()
        }
    }
}
