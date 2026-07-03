package com.shiftcalendar.ui.shiftrule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.databinding.ItemShiftRuleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShiftRuleAdapter(
    private val onItemClick: (ShiftRule) -> Unit,
    private val onItemLongClick: (ShiftRule) -> Unit,
    private val onGenerateClick: (ShiftRule) -> Unit
) : ListAdapter<ShiftRule, ShiftRuleAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShiftRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemShiftRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: ShiftRule) {
            binding.tvName.text = rule.name
            binding.tvStartDate.text = "起始: ${dateFormat.format(Date(rule.startDate))}"
            binding.tvCycle.text = "${rule.cycleDays}天周期"
            binding.tvSequence.text = "序列: ${rule.shiftSequence}"

            binding.root.setOnClickListener { onItemClick(rule) }
            binding.root.setOnLongClickListener {
                onItemLongClick(rule)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ShiftRule>() {
        override fun areItemsTheSame(oldItem: ShiftRule, newItem: ShiftRule): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShiftRule, newItem: ShiftRule): Boolean =
            oldItem == newItem
    }
}