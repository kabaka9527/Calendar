package com.shiftcalendar.ui.shiftrule

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shiftcalendar.R
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.databinding.DialogShiftRuleEditBinding
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ShiftRuleEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogShiftRuleEditBinding? = null
    private val binding get() = _binding!!
    private var editingRule: ShiftRule? = null
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

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
            binding.etCycleDays.setText(rule.cycleDays.toString())
            binding.etSequence.setText(rule.shiftSequence)
            binding.tvStartDate.text = dateFormat.format(java.util.Date(rule.startDate))
            binding.tvTitle.text = getString(R.string.edit_rule)
        } ?: run {
            binding.tvTitle.text = getString(R.string.add_rule)
            binding.tvStartDate.text = dateFormat.format(java.util.Date())
        }

        binding.layoutStartDate.setOnClickListener {
            // 使用日期选择器，这里简化处理
            val calendar = Calendar.getInstance()
            binding.tvStartDate.text = dateFormat.format(calendar.time)
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = "请输入规律名称"
                return@setOnClickListener
            }

            val cycleDaysStr = binding.etCycleDays.text.toString().trim()
            val cycleDays = cycleDaysStr.toIntOrNull()
            if (cycleDays == null || cycleDays <= 0) {
                binding.etCycleDays.error = "请输入有效周期天数"
                return@setOnClickListener
            }

            val sequence = binding.etSequence.text.toString().trim()
            if (sequence.isEmpty()) {
                binding.etSequence.error = "请输入班次序列"
                return@setOnClickListener
            }

            try {
                val startDate = try {
                    dateFormat.parse(binding.tvStartDate.text.toString())?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val rule = ShiftRule(
                    id = editingRule?.id ?: 0,
                    name = name,
                    startDate = startDate,
                    cycleDays = cycleDays,
                    shiftSequence = sequence,
                    createdAt = editingRule?.createdAt ?: System.currentTimeMillis()
                )

                onSaveListener?.invoke(rule)
                dismiss()
            } catch (e: Exception) {
                binding.etSequence.error = "序列格式错误"
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }
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