package com.shiftcalendar.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
    private val weekDayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    private lateinit var monthAdapter: MonthCellAdapter
    private lateinit var weekAdapter: WeekCardAdapter

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
        setupRecyclerViews()
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

    private fun setupRecyclerViews() {
        monthAdapter = MonthCellAdapter { date -> viewModel.selectDay(date) }
        binding.monthRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = monthAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        weekAdapter = WeekCardAdapter { date -> viewModel.selectDay(date) }
        binding.weekRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = weekAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun observeViewModel() {
        viewModel.currentMonthStart.observe(viewLifecycleOwner) { monthStart ->
            binding.tvMonthTitle.text = dateFormat.format(Date(monthStart))
        }

        viewModel.isWeekView.observe(viewLifecycleOwner) { isWeek ->
            binding.btnMonthView.isActivated = !isWeek
            binding.btnWeekView.isActivated = isWeek
            val colorPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
            val colorSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            if (isWeek) {
                binding.btnMonthView.setTextColor(colorSecondary)
                binding.btnWeekView.setTextColor(colorPrimary)
            } else {
                binding.btnMonthView.setTextColor(colorPrimary)
                binding.btnWeekView.setTextColor(colorSecondary)
            }
        }

        viewModel.shiftDays.observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.shiftTypes.observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.currentMonthStart.observe(viewLifecycleOwner) { _ -> renderIfReady() }
        viewModel.isWeekView.observe(viewLifecycleOwner) { _ -> renderIfReady() }

        viewModel.selectedDay.observe(viewLifecycleOwner) { detail ->
            detail?.let { showDayDetail(it) }
        }
    }

    private fun renderIfReady() {
        val shiftDays = viewModel.shiftDays.value ?: return
        val shiftTypes = viewModel.shiftTypes.value ?: return
        val monthStart = viewModel.currentMonthStart.value ?: return
        val isWeekView = viewModel.isWeekView.value ?: return

        if (isWeekView) {
            renderWeekView(shiftDays, shiftTypes, monthStart)
        } else {
            renderMonthView(shiftDays, shiftTypes, monthStart)
        }
    }

    private fun renderMonthView(
        shiftDays: Map<Long, com.shiftcalendar.data.entity.ShiftDay>,
        shiftTypes: Map<Long, com.shiftcalendar.data.entity.ShiftType>,
        monthStart: Long
    ) {
        binding.weekRecyclerView.visibility = View.GONE
        binding.monthRecyclerView.visibility = View.VISIBLE

        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cells = mutableListOf<MonthCellData>()
        for (row in 0 until 6) {
            for (col in 0 until 7) {
                val dayIndex = row * 7 + col - firstDayOfWeek
                val cellCal = Calendar.getInstance().apply {
                    timeInMillis = monthStart
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, dayIndex)
                }
                val date = cellCal.timeInMillis
                val isCurrentMonth = cellCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
                val shiftDay = shiftDays[date]
                val shiftType = shiftDay?.let { shiftTypes[it.shiftTypeId] }

                cells.add(
                    MonthCellData(
                        date = date,
                        dayNumber = cellCal.get(Calendar.DAY_OF_MONTH),
                        isCurrentMonth = isCurrentMonth,
                        isToday = date == today,
                        shiftDay = shiftDay,
                        shiftType = shiftType
                    )
                )
            }
        }
        monthAdapter.submitList(cells)
    }

    private fun renderWeekView(
        shiftDays: Map<Long, com.shiftcalendar.data.entity.ShiftDay>,
        shiftTypes: Map<Long, com.shiftcalendar.data.entity.ShiftType>,
        monthStart: Long
    ) {
        binding.monthRecyclerView.visibility = View.GONE
        binding.weekRecyclerView.visibility = View.VISIBLE

        val cal = Calendar.getInstance().apply {
            timeInMillis = monthStart
            // 调整到本周周一
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else 2 - dayOfWeek
            add(Calendar.DAY_OF_MONTH, diff)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cards = mutableListOf<WeekCardData>()
        for (i in 0 until 7) {
            val date = cal.timeInMillis
            val shiftDay = shiftDays[date]
            val shiftType = shiftDay?.let { shiftTypes[it.shiftTypeId] }

            cards.add(
                WeekCardData(
                    date = date,
                    weekDayName = weekDayFormat.format(Date(date)),
                    dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                    isToday = date == today,
                    shiftType = shiftType
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        weekAdapter.submitList(cards)
    }

    private fun showDayDetail(detail: ShiftDayDetail) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_ShiftCalendar_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_day_detail, null)

        view.findViewById<TextView>(R.id.tvDate).text =
            fullDateFormat.format(Date(detail.date))

        val shiftType = detail.shiftType
        val colorIndicator = view.findViewById<View>(R.id.colorIndicator)
        if (shiftType != null) {
            view.findViewById<TextView>(R.id.tvShiftName).text = shiftType.name
            view.findViewById<TextView>(R.id.tvShiftTime).text =
                "${shiftType.startTime} — ${shiftType.endTime}"
            try {
                colorIndicator.setBackgroundColor(Color.parseColor(shiftType.colorTag))
                colorIndicator.visibility = View.VISIBLE
            } catch (_: Exception) {
                colorIndicator.visibility = View.GONE
            }
        } else {
            view.findViewById<TextView>(R.id.tvShiftName).text = getString(R.string.no_shift)
            view.findViewById<TextView>(R.id.tvShiftTime).text = ""
            colorIndicator.visibility = View.GONE
        }

        val etNote = view.findViewById<android.widget.EditText>(R.id.etNote)
        etNote.setText(detail.note)

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveNote)
            .setOnClickListener {
                val note = etNote.text.toString().trim()
                val shiftTypeId = detail.shiftType?.id
                if (shiftTypeId != null) {
                    viewModel.changeShiftType(detail.date, shiftTypeId, note)
                } else {
                    viewModel.saveNote(detail.date, note)
                }
                android.widget.Toast.makeText(requireContext(), "备注已保存", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

        view.findViewById<TextView>(R.id.tvShiftName).setOnClickListener {
            showShiftTypePicker(dialog, detail, etNote)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showShiftTypePicker(
        parentDialog: BottomSheetDialog,
        detail: ShiftDayDetail,
        etNote: android.widget.EditText
    ) {
        val shiftTypes = viewModel.shiftTypes.value ?: emptyMap()
        if (shiftTypes.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), R.string.empty_shift_hint, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 按 sortOrder 排序，保证列表顺序一致
        val typeList = shiftTypes.values.sortedBy { it.sortOrder }
        val items = typeList.map { it.name }.toTypedArray()
        val checkedItem = typeList.indexOfFirst { it.id == detail.shiftType?.id }

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_ShiftCalendar)
            .setTitle(R.string.select_shift)
            .setSingleChoiceItems(items, checkedItem) { dlg, which ->
                val selected = typeList[which]
                val note = etNote.text?.toString()?.trim() ?: ""
                viewModel.changeShiftType(detail.date, selected.id, note)
                dlg.dismiss()
                parentDialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
