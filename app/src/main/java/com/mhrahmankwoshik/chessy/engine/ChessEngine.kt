package com.mhrahmankwoshik.chessy.engine

import com.mhrahmankwoshik.chessy.data.model.EngineConfig

interface ChessEngine {
    suspend fun getBestMove(fen: String, config: EngineConfig, candidateMoves: List<String>? = null): String?
    suspend fun isAvailable(): Boolean
    val engineName: String
}
