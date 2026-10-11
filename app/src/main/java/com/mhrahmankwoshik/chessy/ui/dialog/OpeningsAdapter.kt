package com.mhrahmankwoshik.chessy.ui.dialog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mhrahmankwoshik.chessy.data.OpeningFileManager
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.databinding.ItemOpeningBinding

class OpeningsAdapter(
    private var allOpenings: List<Opening>,
    initialSelectedIds: Set<String>,
    private val onSelectionChanged: (Set<String>) -> Unit,
    private val onPlayOpening: (Opening) -> Unit,
    private val onEditOpening: (Opening) -> Unit,
    private val onDeleteOpening: (Opening) -> Unit
) : RecyclerView.Adapter<OpeningsAdapter.OpeningViewHolder>() {

    enum class FilterMode {
        ALL,
        WHITE,
        BLACK
    }

    private val selectedIds = initialSelectedIds.toMutableSet()
    private var currentFilter = FilterMode.ALL
    private var displayedOpenings: List<Opening> = emptyList()

    init {
        applyFilter(FilterMode.ALL)
    }

    fun updateData(openings: List<Opening>, updatedSelectedIds: Set<String>? = null) {
        this.allOpenings = openings
        if (updatedSelectedIds != null) {
            selectedIds.clear()
            selectedIds.addAll(updatedSelectedIds)
        }
        applyFilter(currentFilter)
    }

    fun applyFilter(filter: FilterMode) {
        currentFilter = filter
        displayedOpenings = when (filter) {
            FilterMode.ALL -> allOpenings
            FilterMode.WHITE -> allOpenings.filter { it.targetColor == PieceColor.WHITE }
            FilterMode.BLACK -> allOpenings.filter { it.targetColor == PieceColor.BLACK }
        }
        notifyDataSetChanged()
    }

    fun selectAllVisible() {
        displayedOpenings.forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.toSet())
    }

    fun clearAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.toSet())
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun getDisplayedCount(): Int = displayedOpenings.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OpeningViewHolder {
        val binding = ItemOpeningBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OpeningViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OpeningViewHolder, position: Int) {
        holder.bind(displayedOpenings[position])
    }

    override fun getItemCount(): Int = displayedOpenings.size

    inner class OpeningViewHolder(
        private val binding: ItemOpeningBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(opening: Opening) {
            binding.tvOpeningName.text = opening.name

            if (opening.eco.isNotBlank()) {
                binding.tvOpeningEco.visibility = View.VISIBLE
                binding.tvOpeningEco.text = opening.eco
            } else {
                binding.tvOpeningEco.visibility = View.GONE
            }

            val context = binding.root.context
            val hasCustom = OpeningFileManager.hasCustomPaths(context, opening)
            val customBadge = if (hasCustom) " • Variations Active" else ""
            val sideLabel = if (opening.targetColor == PieceColor.WHITE) "Stockfish is White" else "Stockfish is Black"
            binding.tvOpeningBadge.text = "$sideLabel$customBadge"

            binding.tvOpeningMoves.text = opening.movesSan
            binding.tvOpeningDesc.text = if (opening.description.isNotBlank()) {
                opening.description
            } else {
                "Custom gambit for Stockfish"
            }

            // Prevent unwanted triggers when recycling views
            binding.cbSelectOpening.setOnCheckedChangeListener(null)
            binding.cbSelectOpening.isChecked = selectedIds.contains(opening.id)

            binding.cbSelectOpening.setOnCheckedChangeListener { _, isChecked ->
                toggleSelection(opening.id, isChecked)
            }

            binding.root.setOnClickListener {
                val newChecked = !selectedIds.contains(opening.id)
                binding.cbSelectOpening.isChecked = newChecked
                toggleSelection(opening.id, newChecked)
            }

            binding.btnEditVariations.setOnClickListener {
                onEditOpening(opening)
            }

            binding.btnPlayOpening.setOnClickListener {
                onPlayOpening(opening)
            }

            binding.btnDeleteOpening.setOnClickListener {
                onDeleteOpening(opening)
            }
        }

        private fun toggleSelection(openingId: String, isSelected: Boolean) {
            if (isSelected) {
                selectedIds.add(openingId)
            } else {
                selectedIds.remove(openingId)
            }
            onSelectionChanged(selectedIds.toSet())
        }
    }
}
