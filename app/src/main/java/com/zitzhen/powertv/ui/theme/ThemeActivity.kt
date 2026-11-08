package com.zitzhen.powertv.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.PowerTvApp
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ImportedTheme
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemeImporter
import com.zitzhen.powertv.data.theme.ThemePack
import com.zitzhen.powertv.data.theme.ThemeRepository
import com.zitzhen.powertv.databinding.ActivityThemeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeBinding
    private lateinit var repository: ThemeRepository
    private lateinit var theme: ResolvedTheme
    private lateinit var adapter: ThemeAdapter

    private var appliedRevision = 0
    private var pendingExport: ThemePack? = null
    private var externalHandled = false

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::importTheme) }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(::writeExport) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as PowerTvApp).themes
        theme = repository.activeTheme()
        appliedRevision = repository.revision

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_theme_info) {
                showInfo()
                true
            } else {
                false
            }
        }

        ThemeApplier.apply(this, theme)

        adapter = ThemeAdapter(
            repository = repository,
            theme = theme,
            onPreview = ::preview,
            onExport = ::export,
            onDelete = ::confirmDelete,
        )
        binding.themeList.layoutManager = LinearLayoutManager(this)
        binding.themeList.adapter = adapter

        binding.importButton.setOnClickListener {
            openDocument.launch(
                arrayOf(
                    "application/zip",
                    "application/json",
                    "application/octet-stream",
                    "application/vnd.powertv.theme+zip",
                ),
            )
        }
        binding.createButton.setOnClickListener {
            startActivity(Intent(this, ThemeEditorActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        refresh()
        if (savedInstanceState == null) handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalHandled = false
        handleExternalIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (repository.revision != appliedRevision) {
            recreate()
        } else {
            refresh()
        }
    }

    private fun refresh() {
        adapter.submit(repository.all(), repository.activeId())
    }

    private fun preview(pack: ThemePack) {
        ThemePreviewDialog.show(
            activity = this,
            pack = pack,
            theme = repository.resolve(pack, theme.dark),
            onApply = { applyLayout -> applyPack(pack, applyLayout) },
        )
    }

    private fun applyPack(pack: ThemePack, applyLayout: Boolean) {
        if (applyLayout) {
            repository.applyLayout(pack, (application as PowerTvApp).moduleSettings)
        }
        if (repository.setActive(pack.id)) {
            setResult(RESULT_OK)
            recreate()
        }
    }

    private fun confirmDelete(pack: ThemePack) {
        if (pack.builtIn) return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_delete_title)
            .setMessage(getString(R.string.theme_delete_message, pack.name()))
            .setPositiveButton(R.string.theme_delete) { _, _ ->
                if (repository.delete(pack)) {
                    setResult(RESULT_OK)
                    if (pack.id == theme.pack.id) {
                        recreate()
                    } else {
                        refresh()
                    }
                }
            }
            .setNegativeButton(R.string.theme_cancel, null)
            .create()
        dialog.setOnShowListener { ThemeApplier.applyDialog(dialog, theme) }
        dialog.show()
    }

    private fun export(pack: ThemePack) {
        pendingExport = pack
        createDocument.launch("${pack.id}.ptheme")
    }

    private fun writeExport(uri: Uri) {
        val pack = pendingExport ?: return
        pendingExport = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { repository.export(pack, it) }
                        ?: error("unable to open the destination")
                }
            }
            result.onSuccess {
                Toast.makeText(this@ThemeActivity, R.string.theme_export_success, Toast.LENGTH_SHORT)
                    .show()
            }.onFailure { error ->
                showError(getString(R.string.theme_export_failed, error.message ?: "unknown"))
            }
        }
    }

    private fun importTheme(uri: Uri) {
        val progress = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.theme_import_running)
            .setCancelable(false)
            .create()
        progress.setOnShowListener { ThemeApplier.applyDialog(progress, theme) }
        progress.show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ThemeImporter(this@ThemeActivity).import(uri) }
            }
            progress.dismiss()
            result.onSuccess(::previewImported).onFailure { error ->
                showError(getString(R.string.theme_import_failed, error.message ?: "unknown"))
            }
        }
    }

    private fun previewImported(imported: ImportedTheme) {
        val existing = repository.find(imported.pack.id)
        if (existing?.builtIn == true) {
            imported.discard()
            showError(getString(R.string.theme_import_builtin_clash))
            return
        }
        ThemePreviewDialog.show(
            activity = this,
            pack = imported.pack,
            theme = repository.resolve(imported.pack, theme.dark),
            onApply = { applyLayout ->
                if (existing != null) {
                    confirmReplace(existing, imported, applyLayout)
                } else {
                    installImported(imported, applyLayout)
                }
            },
            onDismiss = { imported.discard() },
        )
    }

    private fun confirmReplace(
        existing: ThemePack,
        imported: ImportedTheme,
        applyLayout: Boolean,
    ) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_import_replace_title)
            .setMessage(getString(R.string.theme_import_replace_message, existing.id))
            .setPositiveButton(R.string.theme_apply) { _, _ ->
                installImported(imported, applyLayout)
            }
            .setNegativeButton(R.string.theme_cancel) { _, _ ->
                imported.discard()
            }
            .create()
        dialog.setOnShowListener { ThemeApplier.applyDialog(dialog, theme) }
        dialog.show()
    }

    private fun installImported(imported: ImportedTheme, applyLayout: Boolean) {
        try {
            val installed = repository.install(imported.pack, imported.directory)
            imported.discard()
            repository.setActive(installed.id)
            if (applyLayout) {
                repository.applyLayout(installed, (application as PowerTvApp).moduleSettings)
            }
            Toast.makeText(
                this,
                getString(R.string.theme_imported, installed.name()),
                Toast.LENGTH_SHORT,
            ).show()
            setResult(RESULT_OK)
            recreate()
        } catch (error: Exception) {
            imported.discard()
            showError(getString(R.string.theme_import_failed, error.message ?: "unknown"))
        }
    }

    private fun handleExternalIntent(intent: Intent?) {
        if (externalHandled || intent == null) return
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_SEND) return
        val uri = intent.data ?: extraStream(intent) ?: return
        externalHandled = true
        // Avoid re-importing the same file after activity recreation.
        setIntent(Intent())
        importTheme(uri)
    }

    @Suppress("DEPRECATION")
    private fun extraStream(intent: Intent): Uri? =
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    private fun showInfo() {
        showDialog(
            title = getString(R.string.theme_info_title),
            message = getString(R.string.theme_info_body),
        )
    }

    private fun showError(message: String) {
        showDialog(title = getString(R.string.theme_title), message = message)
    }

    private fun showDialog(title: String, message: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.about_ok, null)
            .create()
        dialog.setOnShowListener { ThemeApplier.applyDialog(dialog, theme) }
        dialog.show()
    }
}
