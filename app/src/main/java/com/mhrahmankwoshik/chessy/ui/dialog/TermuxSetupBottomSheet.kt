package com.mhrahmankwoshik.chessy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.databinding.DialogTermuxSetupBinding
import com.mhrahmankwoshik.chessy.util.PreferencesManager
import com.mhrahmankwoshik.chessy.util.TermuxUtils

class TermuxSetupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogTermuxSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTermuxSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferencesManager(requireContext())
        binding.tvSocketCommand.text = "socat TCP-LISTEN:${prefs.socketPort},reuseaddr,fork EXEC:stockfish"
        binding.tvInstallCommand.text = TermuxUtils.INSTALL_COMMAND

        val isInstalled = TermuxUtils.isTermuxInstalled(requireContext())
        if (isInstalled) {
            binding.vTermuxStatusDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.status_green)
            binding.tvTermuxStatus.text = "Termux App Detected"
        } else {
            binding.vTermuxStatusDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.status_red)
            binding.tvTermuxStatus.text = "Termux Missing"
        }

        binding.btnCopyInstallCommand.setOnClickListener {
            TermuxUtils.copyToClipboard(requireContext(), "Termux Install Command", binding.tvInstallCommand.text.toString())
        }

        binding.btnCopySocketCommand.setOnClickListener {
            TermuxUtils.copyToClipboard(requireContext(), "Termux Live Socket Command", binding.tvSocketCommand.text.toString())
        }

        binding.btnCopySetupStorageCommand.setOnClickListener {
            TermuxUtils.copyToClipboard(requireContext(), "Termux Storage Command", binding.tvSetupStorageCommand.text.toString())
        }

        binding.btnOpenTermux.setOnClickListener {
            TermuxUtils.openTermux(requireContext())
        }

        binding.btnCloseTermuxDialog.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TermuxSetupBottomSheet"
    }
}
