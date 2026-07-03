package com.shiftcalendar.ui.shiftrule

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.shiftcalendar.R
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.databinding.DialogShiftRuleEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ShiftRuleEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogShiftRuleEditBinding? = null
    private val binding get() = _binding!!
    private var editingRule: ShiftRule? = null
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    // 选中的班次类型序列（按点击顺序），序列长度即倒班周期
    private val selectedSequence = mutableListOf<Long>()
    private var shiftTypes: List<ShiftType> = emptyList()

    var onSaveListener: ((ShiftRule) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.Theme_ShiftCalendar_BottomSheet).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogShiftRuleEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingRule = arguments?.let {
            if (it.containsKey("rule")) {
                it.getSerializable("rule") as? ShiftRule
            } else null
        }

        editingRule?.let { rule ->
            binding.etName.setText(rule.name)
            binding.tvStartDate.text = dateFormat.format(java.util.Date(rule.startDate))
            binding.tvTitle.text = getString(R.string.edit_rule)
            // 解析已有序列
            selectedSequence.clear()
            selectedSequence.addAll(parseSequence(rule.shiftSequence))
        } ?: run {
            binding.tvTitle.text = getString(R.string.add_rule)
            binding.tvStartDate.text = dateFormat.format(java.util.Date())
        }

        binding.layoutStartDate.setOnClickListener { showDatePicker() }

        binding.btnSequenceClear.setOnClickListener {
            selectedSequence.clear()
            updateSequencePreview()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = "请输入规律名称"
                return@setOnClickListener
            }

            if (selectedSequence.isEmpty()) {
                binding.tvSequencePreview.text = "请选择至少一个班次"
                return@setOnClickListener
            }

            val startDate = try {
                dateFormat.parse(binding.tvStartDate.text.toString())?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            // 倒班周期 = 班次序列长度，一个班次结束自动接续下一个
            val rule = ShiftRule(
                id = editingRule?.id ?: 0,
                name = name,
                startDate = startDate,
                cycleDays = selectedSequence.size,
                shiftSequence = selectedSequence.joinToString(","),
                createdAt = editingRule?.createdAt ?: System.currentTimeMillis()
            )

            onSaveListener?.invoke(rule)
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        loadShiftTypes()
    }

    private fun showDatePicker() {
        val currentSelection = try {
            dateFormat.parse(binding.tvStartDate.text.toString())?.time
                ?: MaterialDatePicker.todayInUtcMilliseconds()
        } catch (e: Exception) {
            MaterialDatePicker.todayInUtcMilliseconds()
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(currentSelection)
            .setTitleText(getString(R.string.start_date))
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            // MaterialDatePicker 返回 UTC 毫秒，转换为本地时区当天的 00:00
            val calendar = Calendar.getInstance().apply {
                timeInMillis = selection
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            binding.tvStartDate.text = dateFormat.format(calendar.time)
        }

        datePicker.show(parentFragmentManager, "start_date")
    }

    private fun loadShiftTypes() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val types = ShiftCalendarApp.instance.database.shiftTypeDao()
                    .getAllStaticList()
                withContext(Dispatchers.Main) {
                    shiftTypes = types
                    setupSequenceChips()
                    updateSequencePreview()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvSequencePreview.text = getString(R.string.shift_sequence_empty)
                }
            }
        }
    }

    private fun setupSequenceChips() {
        binding.chipGroupSequence.removeAllViews()

        if (shiftTypes.isEmpty()) {
            binding.tvSequencePreview.text = getString(R.string.shift_sequence_empty)
            return
        }

        shiftTypes.forEach { shiftType ->
            val chip = Chip(requireContext()).apply {
                text = shiftType.name
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    selectedSequence.add(shiftType.id)
                    updateSequencePreview()
                }
            }
            binding.chipGroupSequence.addView(chip)
        }
    }

    private fun updateSequencePreview() {
        if (selectedSequence.isEmpty()) {
            binding.tvSequencePreview.text = ""
            binding.tvCycleInfo.text = ""
            return
        }

        val typeMap = shiftTypes.associateBy { it.id }
        val names = selectedSequence.mapIndexed { index, id ->
            val name = typeMap[id]?.name ?: "未知"
            "${index + 1}. $name"
        }
        binding.tvSequencePreview.text = names.joinToString("  →  ")

        // 显示倒班周期信息
        binding.tvCycleInfo.text = getString(R.string.cycle_info_format, selectedSequence.size)
    }

    private fun parseSequence(sequence: String): List<Long> {
        return try {
            sequence.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toLong() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(rule: ShiftRule?): ShiftRuleEditDialogFragment {
            return ShiftRuleEditDialogFragment().apply {
                arguments = Bundle().apply {
                    if (rule != null) {
                        putSerializable("rule", rule)
                    }
                }
            }
        }
    }
}
