package com.shiftcalendar.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.shiftcalendar.R
import com.shiftcalendar.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel

    // 通知权限申请：仅在用户启用闹钟时发起（Android 13+ 需要）
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                context?.let {
                    Toast.makeText(
                        it, "未授予通知权限，闹钟提醒将无法显示", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    // 铃声选择
    private val pickRingtone =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && _binding != null && ::viewModel.isInitialized) {
                @Suppress("DEPRECATION")
                val uri = result.data
                    ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                viewModel.settings.ringtoneUri = uri?.toString() ?: ""
                updateRingtoneLabel()
                viewModel.onSettingChanged()
            }
        }

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

        viewModel = ViewModelProvider(
            this, SettingsViewModel.Factory(requireActivity().application)
        )[SettingsViewModel::class.java]

        // 闹钟启用开关 —— 必须先设初始值再注册监听，避免初始化时误触发权限申请
        binding.switchAlarmEnabled.isChecked = viewModel.settings.enabled
        binding.switchAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.settings.enabled = isChecked
            if (isChecked) {
                // 用户启用闹钟时按需申请通知权限（Android 13+）
                requestNotificationPermissionIfNeeded()
            }
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
            pickRingtone.launch(intent)
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
            try {
                val ringtone = RingtoneManager.getRingtone(requireContext(), Uri.parse(uri))
                val title = ringtone?.getTitle(requireContext())
                    ?: getString(R.string.settings_ringtone_default)
                binding.tvRingtoneValue.text = title
            } catch (_: Exception) {
                binding.tvRingtoneValue.text = getString(R.string.settings_ringtone_default)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
