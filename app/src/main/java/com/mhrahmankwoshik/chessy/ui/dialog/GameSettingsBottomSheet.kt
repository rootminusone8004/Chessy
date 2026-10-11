package com.mhrahmankwoshik.chessy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mhrahmankwoshik.chessy.data.model.EngineMode
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.databinding.DialogGameSettingsBinding
import com.mhrahmankwoshik.chessy.util.PreferencesManager

class GameSettingsBottomSheet(
    private val onSettingsSaved: (colorChanged: Boolean) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogGameSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogGameSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = PreferencesManager(requireContext())
        val initialColor = prefs.playerColor

        // 1. Initial side
        if (initialColor == PieceColor.WHITE) {
            binding.rbSideWhite.isChecked = true
        } else {
            binding.rbSideBlack.isChecked = true
        }

        // 2. Initial depth
        binding.sliderDepth.value = prefs.engineDepth.toFloat()
        binding.tvDepthLabel.text = "Stockfish Search Depth: ${prefs.engineDepth}"
        binding.sliderDepth.addOnChangeListener { _, value, _ ->
            binding.tvDepthLabel.text = "Stockfish Search Depth: ${value.toInt()}"
        }

        // 3. Socket port
        binding.etSocketPort.setText(prefs.socketPort.toString())

        // Buttons
        binding.btnCancelSettings.setOnClickListener { dismiss() }

        binding.btnApplySettings.setOnClickListener {
            val selectedColor = if (binding.rbSideWhite.isChecked) PieceColor.WHITE else PieceColor.BLACK
            val colorChanged = selectedColor != initialColor
            prefs.playerColor = selectedColor

            prefs.engineDepth = binding.sliderDepth.value.toInt()
            prefs.engineMode = EngineMode.TERMUX_SOCKET

            val port = binding.etSocketPort.text?.toString()?.toIntOrNull() ?: 2222
            prefs.socketPort = port

            onSettingsSaved(colorChanged)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GameSettingsBottomSheet"
    }
}
