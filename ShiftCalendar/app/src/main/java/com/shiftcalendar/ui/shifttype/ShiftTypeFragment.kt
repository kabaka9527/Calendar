package com.shiftcalendar.ui.shifttype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.shiftcalendar.R
import com.shiftcalendar.databinding.FragmentShiftTypeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ShiftTypeFragment : Fragment() {

    private var _binding: FragmentShiftTypeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShiftTypeViewModel by viewModels { ShiftTypeViewModel.Factory() }
    private lateinit var adapter: ShiftTypeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShiftTypeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShiftTypeAdapter(
            onItemClick = { showEditDialog(it) },
            onItemLongClick = { showDeleteConfirm(it) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShiftTypeFragment.adapter
        }

        binding.fabAdd.setOnClickListener {
            showEditDialog(null)
        }

        viewModel.shiftTypes.observe(viewLifecycleOwner) { types ->
            adapter.submitList(types)
            binding.emptyHint.visibility = if (types.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showEditDialog(shiftType: com.shiftcalendar.data.entity.ShiftType?) {
        val dialog = ShiftTypeEditDialogFragment.newInstance(shiftType)
        dialog.onSaveListener = { viewModel.saveShiftType(it) }
        dialog.show(parentFragmentManager, "edit_shift_type")
    }

    private fun showDeleteConfirm(shiftType: com.shiftcalendar.data.entity.ShiftType) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除班次")
            .setMessage(getString(R.string.delete_shift_confirm))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteShiftType(shiftType)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}