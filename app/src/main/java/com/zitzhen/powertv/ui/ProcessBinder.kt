package com.zitzhen.powertv.ui

import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.model.ProcessInfo
import com.zitzhen.powertv.data.model.ProcessSnapshot
import com.zitzhen.powertv.databinding.ScreenProcessesBinding

enum class ProcessSort { MEMORY, CPU }

/** Binds process snapshots to the RecyclerView and sort controls. */
class ProcessBinder(private val binding: ScreenProcessesBinding) {

    private val adapter = ProcessAdapter()
    private var sort = ProcessSort.MEMORY
    private var snapshot: ProcessSnapshot? = null

    init {
        binding.processList.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@ProcessBinder.adapter
            itemAnimator = null
        }
        binding.sortGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            sort = if (checkedIds.firstOrNull() == R.id.sort_cpu) {
                ProcessSort.CPU
            } else {
                ProcessSort.MEMORY
            }
            submit()
        }
    }

    fun bind(state: MonitorUiState) {
        val processes = state.snapshot?.processes ?: return
        if (snapshot === processes) return
        snapshot = processes

        binding.processesCount.text = binding.root.context.getString(
            R.string.processes_count,
            processes.count,
        )
        binding.processesUpdated.apply {
            isVisible = processes.sampledAtMillis > 0L
            text = binding.root.context.getString(
                R.string.processes_updated,
                Formatters.timeOfDay(processes.sampledAtMillis),
            )
        }
        binding.processEmpty.isVisible = processes.processes.isEmpty()
        binding.processList.isVisible = processes.processes.isNotEmpty()
        submit()
    }

    private fun submit() {
        val processes = snapshot?.processes ?: emptyList()
        val sorted = when (sort) {
            ProcessSort.MEMORY -> processes.sortedWith(
                compareByDescending<ProcessInfo> { it.memoryBytes }
                    .thenByDescending { it.cpuPercent ?: 0f },
            )
            ProcessSort.CPU -> processes.sortedWith(
                compareByDescending<ProcessInfo> { it.cpuPercent ?: 0f }
                    .thenByDescending { it.memoryBytes },
            )
        }
        adapter.submitList(sorted)
    }
}
