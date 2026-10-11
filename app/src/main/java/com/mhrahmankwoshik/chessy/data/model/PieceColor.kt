package com.mhrahmankwoshik.chessy.data.model

enum class PieceColor {
    WHITE, BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}
