package com.zitzhen.powertv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.model.ProcessInfo
import com.zitzhen.powertv.databinding.ItemProcessBinding

class ProcessAdapter : ListAdapter<ProcessInfo, ProcessAdapter.ProcessViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessViewHolder {
        val binding = ItemProcessBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ProcessViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProcessViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProcessViewHolder(
        private val binding: ItemProcessBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(process: ProcessInfo) {
            val context = binding.root.context
            val initial = process.name.substringAfterLast('.')
                .firstOrNull { it.isLetterOrDigit() }
                ?: process.name.firstOrNull { it.isLetterOrDigit() }
                ?: '#'
            binding.processAvatar.text = initial.uppercase()
            binding.processName.text = process.name
            binding.processMeta.text = context.getString(
                R.string.process_meta,
                process.pid,
                process.uid?.toString() ?: context.getString(R.string.process_uid_unknown),
                process.state,
            )
            binding.processMemory.text = Formatters.bytes(process.memoryBytes)
            binding.processCpu.text = context.getString(
                R.string.process_cpu,
                Formatters.percent(process.cpuPercent, decimals = 1),
            )
            binding.processCpu.setTextColor(LoadPalette.color(context, process.cpuPercent))
            binding.processOwnBadge.isVisible = process.isOwnProcess
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ProcessInfo>() {
            override fun areItemsTheSame(oldItem: ProcessInfo, newItem: ProcessInfo): Boolean =
                oldItem.pid == newItem.pid && oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: ProcessInfo, newItem: ProcessInfo): Boolean =
                oldItem == newItem
        }
    }
}
