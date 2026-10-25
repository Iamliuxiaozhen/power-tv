package com.zitzhen.powertv.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.settings.DashboardModule
import com.zitzhen.powertv.data.settings.ModuleSettingsRepository
import com.zitzhen.powertv.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: ModuleSettingsRepository
    private lateinit var adapter: ModuleAdapter

    private val order = mutableListOf<DashboardModule>()
    private val visible = mutableSetOf<DashboardModule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ModuleSettingsRepository(this)
        order.addAll(repository.loadOrder())
        visible.addAll(repository.loadVisible())

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ModuleAdapter(
            items = order,
            visible = visible,
            onVisibilityChanged = { module, isVisible ->
                if (isVisible) visible.add(module) else visible.remove(module)
                persist()
            },
        )
        binding.modulesList.layoutManager = LinearLayoutManager(this)
        binding.modulesList.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                val item = order.removeAt(from)
                order.add(to, item)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                persist()
            }
        })
        touchHelper.attachToRecyclerView(binding.modulesList)
        adapter.touchHelper = touchHelper

        binding.resetButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_title)
                .setMessage(R.string.settings_reset_message)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    repository.reset()
                    order.clear()
                    order.addAll(DashboardModule.DEFAULT_ORDER)
                    visible.clear()
                    visible.addAll(DashboardModule.DEFAULT_VISIBLE)
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton(R.string.about_ok, null)
                .show()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun persist() {
        repository.save(order.toList(), visible.toSet())
    }
}
