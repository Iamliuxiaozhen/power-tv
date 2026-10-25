package com.zitzhen.powertv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zitzhen.powertv.data.settings.DashboardModule
import com.zitzhen.powertv.databinding.ItemModuleBinding

class ModuleAdapter(
    private val items: List<DashboardModule>,
    private val visible: MutableSet<DashboardModule>,
    private val onVisibilityChanged: (DashboardModule, Boolean) -> Unit,
) : RecyclerView.Adapter<ModuleAdapter.ViewHolder>() {

    var touchHelper: ItemTouchHelper? = null

    inner class ViewHolder(val binding: ItemModuleBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val module = items[position]
        with(holder.binding) {
            moduleTitle.setText(module.titleRes)
            moduleVisible.setOnCheckedChangeListener(null)
            moduleVisible.isChecked = module in visible
            moduleTitle.alpha = if (moduleVisible.isChecked) 1f else 0.5f
            moduleVisible.setOnCheckedChangeListener { _, isChecked ->
                onVisibilityChanged(module, isChecked)
                moduleTitle.alpha = if (isChecked) 1f else 0.5f
            }
            dragHandle.setOnTouchListener { _, _ ->
                touchHelper?.startDrag(holder)
                false
            }
        }
    }

    override fun getItemCount() = items.size
}
