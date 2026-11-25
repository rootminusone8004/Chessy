package com.mhrahmankwoshik.chessy.game

import com.mhrahmankwoshik.chessy.data.model.GameStatus
import com.mhrahmankwoshik.chessy.data.model.Piece
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.data.model.PieceType
import com.mhrahmankwoshik.chessy.data.model.Square

object FenUtil {
    const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    data class FenState(
        val board: Array<Array<Piece?>>,
        val activeColor: PieceColor,
        val whiteKingSideCastle: Boolean,
        val whiteQueenSideCastle: Boolean,
        val blackKingSideCastle: Boolean,
        val blackQueenSideCastle: Boolean,
        val enPassantTarget: Square?,
        val halfMoveClock: Int,
        val fullMoveNumber: Int
    )

    fun parse(fen: String): FenState {
        val parts = fen.trim().split("\\s+".toRegex())
        require(parts.isNotEmpty()) { "Invalid FEN string" }

        val piecePlacement = parts[0]
        val activeColor = if (parts.size > 1 && parts[1].equals("b", ignoreCase = true)) PieceColor.BLACK else PieceColor.WHITE
        val castling = if (parts.size > 2) parts[2] else "-"
        val enPassantStr = if (parts.size > 3) parts[3] else "-"
        val halfMove = if (parts.size > 4) parts[4].toIntOrNull() ?: 0 else 0
        val fullMove = if (parts.size > 5) parts[5].toIntOrNull() ?: 1 else 1

        val grid = Array(8) { Array<Piece?>(8) { null } }
        val rows = piecePlacement.split("/")
        require(rows.size == 8) { "Invalid piece placement rows in FEN" }

        for (rankIndex in 0..7) {
            val fenRow = rows[rankIndex]
            val rank = 7 - rankIndex // row 0 in FEN is rank 8 (index 7)
            var file = 0
            for (char in fenRow) {
                if (char.isDigit()) {
                    file += char.digitToInt()
                } else {
                    val color = if (char.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = PieceType.fromChar(char) ?: error("Unknown piece char: $char")
                    if (file in 0..7) {
                        grid[file][rank] = Piece(type, color)
                    }
                    file++
                }
            }
        }

        val enPassant = if (enPassantStr != "-") Square.fromNotation(enPassantStr) else null

        return FenState(
            board = grid,
            activeColor = activeColor,
            whiteKingSideCastle = castling.contains('K'),
            whiteQueenSideCastle = castling.contains('Q'),
            blackKingSideCastle = castling.contains('k'),
            blackQueenSideCastle = castling.contains('q'),
            enPassantTarget = enPassant,
            halfMoveClock = halfMove,
            fullMoveNumber = fullMove
        )
    }

    /**
     * Validates a user-supplied FEN for use as a game starting position.
     * Returns a human-readable error, or null if the position is playable.
     */
    fun validate(fen: String): String? {
        val parts = fen.trim().split("\\s+".toRegex())
        if (parts.isEmpty() || parts[0].isBlank()) return "FEN is empty"

        val rows = parts[0].split("/")
        if (rows.size != 8) return "Piece placement must have 8 ranks separated by '/'"
        for ((index, row) in rows.withIndex()) {
            var count = 0
            for (char in row) {
                count += when {
                    char in '1'..'8' -> char.digitToInt()
                    PieceType.fromChar(char) != null -> 1
                    else -> return "Unknown character '$char' in rank ${8 - index}"
                }
            }
            if (count != 8) return "Rank ${8 - index} has $count squares instead of 8"
        }
        if (parts.size > 1 && parts[1].lowercase() !in setOf("w", "b")) {
            return "Side to move must be 'w' or 'b'"
        }

        val board = try {
            ChessBoard().apply { loadFen(fen) }
        } catch (e: Exception) {
            return e.message ?: "Invalid FEN"
        }

        var whiteKings = 0
        var blackKings = 0
        for (f in 0..7) {
            for (r in 0..7) {
                val piece = board.getPiece(f, r) ?: continue
                if (piece.type == PieceType.KING) {
                    if (piece.color == PieceColor.WHITE) whiteKings++ else blackKings++
                }
                if (piece.type == PieceType.PAWN && (r == 0 || r == 7)) {
                    return "Pawns cannot be on the first or last rank"
                }
            }
        }
        if (whiteKings != 1 || blackKings != 1) return "Each side must have exactly one king"

        val waitingSide = if (board.activeColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        if (MoveGenerator.isKingInCheck(board, waitingSide)) {
            return "The side not to move is in check"
        }
        return when (MoveGenerator.getGameStatus(board)) {
            GameStatus.CHECKMATE -> "Position is already checkmate"
            GameStatus.STALEMATE -> "Position is already stalemate"
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "Position is a draw by insufficient material"
            GameStatus.DRAW_FIFTY_MOVES -> "Half-move clock is already at the fifty-move limit"
            else -> null
        }
    }

    data class CastlingAvailability(
        val whiteKingSide: Boolean,
        val whiteQueenSide: Boolean,
        val blackKingSide: Boolean,
        val blackQueenSide: Boolean
    )

    /** Which castling rights the piece placement allows (king and rook on their original squares). */
    fun castlingPossible(board: ChessBoard): CastlingAvailability {
        fun isPiece(file: Int, rank: Int, type: PieceType, color: PieceColor): Boolean {
            val p = board.getPiece(file, rank)
            return p != null && p.type == type && p.color == color
        }
        val wKing = isPiece(4, 0, PieceType.KING, PieceColor.WHITE)
        val bKing = isPiece(4, 7, PieceType.KING, PieceColor.BLACK)
        return CastlingAvailability(
            whiteKingSide = wKing && isPiece(7, 0, PieceType.ROOK, PieceColor.WHITE),
            whiteQueenSide = wKing && isPiece(0, 0, PieceType.ROOK, PieceColor.WHITE),
            blackKingSide = bKing && isPiece(7, 7, PieceType.ROOK, PieceColor.BLACK),
            blackQueenSide = bKing && isPiece(0, 7, PieceType.ROOK, PieceColor.BLACK)
        )
    }

    /**
     * Returns the FEN with castling rights and en-passant target dropped where the
     * position doesn't support them (e.g. king or rook not on its original square).
     * Assumes [fen] already passed [validate].
     */
    fun sanitize(fen: String): String {
        val board = ChessBoard().apply { loadFen(fen) }
        fun isPiece(file: Int, rank: Int, type: PieceType, color: PieceColor): Boolean {
            val p = board.getPiece(file, rank)
            return p != null && p.type == type && p.color == color
        }
        val possible = castlingPossible(board)
        board.whiteKingSideCastle = board.whiteKingSideCastle && possible.whiteKingSide
        board.whiteQueenSideCastle = board.whiteQueenSideCastle && possible.whiteQueenSide
        board.blackKingSideCastle = board.blackKingSideCastle && possible.blackKingSide
        board.blackQueenSideCastle = board.blackQueenSideCastle && possible.blackQueenSide

        board.enPassantTarget?.let { ep ->
            // The pawn that just double-pushed belongs to the side not to move
            val (expectedRank, pawnRank, pawnColor) = if (board.activeColor == PieceColor.WHITE) {
                Triple(5, 4, PieceColor.BLACK)
            } else {
                Triple(2, 3, PieceColor.WHITE)
            }
            if (ep.rank != expectedRank || !isPiece(ep.file, pawnRank, PieceType.PAWN, pawnColor)) {
                board.enPassantTarget = null
            }
        }
        if (board.fullMoveNumber < 1) board.fullMoveNumber = 1
        if (board.halfMoveClock < 0) board.halfMoveClock = 0
        return board.toFen()
    }

    fun toFen(
        board: Array<Array<Piece?>>,
        activeColor: PieceColor,
        wK: Boolean,
        wQ: Boolean,
        bK: Boolean,
        bQ: Boolean,
        enPassant: Square?,
        halfMoveClock: Int,
        fullMoveNumber: Int
    ): String {
        val sb = StringBuilder()

        // 1. Piece placement
        for (rank in 7 downTo 0) {
            var emptyCount = 0
            for (file in 0..7) {
                val piece = board[file][rank]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (rank > 0) sb.append('/')
        }

        // 2. Active color
        sb.append(if (activeColor == PieceColor.WHITE) " w " else " b ")

        // 3. Castling
        val castleSb = StringBuilder()
        if (wK) castleSb.append('K')
        if (wQ) castleSb.append('Q')
        if (bK) castleSb.append('k')
        if (bQ) castleSb.append('q')
        if (castleSb.isEmpty()) castleSb.append('-')
        sb.append(castleSb.toString())

        // 4. En passant
        sb.append(' ')
        sb.append(enPassant?.notation ?: "-")

        // 5. Halfmove clock & Fullmove number
        sb.append(" $halfMoveClock $fullMoveNumber")

        return sb.toString()
    }
}
