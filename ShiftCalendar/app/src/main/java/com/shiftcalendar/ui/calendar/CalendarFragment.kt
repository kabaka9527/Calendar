package com.shiftcalendar.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.shiftcalendar.R
import com.shiftcalendar.databinding.FragmentCalendarBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels { CalendarViewModel.Factory() }
    private val dateFormat = SimpleDateFormat("yyyy年M月", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNavigation()
        setupViewToggle()
        observeViewModel()
    }

    private fun setupNavigation() {
        binding.btnPrev.setOnClickListener { viewModel.navigateToPrevious() }
        binding.btnNext.setOnClickListener { viewModel.navigateToNext() }
        binding.btnToday.setOnClickListener { viewModel.goToToday() }
    }

    private fun setupViewToggle() {
        binding.btnMonthView.setOnClickListener {
            if (viewModel.isWeekView.value == true) viewModel.toggleView()
        }
        binding.btnWeekView.setOnClickListener {
            if (viewModel.isWeekView.value == false) viewModel.toggleView()
        }
    }

    private fun observeViewModel() {
        viewModel.getCurrentMonthStart().observe(viewLifecycleOwner) { monthStart ->
            binding.tvMonthTitle.text = dateFormat.format(Date(monthStart))
        }

        viewModel.getIsWeekView().observe(viewLifecycleOwner) { isWeek ->
            binding.btnMonthView.isActivated = !isWeek
            binding.btnWeekView.isActivated = isWeek
        }

        // 合并观察 shiftDays, shiftTypes, monthStart, isWeekView
        viewModel.getShiftDays().observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.getShiftTypes().observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.getCurrentMonthStart().observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.getIsWeekView().observe(viewLifecycleOwner) { _ -> renderIfReady() }

        viewModel.selectedDay.observe(viewLifecycleOwner) { detail ->
            detail?.let { showDayDetail(it) }
        }
    }

    private fun renderIfReady() {
        val shiftDays = viewModel.getShiftDays().value ?: return
        val shiftTypes = viewModel.getShiftTypes().value ?: return
        val monthStart = viewModel.getCurrentMonthStart().value ?: return
        val isWeekView = viewModel.getIsWeekView().value ?: return

        val data = CalendarData(shiftDays, shiftTypes, monthStart, isWeekView)
        if (data.isWeekView) {
            renderWeekView(data)
        } else {
            renderMonthView(data)
        }
    }

    private fun renderMonthView(data: CalendarData) {
        binding.weekViewContainer.visibility = View.GONE
        binding.monthViewContainer.visibility = View.VISIBLE
        binding.monthViewContainer.removeAllViews()

        val cal = Calendar.getInstance().apply { timeInMillis = data.monthStart }
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (row in 0 until 6) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            for (col in 0 until 7) {
                val dayIndex = row * 7 + col - firstDayOfWeek
                val cellCal = Calendar.getInstance().apply {
                    timeInMillis = data.monthStart
                    add(Calendar.DAY_OF_MONTH, dayIndex)
                }
                val date = cellCal.timeInMillis
                val isCurrentMonth = cellCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)

                val cellView = createMonthCell(date, isCurrentMonth, date == today, data)
                rowLayout.addView(cellView)
            }

            binding.monthViewContainer.addView(rowLayout)
        }
    }

    private fun createMonthCell(
        date: Long,
        isCurrentMonth: Boolean,
        isToday: Boolean,
        data: CalendarData
    ): View {
        val cell = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(4, 4, 4, 4)
        }

        val dayText = TextView(requireContext()).apply {
            text = SimpleDateFormat("d", Locale.getDefault()).format(Date(date))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            if (!isCurrentMonth) {
                setTextColor(Color.parseColor("#D1D5DB"))
            } else if (isToday) {
                setTextColor(Color.WHITE)
                background = resources.getDrawable(R.drawable.today_circle, null)
            } else {
                setTextColor(Color.parseColor("#1A1A2E"))
            }
        }

        content.addView(dayText)

        val shiftDay = data.shiftDays[date]
        val shiftType = shiftDay?.let { data.shiftTypes[it.shiftTypeId] }

        if (shiftType != null && isCurrentMonth) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(8, 8).apply {
                    topMargin = 4
                }
                try {
                    setBackgroundColor(Color.parseColor(shiftType.colorTag))
                } catch (e: Exception) {
                    setBackgroundColor(Color.parseColor("#4A6FA5"))
                }
            }
            content.addView(dot)

            val nameText = TextView(requireContext()).apply {
                text = shiftType.name
                textSize = 9f
                setTextColor(Color.parseColor(shiftType.colorTag))
                gravity = android.view.Gravity.CENTER
                maxLines = 1
            }
            content.addView(nameText)
        }

        cell.addView(content)
        cell.setOnClickListener {
            if (isCurrentMonth) viewModel.selectDay(date)
        }

        return cell
    }

    private fun renderWeekView(data: CalendarData) {
        binding.monthViewContainer.visibility = View.GONE
        binding.weekViewContainer.visibility = View.VISIBLE
        binding.weekViewContainer.removeAllViews()

        val cal = Calendar.getInstance().apply { timeInMillis = data.monthStart }
        // 调整到周一
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else 2 - dayOfWeek
        cal.add(Calendar.DAY_OF_MONTH, diff)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (i in 0 until 7) {
            val date = cal.timeInMillis
            val shiftDay = data.shiftDays[date]
            val shiftType = shiftDay?.let { data.shiftTypes[it.shiftTypeId] }

            val itemView = createWeekDayItem(date, date == today, shiftType)
            binding.weekViewContainer.addView(itemView)

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun createWeekDayItem(
        date: Long,
        isToday: Boolean,
        shiftType: com.shiftcalendar.data.entity.ShiftType?
    ): View {
        val item = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val dayName = TextView(requireContext()).apply {
            text = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(date))
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = android.view.Gravity.CENTER
        }
        item.addView(dayName)

        val dayNum = TextView(requireContext()).apply {
            text = SimpleDateFormat("d", Locale.getDefault()).format(Date(date))
            textSize = 16f
            if (isToday) {
                setTextColor(Color.WHITE)
                background = resources.getDrawable(R.drawable.today_circle_small, null)
            } else {
                setTextColor(Color.parseColor("#1A1A2E"))
            }
            gravity = android.view.Gravity.CENTER
            setPadding(8, 4, 8, 4)
        }
        item.addView(dayNum)

        if (shiftType != null) {
            val shiftText = TextView(requireContext()).apply {
                text = shiftType.name
                textSize = 12f
                try {
                    setTextColor(Color.parseColor(shiftType.colorTag))
                } catch (e: Exception) {
                    setTextColor(Color.parseColor("#4A6FA5"))
                }
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            item.addView(shiftText)

            val timeText = TextView(requireContext()).apply {
                text = "${shiftType.startTime}-${shiftType.endTime}"
                textSize = 10f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = android.view.Gravity.CENTER
            }
            item.addView(timeText)
        }

        item.setOnClickListener { viewModel.selectDay(date) }
        return item
    }

    private fun showDayDetail(detail: ShiftDayDetail) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_ShiftCalendar_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_day_detail, null)

        view.findViewById<TextView>(R.id.tvDate).text =
            fullDateFormat.format(Date(detail.date))

        val shiftType = detail.shiftType
        if (shiftType != null) {
            view.findViewById<TextView>(R.id.tvShiftName).text = shiftType.name
            view.findViewById<TextView>(R.id.tvShiftTime).text =
                "${shiftType.startTime} — ${shiftType.endTime}"
            try {
                view.findViewById<View>(R.id.colorIndicator)
                    .setBackgroundColor(Color.parseColor(shiftType.colorTag))
            } catch (_: Exception) {}
            view.findViewById<TextView>(R.id.tvNote).text = detail.note.ifEmpty { "无备注" }
        } else {
            view.findViewById<TextView>(R.id.tvShiftName).text = getString(R.string.no_shift)
            view.findViewById<TextView>(R.id.tvShiftTime).text = ""
            view.findViewById<TextView>(R.id.tvNote).text = ""
        }

        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}