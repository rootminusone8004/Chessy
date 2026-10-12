package com.mhrahmankwoshik.chessy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mhrahmankwoshik.chessy.data.OpeningDatabase
import com.mhrahmankwoshik.chessy.data.model.GameStatus
import com.mhrahmankwoshik.chessy.data.model.Move
import com.mhrahmankwoshik.chessy.data.model.MoveRecord
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.data.model.PieceType
import com.mhrahmankwoshik.chessy.data.model.Square
import com.mhrahmankwoshik.chessy.engine.EngineCheckStatus
import com.mhrahmankwoshik.chessy.engine.EngineManager
import com.mhrahmankwoshik.chessy.game.ChessBoard
import com.mhrahmankwoshik.chessy.game.ChessGame
import com.mhrahmankwoshik.chessy.game.MoveGenerator
import com.mhrahmankwoshik.chessy.util.PreferencesManager
import com.mhrahmankwoshik.chessy.util.SoundPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChessViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)
    val engineManager = EngineManager(application)

    private val chessGame = ChessGame(playerColor = prefs.playerColor)
    private var engineJob: kotlinx.coroutines.Job? = null

    // Observable UI states
    private val _board = MutableLiveData<ChessBoard>()
    val board: LiveData<ChessBoard> = _board

    private val _gameStatus = MutableLiveData<GameStatus>()
    val gameStatus: LiveData<GameStatus> = _gameStatus

    private val _lastMove = MutableLiveData<Move?>()
    val lastMove: LiveData<Move?> = _lastMove

    private val _isKingInCheck = MutableLiveData<Boolean>()
    val isKingInCheck: LiveData<Boolean> = _isKingInCheck

    private val _checkKingSquare = MutableLiveData<Square?>()
    val checkKingSquare: LiveData<Square?> = _checkKingSquare

    private val _moveRecords = MutableLiveData<List<MoveRecord>>()
    val moveRecords: LiveData<List<MoveRecord>> = _moveRecords

    private val _isEngineThinking = MutableLiveData<Boolean>(false)
    val isEngineThinking: LiveData<Boolean> = _isEngineThinking

    private val _engineStatusText = MutableLiveData<String>("Stockfish Ready")
    val engineStatusText: LiveData<String> = _engineStatusText

    // Default to Stockfish's point of view (if Stockfish is Black / player is White, flip so Stockfish is at bottom)
    private val _isBoardFlipped = MutableLiveData<Boolean>(prefs.playerColor == PieceColor.WHITE)
    val isBoardFlipped: LiveData<Boolean> = _isBoardFlipped

    private val _pendingPromotion = MutableLiveData<Pair<Square, Square>?>()
    val pendingPromotion: LiveData<Pair<Square, Square>?> = _pendingPromotion

    private val _currentOpening = MutableLiveData<Opening?>()
    val currentOpening: LiveData<Opening?> = _currentOpening

    private val _isLastMoveBookMove = MutableLiveData<Boolean>(false)
    val isLastMoveBookMove: LiveData<Boolean> = _isLastMoveBookMove

    // Engine requirement state: if Termux engine is missing, app won't run!
    private val _engineCheckStatus = MutableLiveData<EngineCheckStatus>()
    val engineCheckStatus: LiveData<EngineCheckStatus> = _engineCheckStatus

    private val _engineMissingError = MutableLiveData<String?>()
    val engineMissingError: LiveData<String?> = _engineMissingError

    init {
        updateGameState()
        checkTermuxEngineRequirement()
        viewModelScope.launch(Dispatchers.IO) {
            OpeningDatabase.getOpenings(application)
        }
    }

    fun checkTermuxEngineRequirement() {
        viewModelScope.launch {
            if (!engineManager.isTermuxAppInstalled()) {
                _engineCheckStatus.value = EngineCheckStatus.TermuxAppMissing
                _engineStatusText.value = "Termux App Not Found"
                return@launch
            }

            _engineStatusText.value = "Verifying Stockfish in Termux…"
            val status = engineManager.checkEngineStatus(prefs.getEngineConfig())
            _engineCheckStatus.value = status

            when (status) {
                is EngineCheckStatus.Ready -> {
                    _engineStatusText.value = "Stockfish Ready (Termux)"
                    _engineMissingError.value = null
                    if (chessGame.isEngineTurn()) {
                        triggerEngineMove()
                    }
                }
                is EngineCheckStatus.TermuxAppMissing -> {
                    _engineStatusText.value = "Termux App Not Found"
                }
                is EngineCheckStatus.StockfishNotResponding -> {
                    _engineStatusText.value = "Stockfish Not Installed in Termux"
                }
            }
        }
    }

    var lockedOpening: Opening? = null
        private set

    fun startNewGame(
        playerColor: PieceColor = prefs.playerColor,
        initialOpening: Opening? = null,
        lockedOpening: Opening? = null
    ) {
        this.lockedOpening = lockedOpening
        engineJob?.cancel()
        _isEngineThinking.value = false

        if (_engineCheckStatus.value !is EngineCheckStatus.Ready) {
            checkTermuxEngineRequirement()
            return
        }

        prefs.playerColor = playerColor
        if (initialOpening != null) {
            chessGame.resetGame(playerColor, initialOpening.movesUci)
            _currentOpening.value = initialOpening
        } else {
            chessGame.resetGame(playerColor)
            _currentOpening.value = lockedOpening
        }

        // Default to Stockfish's point of view: Stockfish at bottom
        _isBoardFlipped.value = (playerColor == PieceColor.WHITE)
        _isLastMoveBookMove.value = false
        updateGameState()

        if (chessGame.isEngineTurn()) {
            triggerEngineMove()
        }
    }

    fun getLegalMovesForSquare(square: Square): List<Move> {
        if (_engineCheckStatus.value !is EngineCheckStatus.Ready) return emptyList()
        if (!chessGame.isPlayerTurn()) return emptyList()
        return chessGame.getLegalMovesForSquare(square)
    }

    fun attemptMove(from: Square, to: Square) {
        if (_engineCheckStatus.value !is EngineCheckStatus.Ready) {
            checkTermuxEngineRequirement()
            return
        }
        if (!chessGame.isPlayerTurn()) return

        val piece = chessGame.board.getPiece(from) ?: return
        val isPawn = piece.type == PieceType.PAWN
        val isPromoRank = (piece.color == PieceColor.WHITE && to.rank == 7) ||
                (piece.color == PieceColor.BLACK && to.rank == 0)

        // Check if pawn promotion
        if (isPawn && isPromoRank) {
            val hasPromoMove = chessGame.getLegalMoves().any { it.from == from && it.to == to && it.promotion != null }
            if (hasPromoMove) {
                _pendingPromotion.value = Pair(from, to)
                return
            }
        }

        val move = Move(from = from, to = to)
        if (chessGame.makeMove(move)) {
            handleMoveMade(move)
        }
    }

    fun confirmPromotion(promotionType: PieceType) {
        val pending = _pendingPromotion.value ?: return
        _pendingPromotion.value = null

        val move = Move(from = pending.first, to = pending.second, promotion = promotionType)
        if (chessGame.makeMove(move)) {
            handleMoveMade(move)
        }
    }

    fun cancelPromotion() {
        _pendingPromotion.value = null
    }

    private fun handleMoveMade(move: Move) {
        _isLastMoveBookMove.value = false
        if (move.capturedPiece != null) {
            SoundPlayer.playCapture()
        } else {
            SoundPlayer.playMove()
        }

        _currentOpening.value = lockedOpening ?: OpeningDatabase.detectOpening(chessGame.getMoveHistoryUci(), getApplication())
        updateGameState()

        if (chessGame.status == GameStatus.ACTIVE && chessGame.isEngineTurn()) {
            triggerEngineMove()
        }
    }

    private fun triggerEngineMove() {
        if (_engineCheckStatus.value !is EngineCheckStatus.Ready) {
            checkTermuxEngineRequirement()
            return
        }

        engineJob?.cancel()
        _isEngineThinking.value = true
        _engineStatusText.value = "Stockfish thinking…"

        engineJob = viewModelScope.launch {
            // 1. Check opening book first if enabled
            if (prefs.isOpeningBookEnabled) {
                val engineColor = if (chessGame.playerColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
                val history = chessGame.getMoveHistoryUci()
                val bookResult = withContext(Dispatchers.Default) {
                    OpeningDatabase.findBookMove(
                        currentHistoryUci = history,
                        engineColor = engineColor,
                        selectedIds = prefs.selectedOpeningIds,
                        lockedOpening = lockedOpening,
                        context = getApplication()
                    )
                }
                if (bookResult != null) {
                    _isEngineThinking.value = false
                    val (bookUci, opening) = bookResult
                    val executed = chessGame.makeEngineMove(bookUci)
                    if (executed != null) {
                        if (executed.capturedPiece != null) {
                            SoundPlayer.playCapture()
                        } else {
                            SoundPlayer.playMove()
                        }
                        _engineStatusText.value = "Stockfish (${opening.name})"
                        _currentOpening.value = opening
                        _isLastMoveBookMove.value = true
                        updateGameState()
                        return@launch
                    }
                } else {
                    // If book moves are finished or diverged, clear lockedOpening
                    lockedOpening = null
                }
            }

            // 2. Query Termux engine
            val fen = chessGame.board.toFen()
            val config = prefs.getEngineConfig()

            val bestMoveUci = engineManager.getBestMove(fen, config)

            _isEngineThinking.value = false

            if (bestMoveUci != null) {
                val executed = chessGame.makeEngineMove(bestMoveUci)
                if (executed != null) {
                    if (executed.capturedPiece != null) {
                        SoundPlayer.playCapture()
                    } else {
                        SoundPlayer.playMove()
                    }
                }
                _engineStatusText.value = engineManager.activeEngineDescription
                _isLastMoveBookMove.value = false
                _currentOpening.value = withContext(Dispatchers.Default) {
                    OpeningDatabase.detectOpening(chessGame.getMoveHistoryUci(), getApplication())
                }
                updateGameState()
            } else {
                // Termux engine failed to respond - do NOT use fallback, stop game and prompt user!
                _engineStatusText.value = "Stockfish Engine Error"
                _engineMissingError.value = "Stockfish engine in Termux did not return a move. Please verify Termux is running and Stockfish is installed."
            }
        }
    }

    fun undoMove() {
        if (_isEngineThinking.value == true) {
            engineJob?.cancel()
            _isEngineThinking.value = false
        }
        _isLastMoveBookMove.value = false
        if (chessGame.undoMove()) {
            SoundPlayer.playMove()
            _currentOpening.value = OpeningDatabase.detectOpening(chessGame.getMoveHistoryUci(), getApplication())
            updateGameState()
            if (chessGame.isEngineTurn()) {
                triggerEngineMove()
            }
        }
    }

    fun flipBoard() {
        _isBoardFlipped.value = !(_isBoardFlipped.value ?: false)
    }

    fun resign() {
        chessGame.resign()
        updateGameState()
    }

    fun getPgn(): String = chessGame.toPgn()
    fun getFen(): String = chessGame.board.toFen()

    private fun updateGameState() {
        _board.value = chessGame.board.copy()
        _gameStatus.value = chessGame.status
        _lastMove.value = chessGame.lastMove
        _moveRecords.value = chessGame.getMoveRecords()

        val inCheck = MoveGenerator.isKingInCheck(chessGame.board, chessGame.board.activeColor)
        _isKingInCheck.value = inCheck
        _checkKingSquare.value = if (inCheck) chessGame.board.findKing(chessGame.board.activeColor) else null

        if (inCheck && chessGame.status == GameStatus.ACTIVE) {
            SoundPlayer.playCheck()
        }
    }
}
