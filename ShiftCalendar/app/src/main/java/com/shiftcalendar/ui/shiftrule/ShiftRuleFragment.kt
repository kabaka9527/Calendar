package com.shiftcalendar.ui.shiftrule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.shiftcalendar.R
import com.shiftcalendar.databinding.FragmentShiftRuleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ShiftRuleFragment : Fragment() {

    private var _binding: FragmentShiftRuleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShiftRuleViewModel by viewModels { ShiftRuleViewModel.Factory() }
    private lateinit var adapter: ShiftRuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShiftRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShiftRuleAdapter(
            onItemClick = { rule -> showEditDialog(rule) },
            onItemLongClick = { rule -> showDeleteConfirm(rule) },
            onGenerateClick = { rule -> confirmGenerate(rule) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShiftRuleFragment.adapter
        }

        binding.fabAdd.setOnClickListener {
            showEditDialog(null)
        }

        viewModel.rules.observe(viewLifecycleOwner) { rules ->
            adapter.submitList(rules)
            binding.emptyHint.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.generationProgress.observe(viewLifecycleOwner) { progress ->
            if (progress.isNotEmpty()) {
                Toast.makeText(requireContext(), progress as CharSequence, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditDialog(rule: com.shiftcalendar.data.entity.ShiftRule?) {
        val dialog = ShiftRuleEditDialogFragment.newInstance(rule)
        dialog.onSaveListener = { rule ->
            viewModel.saveRule(rule)
            // 自动触发生成
            viewModel.generateShifts(rule)
        }
        dialog.show(parentFragmentManager, "edit_rule")
    }

    private fun showDeleteConfirm(rule: com.shiftcalendar.data.entity.ShiftRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除规律")
            .setMessage("确定删除此规律？")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteRule(rule)
            }
            .show()
    }

    private fun confirmGenerate(rule: com.shiftcalendar.data.entity.ShiftRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("生成班次")
            .setMessage("将根据规律从起始日期生成未来 90 天的班次安排，是否继续？")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.generate_shifts) { _, _ ->
                viewModel.generateShifts(rule)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}