package com.mhrahmankwoshik.chessy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.databinding.DialogChooseSideBinding
import com.mhrahmankwoshik.chessy.util.PreferencesManager

class ChooseSideBottomSheet(
    private val isGameInProgress: Boolean = false,
    private val onSideChosen: (PieceColor) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogChooseSideBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogChooseSideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferencesManager(requireContext())

        if (isGameInProgress) {
            binding.tvChooseSideSubtitle.text = "Starting a new game will reset the current game."
        }

        // Prepopulate current bridge port
        binding.etChooseSidePort.setText(prefs.socketPort.toString())

        binding.cardPlayWhite.setOnClickListener {
            savePortAndStart(PieceColor.WHITE)
        }

        binding.cardPlayBlack.setOnClickListener {
            savePortAndStart(PieceColor.BLACK)
        }

        binding.cardPlayRandom.setOnClickListener {
            val randomColor = if (java.util.Random().nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
            savePortAndStart(randomColor)
        }

        binding.btnCancelChooseSide.setOnClickListener {
            dismiss()
        }
    }

    private fun savePortAndStart(color: PieceColor) {
        val port = binding.etChooseSidePort.text?.toString()?.toIntOrNull() ?: prefs.socketPort
        if (port in 1..65535) {
            prefs.socketPort = port
        }
        onSideChosen(color)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ChooseSideBottomSheet"
    }
}
