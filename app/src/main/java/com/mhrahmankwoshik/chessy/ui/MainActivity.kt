package com.mhrahmankwoshik.chessy.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.data.model.GameStatus
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.databinding.ActivityMainBinding
import com.mhrahmankwoshik.chessy.engine.EngineCheckStatus
import com.mhrahmankwoshik.chessy.ui.dialog.ChooseSideBottomSheet
import com.mhrahmankwoshik.chessy.ui.dialog.GameOverDialog
import com.mhrahmankwoshik.chessy.ui.dialog.GameSettingsBottomSheet
import com.mhrahmankwoshik.chessy.ui.dialog.ManualEngineConfigBottomSheet
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.ui.dialog.OpeningsBottomSheet
import com.mhrahmankwoshik.chessy.ui.dialog.PawnPromotionDialog
import com.mhrahmankwoshik.chessy.ui.dialog.TermuxSetupBottomSheet
import com.mhrahmankwoshik.chessy.util.TermuxUtils
import com.mhrahmankwoshik.chessy.viewmodel.ChessViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChessViewModel by viewModels()
    private val moveHistoryAdapter = MoveHistoryAdapter()
    private var activeManualConfigSheet: ManualEngineConfigBottomSheet? = null

    // Storage file picker for locating engine in phone storage
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "stockfish"
            // Auto-translate to common Termux storage symlink
            val resolvedTermuxPath = "~/storage/shared/$fileName"
            Toast.makeText(this, "Selected: $fileName", Toast.LENGTH_SHORT).show()
            activeManualConfigSheet?.setPathFromFile(resolvedTermuxPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBoardListeners()
        setupButtons()
        setupPrerequisiteOverlay()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.prefs.isManuallyVerified) {
            viewModel.checkTermuxEngineRequirement()
        }
    }

    private fun setupRecyclerView() {
        binding.rvMoveHistory.apply {
            adapter = moveHistoryAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupBoardListeners() {
        binding.chessBoardView.onSquareSelectedListener = { square ->
            val legalMoves = viewModel.getLegalMovesForSquare(square)
            binding.chessBoardView.legalMoves = legalMoves
        }

        binding.chessBoardView.onMoveAttemptListener = { from, to ->
            binding.chessBoardView.selectedSquare = null
            binding.chessBoardView.legalMoves = emptyList()
            viewModel.attemptMove(from, to)
        }
    }

    private fun setupButtons() {
        binding.btnNewGame.setOnClickListener {
            showChooseSideDialog()
        }

        binding.btnSideSelector.setOnClickListener {
            showChooseSideDialog()
        }

        binding.layoutBottomPlayer.setOnClickListener {
            showChooseSideDialog()
        }

        binding.btnUndo.setOnClickListener {
            viewModel.undoMove()
        }

        binding.btnFlipBoard.setOnClickListener {
            viewModel.flipBoard()
        }

        binding.btnResign.setOnClickListener {
            showResignConfirmation()
        }

        binding.btnSharePgn.setOnClickListener {
            showCopyOptions()
        }

        binding.btnManualLocateTop.setOnClickListener {
            openManualEngineConfig()
        }

        binding.btnTermuxSetup.setOnClickListener {
            TermuxSetupBottomSheet().show(supportFragmentManager, TermuxSetupBottomSheet.TAG)
        }

        binding.btnSettings.setOnClickListener {
            GameSettingsBottomSheet { colorChanged ->
                if (colorChanged) {
                    viewModel.startNewGame()
                }
            }.show(supportFragmentManager, GameSettingsBottomSheet.TAG)
        }

        binding.btnOpeningsBook.setOnClickListener {
            showOpeningsDialog()
        }

        binding.layoutOpeningBanner.setOnClickListener {
            showOpeningsDialog()
        }
    }

    private fun setupPrerequisiteOverlay() {
        binding.tvDirectSocketCommand.text = "socat TCP-LISTEN:${viewModel.prefs.socketPort},reuseaddr,fork EXEC:stockfish"
        binding.tvDirectInstallCommand.text = TermuxUtils.INSTALL_COMMAND

        binding.btnCopySocketCommand.setOnClickListener {
            TermuxUtils.copyToClipboard(this, "Termux Live Socket Command", binding.tvDirectSocketCommand.text.toString())
        }

        binding.btnCopyInstallCommandOverlay.setOnClickListener {
            TermuxUtils.copyToClipboard(this, "Termux Install Command", binding.tvDirectInstallCommand.text.toString())
        }

        binding.btnLocateManually.setOnClickListener {
            openManualEngineConfig()
        }

        binding.btnVerifyEngine.setOnClickListener {
            Toast.makeText(this, "Checking localhost Stockfish bridge on port ${viewModel.prefs.socketPort}…", Toast.LENGTH_SHORT).show()
            viewModel.checkTermuxEngineRequirement()
        }

        binding.btnProceedAnyway.setOnClickListener {
            viewModel.prefs.isManuallyVerified = true
            Toast.makeText(this, "Engine confirmed! Starting game…", Toast.LENGTH_SHORT).show()
            viewModel.checkTermuxEngineRequirement()
        }
    }

    private fun openManualEngineConfig() {
        val sheet = ManualEngineConfigBottomSheet(
            onPickFileRequested = {
                filePickerLauncher.launch("*/*")
            },
            onEngineSaved = {
                viewModel.checkTermuxEngineRequirement()
            }
        )
        activeManualConfigSheet = sheet
        sheet.show(supportFragmentManager, ManualEngineConfigBottomSheet.TAG)
    }

    private fun observeViewModel() {
        viewModel.board.observe(this) { board ->
            binding.chessBoardView.board = board
        }

        viewModel.lastMove.observe(this) { move ->
            binding.chessBoardView.lastMove = move
        }

        viewModel.isLastMoveBookMove.observe(this) { isBookMove ->
            binding.chessBoardView.isLastMoveBookMove = isBookMove
        }

        viewModel.isKingInCheck.observe(this) { inCheck ->
            binding.chessBoardView.isKingInCheck = inCheck
        }

        viewModel.checkKingSquare.observe(this) { square ->
            binding.chessBoardView.checkKingSquare = square
        }

        viewModel.isBoardFlipped.observe(this) { flipped ->
            binding.chessBoardView.isFlipped = flipped
            updatePlayerLabels(flipped)
        }

        viewModel.moveRecords.observe(this) { records ->
            moveHistoryAdapter.submitList(records) {
                if (records.isNotEmpty()) {
                    binding.rvMoveHistory.smoothScrollToPosition(records.size - 1)
                }
            }
        }

        viewModel.isEngineThinking.observe(this) { thinking ->
            binding.pbEngineThinking.visibility = if (thinking) View.VISIBLE else View.GONE
            binding.btnUndo.isEnabled = !thinking
            binding.btnNewGame.isEnabled = !thinking
            binding.chessBoardView.isEnabled = !thinking
            if (thinking) {
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card_active)
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
            }
        }

        viewModel.engineStatusText.observe(this) { statusText ->
            binding.tvEngineStatus.text = statusText
        }

        viewModel.gameStatus.observe(this) { status ->
            updateGameStatusUi(status)
        }

        viewModel.engineCheckStatus.observe(this) { checkStatus ->
            handleEngineCheckStatus(checkStatus)
        }

        viewModel.engineMissingError.observe(this) { errorMsg ->
            if (errorMsg != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Termux Engine Notice")
                    .setMessage(errorMsg)
                    .setPositiveButton("Locate Engine") { _, _ ->
                        openManualEngineConfig()
                    }
                    .setNeutralButton("Open Termux") { _, _ ->
                        TermuxUtils.openTermux(this)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }

        viewModel.pendingPromotion.observe(this) { pending ->
            if (pending != null) {
                val piece = viewModel.board.value?.getPiece(pending.first)
                val color = piece?.color ?: PieceColor.WHITE
                val dialog = PawnPromotionDialog(this, color) { chosenType ->
                    viewModel.confirmPromotion(chosenType)
                }
                dialog.setOnCancelListener {
                    viewModel.cancelPromotion()
                }
                dialog.show()
            }
        }

        viewModel.currentOpening.observe(this) { opening ->
            if (opening != null) {
                binding.layoutOpeningBanner.visibility = View.VISIBLE
                binding.tvCurrentOpeningEco.text = opening.eco
                binding.tvCurrentOpeningName.text = opening.name
            } else {
                binding.layoutOpeningBanner.visibility = View.GONE
            }
        }
    }

    private fun handleEngineCheckStatus(status: EngineCheckStatus) {
        when (status) {
            is EngineCheckStatus.Ready -> {
                binding.layoutEngineMissingOverlay.visibility = View.GONE
                binding.layoutGameContainer.visibility = View.VISIBLE
            }
            is EngineCheckStatus.TermuxAppMissing -> {
                binding.layoutGameContainer.visibility = View.GONE
                binding.layoutEngineMissingOverlay.visibility = View.VISIBLE
                binding.tvMissingTitle.text = "Termux App Required"
                binding.tvMissingMessage.text = "Chessy strictly relies on the Stockfish chess engine in Termux. The Termux app is not currently installed. Please install Termux from F-Droid to play."
                binding.btnInstallOrOpenTermux.text = "Install Termux"
                binding.btnInstallOrOpenTermux.setOnClickListener {
                    TermuxUtils.openOrInstallTermux(this)
                }
                binding.tvOverlayErrorLog.visibility = View.GONE
            }
            is EngineCheckStatus.StockfishNotResponding -> {
                binding.layoutGameContainer.visibility = View.GONE
                binding.layoutEngineMissingOverlay.visibility = View.VISIBLE
                binding.tvMissingTitle.text = "Connect Chessy to Termux"
                binding.tvMissingMessage.text = "Stockfish socket bridge is not reachable on localhost port ${viewModel.prefs.socketPort}. Run the command in Termux to start the bridge, or tap 'I\\'ve Installed It' if ready."
                binding.tvDirectSocketCommand.text = "socat TCP-LISTEN:${viewModel.prefs.socketPort},reuseaddr,fork EXEC:stockfish"
                binding.tvDirectInstallCommand.text = TermuxUtils.INSTALL_COMMAND
                binding.btnInstallOrOpenTermux.text = "Open Termux"
                binding.btnInstallOrOpenTermux.setOnClickListener {
                    TermuxUtils.openTermux(this)
                }

                val socketError = status.socketError
                if (!socketError.isNullOrBlank()) {
                    binding.tvOverlayErrorLog.visibility = View.VISIBLE
                    binding.tvOverlayErrorLog.text = "Bridge status: $socketError (Port: ${viewModel.prefs.socketPort})"
                } else {
                    binding.tvOverlayErrorLog.visibility = View.GONE
                }
            }
        }
    }

    private fun updatePlayerLabels(flipped: Boolean) {
        val playerColor = viewModel.prefs.playerColor
        val isPlayerWhite = playerColor == PieceColor.WHITE

        if (isPlayerWhite) {
            binding.ivPlayerIcon.setImageResource(R.drawable.ic_chess_wn)
            binding.tvPlayerName.text = "You (White)"
            binding.ivOpponentIcon.setImageResource(R.drawable.ic_chess_bn)
            binding.tvOpponentName.text = "Stockfish (Black)"
            binding.btnSideSelector.text = "White"
            binding.btnSideSelector.setIconResource(R.drawable.ic_chess_wn)
        } else {
            binding.ivPlayerIcon.setImageResource(R.drawable.ic_chess_bn)
            binding.tvPlayerName.text = "You (Black)"
            binding.ivOpponentIcon.setImageResource(R.drawable.ic_chess_wn)
            binding.tvOpponentName.text = "Stockfish (White)"
            binding.btnSideSelector.text = "Black"
            binding.btnSideSelector.setIconResource(R.drawable.ic_chess_bn)
        }
    }

    private fun updateGameStatusUi(status: GameStatus) {
        val board = viewModel.board.value ?: return
        val isPlayerTurn = (board.activeColor == viewModel.prefs.playerColor)

        when (status) {
            GameStatus.ACTIVE -> {
                if (isPlayerTurn) {
                    val checkPrefix = if (viewModel.isKingInCheck.value == true) "Check! " else ""
                    binding.tvGameTurnStatus.text = "${checkPrefix}Your Turn"
                    binding.tvGameTurnStatus.setTextColor(getColor(R.color.primaryOrange))
                    binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card_active)
                    binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                } else {
                    binding.tvGameTurnStatus.text = "Stockfish's Turn"
                    binding.tvGameTurnStatus.setTextColor(getColor(R.color.textSecondary))
                    binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card_active)
                    binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                }
            }
            GameStatus.CHECKMATE -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                val won = !isPlayerTurn
                val message = if (won) getString(R.string.status_checkmate_win) else getString(R.string.status_checkmate_loss)
                binding.tvGameTurnStatus.text = message
                showGameOverDialog(message)
            }
            GameStatus.STALEMATE -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                val message = getString(R.string.status_stalemate)
                binding.tvGameTurnStatus.text = message
                showGameOverDialog(message)
            }
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                val message = getString(R.string.status_draw_insufficient)
                binding.tvGameTurnStatus.text = message
                showGameOverDialog(message)
            }
            GameStatus.DRAW_FIFTY_MOVES -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                val message = getString(R.string.status_draw_50_moves)
                binding.tvGameTurnStatus.text = message
                showGameOverDialog(message)
            }
            GameStatus.RESIGNED -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                val message = getString(R.string.status_resigned)
                binding.tvGameTurnStatus.text = message
                showGameOverDialog(message)
            }
            else -> {
                binding.layoutBottomPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
                binding.layoutTopPlayer.setBackgroundResource(R.drawable.bg_rounded_card)
            }
        }
    }

    private var activeGameOverDialog: GameOverDialog? = null

    private fun showGameOverDialog(message: String) {
        if (activeGameOverDialog?.isShowing == true) {
            activeGameOverDialog?.dismiss()
        }
        activeGameOverDialog = GameOverDialog(
            context = this,
            message = message,
            onCopyPgn = {
                TermuxUtils.copyToClipboard(this, "Game PGN", viewModel.getPgn())
            },
            onNewGame = {
                showChooseSideDialog()
            },
            onReviewBoard = {
                // Dialog dismissed; user can review board
            }
        ).also { it.show() }
    }

    private fun showChooseSideDialog() {
        val isGameInProgress = (viewModel.gameStatus.value == GameStatus.ACTIVE) && (viewModel.moveRecords.value?.isNotEmpty() == true)
        ChooseSideBottomSheet(isGameInProgress = isGameInProgress) { chosenColor ->
            viewModel.startNewGame(chosenColor)
        }.show(supportFragmentManager, ChooseSideBottomSheet.TAG)
    }

    private fun showResignConfirmation() {
        if (viewModel.gameStatus.value != GameStatus.ACTIVE) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_resign)
            .setMessage("Are you sure you want to resign this game?")
            .setPositiveButton("Resign") { _, _ ->
                viewModel.resign()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOpeningsDialog() {
        OpeningsBottomSheet { opening ->
            val playerColor = if (opening.targetColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
            val playerSide = if (playerColor == PieceColor.WHITE) "White" else "Black"
            val engineSide = if (opening.targetColor == PieceColor.WHITE) "White" else "Black"

            val options = arrayOf(
                "Start from Move 1 (Stockfish plays this line)",
                "Jump to Gambit Position (Face ${opening.name})"
            )

            MaterialAlertDialogBuilder(this)
                .setTitle("Face ${opening.name}")
                .setMessage("Stockfish will play as $engineSide (${opening.name}).\nYou will play as $playerSide.\n\nChoose starting mode:")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            viewModel.startNewGame(
                                playerColor = playerColor,
                                lockedOpening = opening
                            )
                            Toast.makeText(this, "Facing Stockfish's ${opening.name} from Move 1", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            viewModel.startNewGame(
                                playerColor = playerColor,
                                initialOpening = opening
                            )
                            Toast.makeText(this, "Facing Stockfish's ${opening.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }.show(supportFragmentManager, OpeningsBottomSheet.TAG)
    }

    private fun showCopyOptions() {
        val options = arrayOf("Copy PGN Notation", "Copy FEN Position")
        MaterialAlertDialogBuilder(this)
            .setTitle("Export Game")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> TermuxUtils.copyToClipboard(this, "PGN", viewModel.getPgn())
                    1 -> TermuxUtils.copyToClipboard(this, "FEN", viewModel.getFen())
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeGameOverDialog?.dismiss()
        activeGameOverDialog = null
    }
}
