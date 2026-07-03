package com.shiftcalendar.ui.shiftrule

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import java.util.Collections
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
                binding.tvCycleInfo.text = "请选择至少一个班次"
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
                    binding.tvCycleInfo.text = getString(R.string.shift_sequence_empty)
                }
            }
        }
    }

    private fun setupSequenceChips() {
        binding.chipGroupSequence.removeAllViews()

        if (shiftTypes.isEmpty()) {
            binding.tvCycleInfo.text = getString(R.string.shift_sequence_empty)
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
        binding.sequenceListContainer.removeAllViews()

        if (selectedSequence.isEmpty()) {
            binding.tvCycleInfo.text = ""
            return
        }

        val typeMap = shiftTypes.associateBy { it.id }

        selectedSequence.forEachIndexed { index, id ->
            val item = createSequenceItem(index, id, typeMap)
            binding.sequenceListContainer.addView(item)
        }

        // 显示倒班周期信息
        binding.tvCycleInfo.text = getString(R.string.cycle_info_format, selectedSequence.size)
    }

    private fun createSequenceItem(
        index: Int,
        shiftTypeId: Long,
        typeMap: Map<Long, ShiftType>
    ): View {
        val item = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            val bg = resources.getDrawable(R.drawable.shift_pill_bg, null).mutate()
            bg.setColorFilter(Color.parseColor("#F0F1F3"), android.graphics.PorterDuff.Mode.SRC_IN)
            background = bg
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6
            }
            layoutParams = params
        }

        // 序号
        val indexText = TextView(requireContext()).apply {
            text = "${index + 1}."
            textSize = 13f
            setTextColor(Color.parseColor("#9CA3AF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
        }
        item.addView(indexText)

        // 班次色点
        val shiftType = typeMap[shiftTypeId]
        val colorDot = View(requireContext()).apply {
            val color = try {
                Color.parseColor(shiftType?.colorTag ?: "#4A6FA5")
            } catch (e: Exception) {
                Color.parseColor("#4A6FA5")
            }
            setBackgroundColor(color)
            val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
            background = dotDrawable
            layoutParams = LinearLayout.LayoutParams(20, 20).apply { marginEnd = 8 }
        }
        item.addView(colorDot)

        // 班次名
        val nameText = TextView(requireContext()).apply {
            text = shiftType?.name ?: "未知"
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }
        item.addView(nameText)

        // 上移按钮
        val btnUp = android.widget.ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_up_float)
            background = null
            setPadding(8, 8, 8, 8)
            isEnabled = index > 0
            alpha = if (index > 0) 1f else 0.3f
            setOnClickListener {
                if (index > 0) {
                    Collections.swap(selectedSequence, index, index - 1)
                    updateSequencePreview()
                }
            }
        }
        item.addView(btnUp)

        // 下移按钮
        val btnDown = android.widget.ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            background = null
            setPadding(8, 8, 8, 8)
            isEnabled = index < selectedSequence.size - 1
            alpha = if (index < selectedSequence.size - 1) 1f else 0.3f
            setOnClickListener {
                if (index < selectedSequence.size - 1) {
                    Collections.swap(selectedSequence, index, index + 1)
                    updateSequencePreview()
                }
            }
        }
        item.addView(btnDown)

        // 删除按钮
        val btnDelete = android.widget.ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                selectedSequence.removeAt(index)
                updateSequencePreview()
            }
        }
        item.addView(btnDelete)

        return item
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
