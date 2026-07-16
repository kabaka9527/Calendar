package com.shiftcalendar.ui.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shiftcalendar.R
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.databinding.ItemMonthCellBinding

data class MonthCellData(
    val date: Long,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val shiftDay: ShiftDay?,
    val shiftType: ShiftType?
)

class MonthCellAdapter(
    private val onItemClick: (Long) -> Unit
) : RecyclerView.Adapter<MonthCellAdapter.ViewHolder>() {

    private var items: List<MonthCellData> = emptyList()

    fun submitList(newItems: List<MonthCellData>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemMonthCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: MonthCellData) {
            binding.tvDay.text = data.dayNumber.toString()

            // 背景：今日高亮 > 非本月 > 普通
            when {
                data.isToday -> {
                    binding.cardRoot.setBackgroundResource(R.drawable.month_cell_bg_today)
                    binding.tvDay.setBackgroundResource(R.drawable.today_badge_month)
                    binding.tvDay.setTextColor(Color.WHITE)
                }
                !data.isCurrentMonth -> {
                    binding.cardRoot.setBackgroundResource(R.drawable.month_cell_bg_other)
                    binding.tvDay.background = null
                    binding.tvDay.setTextColor(Color.parseColor("#D1D5DB"))
                }
                else -> {
                    binding.cardRoot.setBackgroundResource(R.drawable.month_cell_bg)
                    binding.tvDay.background = null
                    binding.tvDay.setTextColor(Color.parseColor("#1A1A2E"))
                }
            }

            // 班次色条 + 名称
            val shiftType = data.shiftType
            if (shiftType != null && data.isCurrentMonth) {
                val color = try {
                    Color.parseColor(shiftType.colorTag)
                } catch (_: Exception) {
                    Color.parseColor("#4A6FA5")
                }
                binding.shiftColorBar.visibility = View.VISIBLE
                binding.shiftColorBar.setBackgroundColor(color)
                binding.tvShiftName.visibility = View.VISIBLE
                binding.tvShiftName.text = shiftType.name
                binding.tvShiftName.setTextColor(color)
            } else {
                binding.shiftColorBar.visibility = View.GONE
                binding.tvShiftName.visibility = View.GONE
            }

            // 备注标记
            val hasNote = data.shiftDay?.note?.isNotEmpty() == true && data.isCurrentMonth
            binding.noteDot.visibility = if (hasNote) View.VISIBLE else View.GONE

            // 点击
            itemView.setOnClickListener {
                if (data.isCurrentMonth) onItemClick(data.date)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMonthCellBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
