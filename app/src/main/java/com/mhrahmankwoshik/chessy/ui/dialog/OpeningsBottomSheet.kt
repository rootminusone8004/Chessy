package com.mhrahmankwoshik.chessy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mhrahmankwoshik.chessy.data.OpeningDatabase
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.databinding.DialogOpeningsBinding
import com.mhrahmankwoshik.chessy.util.PreferencesManager

class OpeningsBottomSheet(
    private val onOpeningSelectedToPlay: (Opening) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogOpeningsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: OpeningsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOpeningsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferencesManager(requireContext())

        // 1. Opening book toggle
        binding.switchOpeningBook.isChecked = prefs.isOpeningBookEnabled
        updateHeaderSubtitle(binding.switchOpeningBook.isChecked)
        binding.switchOpeningBook.setOnCheckedChangeListener { _, isChecked ->
            prefs.isOpeningBookEnabled = isChecked
            updateHeaderSubtitle(isChecked)
        }

        // 2. Load custom gambits
        val currentOpenings = OpeningDatabase.getOpenings(requireContext())

        // 3. Setup RecyclerView & Adapter
        adapter = OpeningsAdapter(
            allOpenings = currentOpenings,
            initialSelectedIds = prefs.selectedOpeningIds,
            onSelectionChanged = { newSelectedIds ->
                prefs.selectedOpeningIds = newSelectedIds
                updateCounter(newSelectedIds.size)
            },
            onPlayOpening = { opening ->
                onOpeningSelectedToPlay(opening)
                dismiss()
            },
            onEditOpening = { opening ->
                openGambitEditor(opening)
            },
            onDeleteOpening = { opening ->
                confirmDeleteOpening(opening)
            }
        )

        binding.rvOpenings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOpenings.adapter = adapter

        // 4. Update UI State (empty vs list)
        updateListVisibility()

        // 5. Create Gambit buttons
        binding.btnCreateGambit.setOnClickListener {
            openGambitEditor(null)
        }

        binding.btnCreateFirstGambit.setOnClickListener {
            openGambitEditor(null)
        }

        binding.btnAddSampleGambit.setOnClickListener {
            val sample = OpeningDatabase.createSampleScotchGambit(requireContext())
            prefs.selectedOpeningIds = prefs.selectedOpeningIds + sample.id
            refreshOpeningsList()
            Toast.makeText(requireContext(), "Added Scotch Gambit example", Toast.LENGTH_SHORT).show()
        }

        // 6. Chip filters
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                binding.chipFilterAll.id -> adapter.applyFilter(OpeningsAdapter.FilterMode.ALL)
                binding.chipFilterWhite.id -> adapter.applyFilter(OpeningsAdapter.FilterMode.WHITE)
                binding.chipFilterBlack.id -> adapter.applyFilter(OpeningsAdapter.FilterMode.BLACK)
            }
        }

        // 7. Select All / Clear All buttons
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAllVisible()
        }

        binding.btnClearAll.setOnClickListener {
            adapter.clearAll()
        }

        // 8. Done button
        binding.btnDoneOpenings.setOnClickListener {
            dismiss()
        }
    }

    private fun openGambitEditor(opening: Opening?) {
        OpeningVariationEditorBottomSheet(
            existingOpening = opening,
            onSaved = {
                refreshOpeningsList()
            },
            onDeleted = {
                refreshOpeningsList()
            }
        ).show(parentFragmentManager, OpeningVariationEditorBottomSheet.TAG)
    }

    private fun confirmDeleteOpening(opening: Opening) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${opening.name}?")
            .setMessage("Are you sure you want to delete this gambit and its variation file?")
            .setPositiveButton("Delete") { _, _ ->
                OpeningDatabase.deleteOpening(requireContext(), opening.id)
                prefs.selectedOpeningIds = prefs.selectedOpeningIds - opening.id
                refreshOpeningsList()
                Toast.makeText(requireContext(), "Deleted ${opening.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshOpeningsList() {
        val updated = OpeningDatabase.getOpenings(requireContext())
        adapter.updateData(updated, prefs.selectedOpeningIds)
        updateListVisibility()
    }

    private fun updateListVisibility() {
        val openings = OpeningDatabase.getOpenings(requireContext())
        if (openings.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvOpenings.visibility = View.GONE
            binding.layoutSelectionControls.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvOpenings.visibility = View.VISIBLE
            binding.layoutSelectionControls.visibility = View.VISIBLE
        }
        updateCounter(adapter.getSelectedIds().size)
    }

    private fun updateCounter(count: Int) {
        binding.tvSelectedCounter.text = when (count) {
            0 -> "No gambits selected (Stockfish evaluates freely)"
            1 -> "1 gambit selected for Stockfish"
            else -> "$count gambits selected for Stockfish"
        }
    }

    private fun updateHeaderSubtitle(isEnabled: Boolean) {
        binding.tvOpeningHeaderSubtitle.text = if (isEnabled) {
            "Create and select gambits Stockfish will play against you"
        } else {
            "Opening book disabled. Stockfish evaluates freely."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpeningsBottomSheet"
    }
}
