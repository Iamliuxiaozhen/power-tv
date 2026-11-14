package com.zitzhen.powertv.ui.theme

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.theme.ResolvedTheme
import com.zitzhen.powertv.data.theme.ThemePack
import com.zitzhen.powertv.data.theme.ThemeQuality
import com.zitzhen.powertv.data.theme.ThemeRepository
import com.zitzhen.powertv.databinding.ItemThemeBinding

class ThemeAdapter(
    private val repository: ThemeRepository,
    private val theme: ResolvedTheme,
    private val onPreview: (ThemePack) -> Unit,
    private val onExport: (ThemePack) -> Unit,
    private val onDelete: (ThemePack) -> Unit,
) : RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {

    private var items: List<ThemePack> = emptyList()
    private var activeId: String = theme.pack.id
    private var styler: ThemeApplier.Styler? = null

    inner class ViewHolder(val binding: ItemThemeBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun submit(packs: List<ThemePack>, activeId: String) {
        items = packs
        this.activeId = activeId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemThemeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        val viewStyler = styler ?: ThemeApplier.styler(parent.context, theme).also { styler = it }
        viewStyler.apply(binding.root)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pack = items[position]
        val resolved = repository.resolve(pack, theme.dark)
        with(holder.binding) {
            themeSwatch.bind(if (theme.dark) pack.dark else pack.light)
            themeSwatch.contentDescription = pack.name()
            themeName.text = pack.name()
            themeMeta.text = buildMeta(pack, holder.itemView.context)
            themeActiveBadge.isVisible = pack.id == activeId
            themeContrastBadge.isVisible = ThemeQuality.hasLowContrast(resolved)
            themeShare.setOnClickListener { onExport(pack) }
            themeDelete.isVisible = !pack.builtIn
            themeDelete.setOnClickListener { onDelete(pack) }
            root.setOnClickListener { onPreview(pack) }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun buildMeta(pack: ThemePack, context: android.content.Context): String {
        val author = pack.author
        return when {
            pack.builtIn -> if (author.isNullOrBlank()) {
                context.getString(R.string.theme_builtin)
            } else {
                context.getString(R.string.theme_builtin_line, author)
            }
            author.isNullOrBlank() -> "v${pack.version}"
            else -> context.getString(R.string.theme_author_line, author, pack.version)
        }
    }
}
