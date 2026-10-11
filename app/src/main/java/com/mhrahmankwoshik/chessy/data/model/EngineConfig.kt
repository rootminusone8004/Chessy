package com.mhrahmankwoshik.chessy.data.model

enum class EngineMode {
    TERMUX_SOCKET
}

data class EngineConfig(
    val mode: EngineMode = EngineMode.TERMUX_SOCKET,
    val depth: Int = 10,
    val moveTimeMs: Int = 1000,
    val socketPort: Int = 2222,
    val skillLevel: Int = 20,
    val customEnginePath: String = "stockfish",
    val isManuallyVerified: Boolean = false
)
