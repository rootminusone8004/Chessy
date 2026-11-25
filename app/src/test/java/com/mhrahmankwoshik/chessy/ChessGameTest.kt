package com.mhrahmankwoshik.chessy

import com.mhrahmankwoshik.chessy.data.model.GameStatus
import com.mhrahmankwoshik.chessy.data.model.Move
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.data.model.Square
import com.mhrahmankwoshik.chessy.game.ChessGame
import com.mhrahmankwoshik.chessy.game.FenUtil
import com.mhrahmankwoshik.chessy.game.MoveGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessGameTest {

    @Test
    fun testStartingPositionLegalMoves() {
        val game = ChessGame()
        val legalMoves = game.getLegalMoves()
        // There are 20 legal moves in starting chess position (16 pawn moves + 4 knight moves)
        assertEquals(20, legalMoves.size)
    }

    @Test
    fun testMakePawnMoveAndUndo() {
        val game = ChessGame()
        val e4 = Move.fromUci("e2e4")!!
        assertTrue(game.makeMove(e4))
        assertEquals(1, game.moveHistory.size)
        assertEquals(Square(4, 3), game.lastMove?.to)

        // Undo
        assertTrue(game.undoMove())
        assertEquals(0, game.moveHistory.size)
        assertEquals(FenUtil.STARTING_FEN, game.board.toFen())
    }

    @Test
    fun testScholarsMateCheckmate() {
        val game = ChessGame()
        // 1. e4 e5
        assertTrue(game.makeMove(Move.fromUci("e2e4")!!))
        assertTrue(game.makeMove(Move.fromUci("e7e5")!!))
        // 2. Qh5 Nc6
        assertTrue(game.makeMove(Move.fromUci("d1h5")!!))
        assertTrue(game.makeMove(Move.fromUci("b8c6")!!))
        // 3. Bc4 Nf6
        assertTrue(game.makeMove(Move.fromUci("f1c4")!!))
        assertTrue(game.makeMove(Move.fromUci("g8f6")!!))
        // 4. Qxf7#
        assertTrue(game.makeMove(Move.fromUci("h5f7")!!))

        assertEquals(GameStatus.CHECKMATE, game.status)
        assertTrue(MoveGenerator.isKingInCheck(game.board, PieceColor.BLACK))
    }

    @Test
    fun testKingsideCastling() {
        val game = ChessGame()
        // Set up position where white can castle kingside
        game.board.loadFen("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
        val oO = Move.fromUci("e1g1")!!
        val legalMoves = game.getLegalMoves()
        val castlingMove = legalMoves.find { it.from == oO.from && it.to == oO.to }
        assertNotNull(castlingMove)
        assertTrue(castlingMove!!.isCastling)

        assertTrue(game.makeMove(castlingMove))
        // King is on g1 (file 6, rank 0), Rook is on f1 (file 5, rank 0)
        assertEquals(Square(6, 0), game.board.findKing(PieceColor.WHITE))
        assertNotNull(game.board.getPiece(Square(5, 0)))
    }

    @Test
    fun testFenRoundtrip() {
        val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"
        val state = FenUtil.parse(fen)
        val generated = FenUtil.toFen(
            board = state.board,
            activeColor = state.activeColor,
            wK = state.whiteKingSideCastle,
            wQ = state.whiteQueenSideCastle,
            bK = state.blackKingSideCastle,
            bQ = state.blackQueenSideCastle,
            enPassant = state.enPassantTarget,
            halfMoveClock = state.halfMoveClock,
            fullMoveNumber = state.fullMoveNumber
        )
        assertEquals(fen, generated)
    }

    @Test
    fun testPlayAsBlackEngineTurnFirst() {
        val game = ChessGame(playerColor = PieceColor.BLACK)
        // At start, activeColor is WHITE, so it's Engine's turn, not Player's turn
        assertFalse(game.isPlayerTurn())
        assertTrue(game.isEngineTurn())

        // Engine plays 1. e4
        val engineMove = game.makeEngineMove("e2e4")
        assertNotNull(engineMove)
        assertEquals(Square(4, 3), engineMove!!.to)

        // Now activeColor is BLACK, so it's Player's turn
        assertTrue(game.isPlayerTurn())
        assertFalse(game.isEngineTurn())

        // Player plays 1... e5
        val playerMove = Move.fromUci("e7e5")!!
        assertTrue(game.makeMove(playerMove))

        // Now activeColor is WHITE, back to Engine's turn
        assertFalse(game.isPlayerTurn())
        assertTrue(game.isEngineTurn())
    }

    @Test
    fun testResetGameSwitchingColors() {
        val game = ChessGame(playerColor = PieceColor.WHITE)
        assertTrue(game.isPlayerTurn())
        assertFalse(game.isEngineTurn())

        game.resetGame(PieceColor.BLACK)
        assertEquals(PieceColor.BLACK, game.playerColor)
        assertFalse(game.isPlayerTurn())
        assertTrue(game.isEngineTurn())
    }

    @Test
    fun testHasAnyLegalMoveStartingPosition() {
        val game = ChessGame()
        assertTrue(MoveGenerator.hasAnyLegalMove(game.board))
    }

    @Test
    fun testHasAnyLegalMoveCheckmateAndStalemate() {
        val game = ChessGame()
        // Scholar's mate
        game.makeMove(Move.fromUci("e2e4")!!)
        game.makeMove(Move.fromUci("e7e5")!!)
        game.makeMove(Move.fromUci("d1h5")!!)
        game.makeMove(Move.fromUci("b8c6")!!)
        game.makeMove(Move.fromUci("f1c4")!!)
        game.makeMove(Move.fromUci("g8f6")!!)
        game.makeMove(Move.fromUci("h5f7")!!)

        assertEquals(GameStatus.CHECKMATE, game.status)
        assertFalse(MoveGenerator.hasAnyLegalMove(game.board))

        // Stalemate test: 7k/5Q2/6K1/8/8/8/8/8 b - - 0 1 (Black king on h8, White Q on f7, K on g6)
        game.board.loadFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(MoveGenerator.hasAnyLegalMove(game.board))
        assertEquals(GameStatus.STALEMATE, MoveGenerator.getGameStatus(game.board))
    }

    @Test
    fun testGenerateLegalMovesForSquare() {
        val game = ChessGame()
        // e2 pawn in starting position: e3, e4
        val e2Square = Square(4, 1)
        val e2Moves = game.getLegalMovesForSquare(e2Square)
        assertEquals(2, e2Moves.size)
        assertTrue(e2Moves.any { it.to == Square(4, 2) })
        assertTrue(e2Moves.any { it.to == Square(4, 3) })

        // b1 knight: a3, c3
        val b1Square = Square(1, 0)
        val b1Moves = game.getLegalMovesForSquare(b1Square)
        assertEquals(2, b1Moves.size)

        // Empty square e4
        val e4Square = Square(4, 3)
        assertTrue(game.getLegalMovesForSquare(e4Square).isEmpty())

        // Opponent piece (Black pawn on e7 while White to move)
        val e7Square = Square(4, 6)
        assertTrue(game.getLegalMovesForSquare(e7Square).isEmpty())
    }

    @Test
    fun testUndoEngineMoveOnlyKeepsPlayerMoveIntact() {
        val game = ChessGame(playerColor = PieceColor.WHITE)
        // 1. Player plays e4
        val playerMove = Move.fromUci("e2e4")!!
        assertTrue(game.makeMove(playerMove))
        assertEquals(PieceColor.BLACK, game.board.activeColor)
        val fenAfterPlayerMove = game.board.toFen()

        // 2. Engine plays e5
        val engineMove = game.makeEngineMove("e7e5")
        assertNotNull(engineMove)
        assertEquals(PieceColor.WHITE, game.board.activeColor)
        assertTrue(game.isPlayerTurn())

        // 3. User calls undoEngineMoveOnly()
        val undone = game.undoEngineMoveOnly()
        assertEquals("e7e5", undone?.uci)

        // Verify that board has reverted to the position AFTER player's move e4,
        // so it is BLACK's turn (Engine's turn), NOT White's turn!
        assertEquals(PieceColor.BLACK, game.board.activeColor)
        assertEquals(fenAfterPlayerMove, game.board.toFen())
        assertEquals("e2e4", game.lastMove?.uci)
        assertEquals(1, game.moveHistory.size)
        assertEquals("e2e4", game.moveHistory.first().uci)
    }

    @Test
    fun testUndoEngineMoveOnlyMultiPly() {
        val game = ChessGame(playerColor = PieceColor.WHITE)
        game.makeMove(Move.fromUci("e2e4")!!)
        game.makeEngineMove("e7e5")
        game.makeMove(Move.fromUci("g1f3")!!)
        val fenAfterNf3 = game.board.toFen()
        game.makeEngineMove("b8c6")

        assertEquals(PieceColor.WHITE, game.board.activeColor)
        assertTrue(game.isPlayerTurn())

        val undone = game.undoEngineMoveOnly()
        assertEquals("b8c6", undone?.uci)
        assertEquals(PieceColor.BLACK, game.board.activeColor)
        assertEquals(fenAfterNf3, game.board.toFen())
        assertEquals("g1f3", game.lastMove?.uci)
        assertEquals(3, game.moveHistory.size)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), game.getMoveHistoryUci())
    }

    @Test
    fun testUndoEngineMoveOnlyWhenPlayerIsBlack() {
        val game = ChessGame(playerColor = PieceColor.BLACK)
        // Engine plays 1. e4
        game.makeEngineMove("e2e4")
        assertTrue(game.isPlayerTurn())

        val undone = game.undoEngineMoveOnly()
        assertEquals("e2e4", undone?.uci)
        assertEquals(PieceColor.WHITE, game.board.activeColor)
        assertNull(game.lastMove)
        assertTrue(game.moveHistory.isEmpty())
    }

    @Test
    fun testManualEngineMoveFlow() {
        val game = ChessGame(playerColor = PieceColor.WHITE)
        // 1. Player plays 1. e4
        assertTrue(game.makeMove(Move.fromUci("e2e4")!!))
        // 2. Engine plays 1... e5
        assertNotNull(game.makeEngineMove("e7e5"))
        assertTrue(game.isPlayerTurn())
        assertEquals(2, game.moveHistory.size)

        // 3. User decides to revoke 1... e5 and play 1... c5 on Stockfish's behalf
        val undone = game.undoEngineMoveOnly()
        assertEquals("e7e5", undone?.uci)
        assertTrue(game.isEngineTurn())
        assertEquals(1, game.moveHistory.size)

        // 4. User plays 1... c5 on behalf of Stockfish
        val manualEngineMove = game.makeEngineMove("c7c5")
        assertNotNull(manualEngineMove)
        assertEquals("c7c5", manualEngineMove?.uci)

        // 5. Turn returns to player, game history reflects alternative
        assertTrue(game.isPlayerTurn())
        assertEquals(2, game.moveHistory.size)
        assertEquals(listOf("e2e4", "c7c5"), game.getMoveHistoryUci())
        assertEquals("c7c5", game.lastMove?.uci)
    }

    @Test
    fun testCustomPositionStartBlackToMove() {
        val fen = "4k3/8/8/8/8/8/4P3/4K3 b - - 0 40"
        val game = ChessGame(playerColor = PieceColor.WHITE)
        game.resetGame(PieceColor.WHITE, startFen = fen)

        assertTrue(game.isCustomPosition)
        assertEquals(fen, game.board.toFen())
        assertTrue(game.isEngineTurn())

        assertNotNull(game.makeEngineMove("e8d7"))
        assertTrue(game.makeMove(Move.fromUci("e2e4")!!))

        val records = game.getMoveRecords()
        assertEquals(2, records.size)
        assertEquals(40, records[0].moveNumber)
        assertEquals("Kd7", records[0].blackMove)
        assertEquals(41, records[1].moveNumber)
        assertEquals("e4", records[1].whiteMove)

        val pgn = game.toPgn()
        assertTrue(pgn.contains("[SetUp \"1\"]"))
        assertTrue(pgn.contains("[FEN \"$fen\"]"))
        assertTrue(pgn.contains("40... Kd7 41. e4"))

        // Undo returns to the custom start position, not the standard one
        assertTrue(game.undoMove())
        assertTrue(game.undoMove())
        assertEquals(fen, game.board.toFen())
    }

    @Test
    fun testStandardResetClearsCustomPosition() {
        val game = ChessGame()
        game.resetGame(PieceColor.WHITE, startFen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        game.resetGame(PieceColor.WHITE)
        assertFalse(game.isCustomPosition)
        assertEquals(FenUtil.STARTING_FEN, game.board.toFen())
    }

    @Test
    fun testFenValidation() {
        assertNull(FenUtil.validate(FenUtil.STARTING_FEN))
        assertNull(FenUtil.validate("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"))
        assertNotNull(FenUtil.validate(""))
        assertNotNull(FenUtil.validate("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1")) // 7 ranks
        assertNotNull(FenUtil.validate("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")) // bad rank width
        assertNotNull(FenUtil.validate("8/8/8/8/8/8/4P3/4K3 w - - 0 1")) // missing black king
        assertNotNull(FenUtil.validate("4k3/8/8/8/8/8/8/4KP2 w - - 0 1")) // pawn on first rank
        assertNotNull(FenUtil.validate("4k3/8/8/8/8/8/8/4R1K1 w - - 0 1")) // side not to move in check
        assertNotNull(FenUtil.validate("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")) // stalemate
        assertNotNull(FenUtil.validate("4k3/8/8/8/8/8/8/4K3 w - - 0 1")) // insufficient material
    }

    @Test
    fun testFenSanitizeDropsImpossibleCastlingAndEnPassant() {
        // White king not on e1, so no white castling; no pawn on e4 for the ep target
        val sanitized = FenUtil.sanitize("r3k2r/8/8/8/8/8/8/R4K1R b KQkq e3 0 1")
        assertEquals("r3k2r/8/8/8/8/8/8/R4K1R b kq - 0 1", sanitized)
    }
}
