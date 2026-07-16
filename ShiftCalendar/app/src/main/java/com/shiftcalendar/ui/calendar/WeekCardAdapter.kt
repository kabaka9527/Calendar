package com.shiftcalendar.ui.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shiftcalendar.R
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.databinding.ItemWeekCardBinding

data class WeekCardData(
    val date: Long,
    val weekDayName: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val shiftType: ShiftType?
)

class WeekCardAdapter(
    private val onItemClick: (Long) -> Unit
) : RecyclerView.Adapter<WeekCardAdapter.ViewHolder>() {

    private var items: List<WeekCardData> = emptyList()

    fun submitList(newItems: List<WeekCardData>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemWeekCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: WeekCardData) {
            val ctx = binding.root.context
            val colorPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
            val colorAccent = ContextCompat.getColor(ctx, R.color.accent)

            binding.tvWeekDay.text = data.weekDayName
            binding.tvDay.text = data.dayNumber.toString()

            // 今日高亮
            if (data.isToday) {
                binding.tvDay.setBackgroundResource(R.drawable.today_badge_week)
                binding.tvDay.setTextColor(Color.WHITE)
            } else {
                binding.tvDay.background = null
                binding.tvDay.setTextColor(colorPrimary)
            }

            // 班次信息
            val shiftType = data.shiftType
            if (shiftType != null) {
                val color = try {
                    Color.parseColor(shiftType.colorTag)
                } catch (_: Exception) {
                    colorAccent
                }
                binding.shiftColorBar.visibility = View.VISIBLE
                binding.shiftColorBar.backgroundTintList = ColorStateList.valueOf(color)
                binding.tvShiftName.visibility = View.VISIBLE
                binding.tvShiftName.text = shiftType.name
                binding.tvShiftName.setTextColor(color)
                binding.tvShiftTime.visibility = View.VISIBLE
                binding.tvShiftTime.text = "${shiftType.startTime}-${shiftType.endTime}"
            } else {
                binding.shiftColorBar.visibility = View.GONE
                binding.tvShiftName.visibility = View.GONE
                binding.tvShiftTime.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(data.date) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeekCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // 每张卡片占 RecyclerView 宽度的 1/7
        val cardWidth = (parent.width / 7).coerceAtLeast(1)
        binding.root.layoutParams = RecyclerView.LayoutParams(cardWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
