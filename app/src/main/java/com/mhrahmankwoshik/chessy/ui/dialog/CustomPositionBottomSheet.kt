package com.mhrahmankwoshik.chessy.ui.dialog

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.data.model.Piece
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.data.model.PieceType
import com.mhrahmankwoshik.chessy.data.model.Square
import com.mhrahmankwoshik.chessy.databinding.DialogCustomPositionBinding
import com.mhrahmankwoshik.chessy.game.ChessBoard
import com.mhrahmankwoshik.chessy.game.FenUtil
import com.mhrahmankwoshik.chessy.util.TermuxUtils

/**
 * Lets the user build a position on a visual board editor (or paste a FEN) and start a
 * new game against Stockfish from it. The board, side-to-move, castling controls and the
 * FEN text field are all kept in sync.
 */
class CustomPositionBottomSheet(
    private val currentFen: String,
    private val defaultColor: PieceColor,
    private val onStart: (fen: String, playerColor: PieceColor) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogCustomPositionBinding? = null
    private val binding get() = _binding!!

    /** The position being edited. */
    private var editBoard = ChessBoard()

    /** Piece placed on tap; null means the eraser is selected. */
    private var selectedTool: Piece? = Piece(PieceType.QUEEN, PieceColor.WHITE)
    private val paletteCells = mutableListOf<Pair<Piece?, ImageView>>()

    /** Square of a king the user tapped and is about to move; kings can be moved but never added or removed. */
    private var pickedKing: Square? = null

    /** Sanitized FEN of the last valid input, or null if the input is currently invalid. */
    private var validFen: String? = null

    /** True while controls are updated programmatically, so their listeners don't feed back. */
    private var isSyncing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCustomPositionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildPalette()

        binding.boardEditor.onSquareTapListener = ::onSquareTapped
        binding.btnFlipEditor.setOnClickListener {
            binding.boardEditor.isFlipped = !binding.boardEditor.isFlipped
        }
        binding.btnClearBoard.setOnClickListener { loadIntoEditor(KINGS_ONLY_FEN) }
        binding.btnStartingFen.setOnClickListener { loadIntoEditor(FenUtil.STARTING_FEN) }
        binding.btnCurrentFen.setOnClickListener { loadIntoEditor(currentFen) }

        binding.toggleSideToMove.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isSyncing) return@addOnButtonCheckedListener
            editBoard.activeColor = if (checkedId == R.id.btnToMoveBlack) PieceColor.BLACK else PieceColor.WHITE
            editBoard.enPassantTarget = null
            commitBoardEdit()
        }

        listOf(
            binding.cbWhiteKingSide, binding.cbWhiteQueenSide,
            binding.cbBlackKingSide, binding.cbBlackQueenSide
        ).forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ ->
                if (isSyncing) return@setOnCheckedChangeListener
                editBoard.whiteKingSideCastle = binding.cbWhiteKingSide.isChecked
                editBoard.whiteQueenSideCastle = binding.cbWhiteQueenSide.isChecked
                editBoard.blackKingSideCastle = binding.cbBlackKingSide.isChecked
                editBoard.blackQueenSideCastle = binding.cbBlackQueenSide.isChecked
                commitBoardEdit()
            }
        }

        binding.etFen.doAfterTextChanged { editable ->
            if (isSyncing) return@doAfterTextChanged
            // User typed/pasted a FEN: mirror it on the board when it can be parsed
            val text = editable?.toString()?.trim().orEmpty()
            runCatching { ChessBoard().apply { loadFen(text) } }.getOrNull()?.let {
                editBoard = it
                setPickedKing(null)
                syncControls()
            }
            validate(text)
        }
        binding.tilFen.setEndIconOnClickListener {
            TermuxUtils.copyToClipboard(requireContext(), "FEN", binding.etFen.text?.toString().orEmpty())
        }
        binding.btnPasteFen.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(requireContext())?.toString()
            if (!text.isNullOrBlank()) binding.etFen.setText(text.trim())
        }

        binding.toggleSide.check(
            if (defaultColor == PieceColor.WHITE) R.id.btnSideWhite else R.id.btnSideBlack
        )
        binding.btnCancelCustomPosition.setOnClickListener { dismiss() }
        binding.btnStartCustomPosition.setOnClickListener {
            val fen = validFen ?: return@setOnClickListener
            val color = if (binding.toggleSide.checkedButtonId == R.id.btnSideBlack) PieceColor.BLACK else PieceColor.WHITE
            dismiss()
            onStart(fen, color)
        }

        loadIntoEditor(KINGS_ONLY_FEN)
    }

    // region Editing

    private fun onSquareTapped(square: Square) {
        val existing = editBoard.getPiece(square)
        val isKingSquare = existing?.type == PieceType.KING

        // Second tap after picking up a king: move it there
        pickedKing?.let { from ->
            setPickedKing(null)
            if (square == from) return
            if (isKingSquare) {
                setPickedKing(square)
                return
            }
            val king = editBoard.getPiece(from) ?: return
            editBoard.setPiece(from, null)
            editBoard.setPiece(square, king)
            editBoard.enPassantTarget = null
            commitBoardEdit()
            return
        }

        // Kings can't be erased or overwritten; tapping one picks it up instead
        if (isKingSquare) {
            setPickedKing(square)
            return
        }

        val tool = selectedTool
        if (tool == null || existing == tool) {
            if (existing == null) return
            editBoard.setPiece(square, null)
        } else {
            if (tool.type == PieceType.PAWN && (square.rank == 0 || square.rank == 7)) return
            editBoard.setPiece(square, tool)
        }
        editBoard.enPassantTarget = null
        commitBoardEdit()
    }

    private fun setPickedKing(square: Square?) {
        pickedKing = square
        binding.boardEditor.selectedSquare = square
    }

    private fun loadIntoEditor(fen: String) {
        setPickedKing(null)
        editBoard = runCatching { ChessBoard().apply { loadFen(fen) } }.getOrElse { ChessBoard() }
        commitBoardEdit()
    }

    /** Normalizes the edited board, then pushes it to every control and the FEN field. */
    private fun commitBoardEdit() {
        editBoard = ChessBoard().apply { loadFen(FenUtil.sanitize(editBoard.toFen())) }
        syncControls()
        val fen = editBoard.toFen()
        isSyncing = true
        binding.etFen.setText(fen)
        binding.etFen.setSelection(fen.length)
        isSyncing = false
        validate(fen)
    }

    private fun syncControls() {
        isSyncing = true
        binding.boardEditor.board = editBoard.copy()
        binding.toggleSideToMove.check(
            if (editBoard.activeColor == PieceColor.WHITE) R.id.btnToMoveWhite else R.id.btnToMoveBlack
        )
        val possible = FenUtil.castlingPossible(editBoard)
        binding.cbWhiteKingSide.apply {
            isEnabled = possible.whiteKingSide
            isChecked = possible.whiteKingSide && editBoard.whiteKingSideCastle
        }
        binding.cbWhiteQueenSide.apply {
            isEnabled = possible.whiteQueenSide
            isChecked = possible.whiteQueenSide && editBoard.whiteQueenSideCastle
        }
        binding.cbBlackKingSide.apply {
            isEnabled = possible.blackKingSide
            isChecked = possible.blackKingSide && editBoard.blackKingSideCastle
        }
        binding.cbBlackQueenSide.apply {
            isEnabled = possible.blackQueenSide
            isChecked = possible.blackQueenSide && editBoard.blackQueenSideCastle
        }
        isSyncing = false
    }

    private fun validate(fen: String) {
        val error = if (fen.isEmpty()) "Enter a FEN" else FenUtil.validate(fen)
        validFen = if (error == null) FenUtil.sanitize(fen) else null
        binding.tilFen.error = error
        binding.btnStartCustomPosition.isEnabled = error == null
    }

    // endregion

    // region Palette

    private fun buildPalette() {
        // Kings are fixed on the board (moved by tapping them), so they aren't in the palette
        val order = listOf(
            PieceType.QUEEN, PieceType.ROOK,
            PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN
        )
        for (type in order) addPaletteCell(binding.paletteRowWhite, Piece(type, PieceColor.WHITE))
        addPaletteCell(binding.paletteRowWhite, null) // eraser
        for (type in order) addPaletteCell(binding.paletteRowBlack, Piece(type, PieceColor.BLACK))
        // Spacer so both rows line up under the eraser column
        binding.paletteRowBlack.addView(View(requireContext()), cellLayoutParams())
        refreshPaletteSelection()
    }

    private fun addPaletteCell(row: LinearLayout, piece: Piece?) {
        val pad = dp(4)
        val cell = ImageView(requireContext()).apply {
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (piece != null) {
                setImageResource(pieceDrawable(piece))
                contentDescription = "${piece.color.name.lowercase()} ${piece.type.name.lowercase()}"
            } else {
                setImageResource(R.drawable.ic_eraser)
                ImageViewCompat.setImageTintList(
                    this, ColorStateList.valueOf(requireContext().getColor(R.color.textSecondary))
                )
                contentDescription = "Eraser"
            }
            setOnClickListener {
                selectedTool = piece
                refreshPaletteSelection()
            }
        }
        row.addView(cell, cellLayoutParams())
        paletteCells.add(piece to cell)
    }

    private fun cellLayoutParams() = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    private fun refreshPaletteSelection() {
        for ((piece, cell) in paletteCells) {
            if (piece == selectedTool) {
                cell.setBackgroundResource(R.drawable.bg_palette_selected)
            } else {
                cell.background = null
            }
        }
    }

    @DrawableRes
    private fun pieceDrawable(piece: Piece): Int {
        val white = piece.color == PieceColor.WHITE
        return when (piece.type) {
            PieceType.KING -> if (white) R.drawable.ic_chess_wk else R.drawable.ic_chess_bk
            PieceType.QUEEN -> if (white) R.drawable.ic_chess_wq else R.drawable.ic_chess_bq
            PieceType.ROOK -> if (white) R.drawable.ic_chess_wr else R.drawable.ic_chess_br
            PieceType.BISHOP -> if (white) R.drawable.ic_chess_wb else R.drawable.ic_chess_bb
            PieceType.KNIGHT -> if (white) R.drawable.ic_chess_wn else R.drawable.ic_chess_bn
            PieceType.PAWN -> if (white) R.drawable.ic_chess_wp else R.drawable.ic_chess_bp
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CustomPositionBottomSheet"
        private const val KINGS_ONLY_FEN = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
    }
}
