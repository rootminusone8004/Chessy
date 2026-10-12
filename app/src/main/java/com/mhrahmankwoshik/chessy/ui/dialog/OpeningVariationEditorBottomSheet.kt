package com.mhrahmankwoshik.chessy.ui.dialog

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.data.OpeningDatabase
import com.mhrahmankwoshik.chessy.data.OpeningFileManager
import com.mhrahmankwoshik.chessy.data.OpeningVariationParser
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.databinding.DialogOpeningVariationEditorBinding
import com.mhrahmankwoshik.chessy.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpeningVariationEditorBottomSheet(
    private val existingOpening: Opening? = null,
    private val onSaved: ((Opening) -> Unit)? = null,
    private val onDeleted: ((String) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogOpeningVariationEditorBinding? = null
    private val binding get() = _binding!!
    private var validationJob: Job? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImportedUri(it) }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let { handleExportUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOpeningVariationEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isEditing = existingOpening != null
        if (isEditing) {
            val op = existingOpening!!
            binding.tvEditorTitle.text = "Edit ${op.name}"
            binding.tvEditorSubtitle.text = "File: ${op.id}.txt"
            binding.etGambitName.setText(op.name)
            if (op.targetColor == PieceColor.WHITE) {
                binding.chipColorWhite.isChecked = true
            } else {
                binding.chipColorBlack.isChecked = true
            }
            binding.etGambitDescription.setText(op.description)
            val initialContent = OpeningFileManager.getOrInitializeOpeningContent(requireContext(), op)
            binding.etVariationFileContent.setText(initialContent)
            binding.btnSaveAndApply.text = "Update Gambit"
            binding.btnDeleteGambit.visibility = View.VISIBLE
            binding.btnDeleteGambit.setOnClickListener {
                confirmDeleteGambit(op)
            }
        } else {
            binding.tvEditorTitle.text = "Create Custom Gambit"
            binding.tvEditorSubtitle.text = "Define custom moves for Stockfish to play"
            binding.chipColorWhite.isChecked = true
            binding.btnSaveAndApply.text = "Save Gambit"
            binding.btnDeleteGambit.visibility = View.GONE
        }

        updateValidationStatus(binding.etVariationFileContent.text?.toString() ?: "")

        binding.etVariationFileContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateValidationStatus(s?.toString() ?: "")
            }
        })

        binding.btnLoadFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnExportFile.setOnClickListener {
            val defaultName = (binding.etGambitName.text?.toString()?.trim()
                ?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_") ?: "my_gambit") + ".txt"
            exportLauncher.launch(defaultName)
        }

        binding.btnInsertExample.setOnClickListener {
            val exampleContent = """
1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Bc4
   Be7 5. c3 dxc3 6. Qd5 Nh6 7. Bxh6
      gxh6 8.Qf7#
      0-0 8. Bxg7 Kxg7 9. Nxc3
   Bb4 5. c3 dxc3 6. bxc3
      Bc5 7. Bxf7 Kxf7 8. Qd5+ Ke8 9. Qxc5
      Ba5 7. Qb3 Qe7 8. 0-0
            """.trimIndent()
            binding.etVariationFileContent.setText(exampleContent)
            if (binding.etGambitName.text.isNullOrBlank()) {
                binding.etGambitName.setText("Scotch Gambit")
            }
            binding.chipColorWhite.isChecked = true
            Toast.makeText(requireContext(), "Inserted Scotch Gambit variation example", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveAndApply.setOnClickListener {
            saveAndDismiss()
        }

        binding.btnCloseEditor.setOnClickListener {
            dismiss()
        }
    }

    private fun updateValidationStatus(content: String) {
        validationJob?.cancel()
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            if (_binding != null) {
                binding.tvValidationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.textSecondary)
                )
                binding.tvValidationStatus.text = "Enter moves above. Line 1 is the base move line."
            }
            return
        }

        validationJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            val parseResult = withContext(Dispatchers.Default) {
                OpeningVariationParser.parseFileContent(content)
            }
            if (_binding == null) return@launch
            val validPathsCount = parseResult.paths.size
            val totalMoves = parseResult.totalMovesCount

            if (parseResult.errors.isNotEmpty()) {
                binding.tvValidationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_red)
                )
                val firstError = parseResult.errors.first()
                binding.tvValidationStatus.text = "⚠️ $firstError ($validPathsCount valid path(s) parsed)"
            } else if (validPathsCount > 1) {
                binding.tvValidationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_green)
                )
                binding.tvValidationStatus.text = "✓ $validPathsCount variation paths ($totalMoves total moves). Stockfish will play these branches!"
            } else if (validPathsCount == 1) {
                binding.tvValidationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.lichess_last_move_light)
                )
                binding.tvValidationStatus.text = "✓ Base line valid ($totalMoves moves). Add indented lines below to branch variations."
            } else {
                binding.tvValidationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_amber)
                )
                binding.tvValidationStatus.text = "No valid move paths found in text."
            }
        }
    }

    private fun handleImportedUri(uri: Uri) {
        try {
            val content = OpeningFileManager.readContentFromUri(requireContext(), uri)
            if (content.isNotBlank()) {
                binding.etVariationFileContent.setText(content)
                Toast.makeText(requireContext(), "Loaded file into editor", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Selected file was empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleExportUri(uri: Uri) {
        try {
            val content = binding.etVariationFileContent.text?.toString() ?: ""
            OpeningFileManager.exportContentToUri(requireContext(), uri, content)
            Toast.makeText(requireContext(), "Exported gambit file successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDeleteGambit(opening: Opening) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${opening.name}?")
            .setMessage("Are you sure you want to delete this gambit and its variation file?")
            .setPositiveButton("Delete") { _, _ ->
                OpeningDatabase.deleteOpening(requireContext(), opening.id)
                val prefs = PreferencesManager(requireContext())
                prefs.selectedOpeningIds = prefs.selectedOpeningIds - opening.id
                onDeleted?.invoke(opening.id)
                Toast.makeText(requireContext(), "Deleted ${opening.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAndDismiss() {
        val name = binding.etGambitName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.etGambitName.error = "Please enter a gambit name"
            binding.etGambitName.requestFocus()
            return
        }

        val content = binding.etVariationFileContent.text?.toString()?.trim() ?: ""
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter the moves for this gambit", Toast.LENGTH_SHORT).show()
            return
        }

        val parseResult = OpeningVariationParser.parseFileContent(content)
        if (parseResult.paths.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot save: No valid move paths found", Toast.LENGTH_LONG).show()
            return
        }

        val basePath = parseResult.paths.firstOrNull { it.isBaseLine } ?: parseResult.paths.first()
        val movesUci = basePath.movesUci
        val movesSan = OpeningVariationParser.formatSanMoves(basePath.movesSan)
        val targetColor = if (binding.chipColorWhite.isChecked) PieceColor.WHITE else PieceColor.BLACK
        val description = binding.etGambitDescription.text?.toString()?.trim() ?: ""

        val openingId = existingOpening?.id ?: run {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            "${slug}_${System.currentTimeMillis()}"
        }

        val opening = Opening(
            id = openingId,
            name = name,
            eco = existingOpening?.eco ?: "",
            targetColor = targetColor,
            movesUci = movesUci,
            movesSan = movesSan,
            description = description,
            isUserFavorite = true
        )

        OpeningDatabase.saveOpening(requireContext(), opening, content)

        // Ensure newly created or updated opening is selected for Stockfish
        val prefs = PreferencesManager(requireContext())
        prefs.selectedOpeningIds = prefs.selectedOpeningIds + opening.id

        onSaved?.invoke(opening)
        Toast.makeText(requireContext(), "Saved '$name'", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        validationJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpeningVariationEditorBottomSheet"
    }
}
