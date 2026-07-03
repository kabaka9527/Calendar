package com.shiftcalendar.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.shiftcalendar.R
import com.shiftcalendar.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel

    private val RINGTONE_REQUEST_CODE = 2001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(requireActivity().application))[SettingsViewModel::class.java]

        // 闹钟启用开关
        binding.switchAlarmEnabled.isChecked = viewModel.settings.enabled
        binding.switchAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.settings.enabled = isChecked
            viewModel.onSettingChanged()
        }

        // 提前提醒时间
        val leadMinutes = viewModel.settings.leadMinutes
        when (leadMinutes) {
            0 -> binding.chipLead0.isChecked = true
            15 -> binding.chipLead15.isChecked = true
            30 -> binding.chipLead30.isChecked = true
            60 -> binding.chipLead60.isChecked = true
        }
        binding.chipLead0.setOnClickListener { setLeadMinutes(0) }
        binding.chipLead15.setOnClickListener { setLeadMinutes(15) }
        binding.chipLead30.setOnClickListener { setLeadMinutes(30) }
        binding.chipLead60.setOnClickListener { setLeadMinutes(60) }

        // 振动开关
        binding.switchVibrate.isChecked = viewModel.settings.vibrate
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.settings.vibrate = isChecked
            viewModel.onSettingChanged()
        }

        // 铃声选择
        updateRingtoneLabel()
        binding.layoutRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                val currentUri = viewModel.settings.ringtoneUri
                if (currentUri.isNotEmpty()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                }
            }
            startActivityForResult(intent, RINGTONE_REQUEST_CODE)
        }
    }

    private fun setLeadMinutes(minutes: Int) {
        viewModel.settings.leadMinutes = minutes
        binding.chipLead0.isChecked = minutes == 0
        binding.chipLead15.isChecked = minutes == 15
        binding.chipLead30.isChecked = minutes == 30
        binding.chipLead60.isChecked = minutes == 60
        viewModel.onSettingChanged()
    }

    private fun updateRingtoneLabel() {
        val uri = viewModel.settings.ringtoneUri
        if (uri.isEmpty()) {
            binding.tvRingtoneValue.text = getString(R.string.settings_ringtone_default)
        } else {
            val ringtone = RingtoneManager.getRingtone(requireContext(), Uri.parse(uri))
            val title = ringtone?.getTitle(requireContext()) ?: getString(R.string.settings_ringtone_default)
            binding.tvRingtoneValue.text = title
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RINGTONE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.settings.ringtoneUri = uri?.toString() ?: ""
            updateRingtoneLabel()
            viewModel.onSettingChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}