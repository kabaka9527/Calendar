package com.shiftcalendar.ui.shifttype

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.shiftcalendar.R
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.databinding.DialogShiftTypeEditBinding

class ShiftTypeEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogShiftTypeEditBinding? = null
    private val binding get() = _binding!!
    private var editingShiftType: ShiftType? = null
    private var selectedColor: String = "#4A6FA5"

    // 时间状态：hour * 60 + minute
    private var startMinutes: Int = 8 * 60      // 默认 08:00
    private var endMinutes: Int = 16 * 60       // 默认 16:00

    var onSaveListener: ((ShiftType) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.Theme_ShiftCalendar_BottomSheet).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogShiftTypeEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingShiftType = arguments?.let {
            if (it.containsKey("shiftType")) {
                it.getSerializable("shiftType") as? ShiftType
            } else null
        }

        editingShiftType?.let { shiftType ->
            binding.etName.setText(shiftType.name)
            startMinutes = parseTimeToMinutes(shiftType.startTime)
            endMinutes = parseTimeToMinutes(shiftType.endTime)
            selectedColor = shiftType.colorTag
            updateColorPreview()
            binding.tvTitle.text = getString(R.string.edit_shift)
            binding.switchAlarmEnabled.isChecked = shiftType.alarmEnabled != false
        } ?: run {
            binding.tvTitle.text = getString(R.string.add_shift)
            binding.switchAlarmEnabled.isChecked = true
        }

        updateTimeDisplay()

        val colorMap = mapOf(
            binding.colorMorning to "#7BA587",
            binding.colorAfternoon to "#E8A87C",
            binding.colorNight to "#6C7BA6",
            binding.colorRest to "#A0A0A0",
            binding.colorDefault to "#4A6FA5"
        )

        colorMap.forEach { (view, color) ->
            view.background?.let { bg ->
                DrawableCompat.setTint(bg.mutate(), Color.parseColor(color))
            }
            view.setOnClickListener {
                selectedColor = color
                updateColorPreview()
            }
        }

        binding.layoutStartTime.setOnClickListener { showTimePicker(isStart = true) }
        binding.layoutEndTime.setOnClickListener { showTimePicker(isStart = false) }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = "请输入班次名称"
                return@setOnClickListener
            }

            val shiftType = ShiftType(
                id = editingShiftType?.id ?: 0,
                name = name,
                colorTag = selectedColor,
                startTime = formatMinutes(startMinutes),
                endTime = formatMinutes(endMinutes),
                sortOrder = editingShiftType?.sortOrder ?: 0,
                alarmEnabled = binding.switchAlarmEnabled.isChecked
            )

            onSaveListener?.invoke(shiftType)
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun showTimePicker(isStart: Boolean) {
        val current = if (isStart) startMinutes else endMinutes
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current / 60)
            .setMinute(current % 60)
            .setTitleText(if (isStart) getString(R.string.shift_start_time) else getString(R.string.shift_end_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            val minutes = picker.hour * 60 + picker.minute
            if (isStart) {
                startMinutes = minutes
            } else {
                endMinutes = minutes
            }
            updateTimeDisplay()
        }

        picker.show(parentFragmentManager, if (isStart) "start_time" else "end_time")
    }

    private fun updateTimeDisplay() {
        binding.tvStartTime.text = formatMinutes(startMinutes)
        binding.tvEndTime.text = formatMinutes(endMinutes)
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            if (parts.size == 2) {
                parts[0].toInt() * 60 + parts[1].toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun updateColorPreview() {
        try {
            binding.colorPreview.setBackgroundColor(Color.parseColor(selectedColor))
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(shiftType: ShiftType?): ShiftTypeEditDialogFragment {
            return ShiftTypeEditDialogFragment().apply {
                arguments = Bundle().apply {
                    if (shiftType != null) {
                        putSerializable("shiftType", shiftType)
                    }
                }
            }
        }
    }
}
