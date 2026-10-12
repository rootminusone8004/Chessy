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
}
