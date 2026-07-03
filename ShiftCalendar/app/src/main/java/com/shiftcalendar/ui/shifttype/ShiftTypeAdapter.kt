package com.shiftcalendar.ui.shifttype

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.databinding.ItemShiftTypeBinding

class ShiftTypeAdapter(
    private val onItemClick: (ShiftType) -> Unit,
    private val onItemLongClick: (ShiftType) -> Unit
) : ListAdapter<ShiftType, ShiftTypeAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShiftTypeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemShiftTypeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(shiftType: ShiftType) {
            binding.tvName.text = shiftType.name
            binding.tvTime.text = "${shiftType.startTime} — ${shiftType.endTime}"

            try {
                binding.colorDot.background.setTint(Color.parseColor(shiftType.colorTag))
            } catch (e: Exception) {
                binding.colorDot.background.setTint(Color.parseColor("#4A6FA5"))
            }

            binding.ivAlarm.visibility = if (shiftType.alarmEnabled != false) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            binding.root.setOnClickListener { onItemClick(shiftType) }
            binding.root.setOnLongClickListener {
                onItemLongClick(shiftType)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ShiftType>() {
        override fun areItemsTheSame(oldItem: ShiftType, newItem: ShiftType): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShiftType, newItem: ShiftType): Boolean =
            oldItem == newItem
    }
}