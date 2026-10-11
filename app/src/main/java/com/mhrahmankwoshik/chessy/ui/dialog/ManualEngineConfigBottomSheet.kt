package com.mhrahmankwoshik.chessy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.databinding.DialogManualEngineConfigBinding
import com.mhrahmankwoshik.chessy.engine.EngineManager
import com.mhrahmankwoshik.chessy.util.PreferencesManager
import com.mhrahmankwoshik.chessy.util.TermuxUtils
import kotlinx.coroutines.launch

class ManualEngineConfigBottomSheet(
    private val onPickFileRequested: () -> Unit,
    private val onEngineSaved: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogManualEngineConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogManualEngineConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = PreferencesManager(requireContext())
        val engineManager = EngineManager(requireContext())

        binding.etEnginePath.setText(prefs.customEnginePath)

        val updatePermissionUi = {
            val hasPermission = TermuxUtils.hasRunCommandPermission(requireContext())
            if (hasPermission) {
                binding.tvSheetPermissionStatus.text = "Permission: Granted ✅"
                binding.tvSheetPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
                binding.btnSheetGrantPermission.visibility = View.GONE
            } else {
                binding.tvSheetPermissionStatus.text = "Permission: Not Granted ❌"
                binding.tvSheetPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red))
                binding.btnSheetGrantPermission.visibility = View.VISIBLE
                binding.btnSheetGrantPermission.setOnClickListener {
                    TermuxUtils.openAppPermissionSettings(requireContext())
                }
            }
        }
        updatePermissionUi()

        // Presets
        binding.chipDefaultPath.setOnClickListener {
            binding.etEnginePath.setText("stockfish")
        }
        binding.chipUsrBinPath.setOnClickListener {
            binding.etEnginePath.setText("/data/data/com.termux/files/usr/bin/stockfish")
        }
        binding.chipHomePath.setOnClickListener {
            binding.etEnginePath.setText("~/stockfish")
        }
        binding.chipSharedStoragePath.setOnClickListener {
            binding.etEnginePath.setText("~/storage/shared/stockfish")
        }

        // Copy storage command
        binding.btnCopyStorageCmd.setOnClickListener {
            TermuxUtils.copyToClipboard(requireContext(), "Termux Storage Command", binding.tvStorageCmd.text.toString())
        }

        // Pick file from phone storage
        binding.btnPickFileFromPhone.setOnClickListener {
            onPickFileRequested()
        }

        // Test path diagnostics
        binding.btnTestDiagnostic.setOnClickListener {
            val path = binding.etEnginePath.text?.toString()?.trim() ?: "stockfish"
            val config = prefs.getEngineConfig().copy(customEnginePath = path)

            updatePermissionUi()
            binding.tvDiagnosticLog.visibility = View.VISIBLE
            binding.tvDiagnosticLog.text = "Running diagnostic test for: $path…"

            lifecycleScope.launch {
                val diag = engineManager.runDiagnostic(config)
                val log = buildString {
                    append(if (diag.success) "STATUS: SUCCESS ✅\n" else "STATUS: FAILED ❌\n")
                    append("Exit Code: ${diag.exitCode}\n")
                    if (diag.stdout.isNotBlank()) append("Output:\n${diag.stdout.take(300)}\n")
                    if (diag.stderr.isNotBlank()) append("Error:\n${diag.stderr}\n")
                    append("Info: ${diag.infoMessage}")
                }
                binding.tvDiagnosticLog.text = log
            }
        }

        // Save & Confirm
        binding.btnSaveAndConfirm.setOnClickListener {
            val path = binding.etEnginePath.text?.toString()?.trim() ?: "stockfish"
            prefs.customEnginePath = path
            prefs.isManuallyVerified = true
            Toast.makeText(requireContext(), "Engine configured: $path", Toast.LENGTH_SHORT).show()
            onEngineSaved()
            dismiss()
        }
    }

    fun setPathFromFile(path: String) {
        _binding?.etEnginePath?.setText(path)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ManualEngineConfigBottomSheet"
    }
}
