package com.mhrahmankwoshik.chessy.engine

import android.content.Context
import com.mhrahmankwoshik.chessy.data.model.EngineConfig
import com.mhrahmankwoshik.chessy.util.TermuxUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class EngineCheckStatus {
    object Ready : EngineCheckStatus()
    object TermuxAppMissing : EngineCheckStatus()
    data class StockfishNotResponding(val diagnostic: DiagnosticResult?, val socketError: String?) : EngineCheckStatus()
}

class EngineManager(
    private val context: Context
) {
    val termuxSocketEngine = TermuxSocketEngine()

    var activeEngineDescription: String = "Termux Stockfish (127.0.0.1:2222)"
        private set

    suspend fun checkEngineStatus(config: EngineConfig): EngineCheckStatus = withContext(Dispatchers.IO) {
        if (!isTermuxAppInstalled()) {
            return@withContext EngineCheckStatus.TermuxAppMissing
        }

        // Check if Direct Socket Bridge is active on localhost
        val socketActive = termuxSocketEngine.isAvailableOnPort(config.socketPort)
        if (socketActive) {
            activeEngineDescription = "Direct Termux Socket (127.0.0.1:${config.socketPort})"
            return@withContext EngineCheckStatus.Ready
        }

        // If the user manually verified their engine setup
        if (config.isManuallyVerified) {
            activeEngineDescription = "Termux Stockfish (Manual Confirmed: ${config.socketPort})"
            return@withContext EngineCheckStatus.Ready
        }

        val (socketSuccess, socketMsg) = termuxSocketEngine.testSocket(config.socketPort)
        if (socketSuccess) {
            activeEngineDescription = "Direct Termux Socket (127.0.0.1:${config.socketPort})"
            EngineCheckStatus.Ready
        } else {
            EngineCheckStatus.StockfishNotResponding(null, socketMsg)
        }
    }

    suspend fun runDiagnostic(config: EngineConfig): DiagnosticResult = withContext(Dispatchers.IO) {
        val (socketSuccess, socketMsg) = termuxSocketEngine.testSocket(config.socketPort)
        if (socketSuccess) {
            DiagnosticResult(
                success = true,
                stdout = socketMsg,
                stderr = "",
                exitCode = 0,
                infoMessage = "Connected directly to Stockfish via Localhost Socket 127.0.0.1:${config.socketPort}!"
            )
        } else {
            DiagnosticResult(
                success = false,
                stdout = "",
                stderr = socketMsg ?: "Cannot connect to 127.0.0.1:${config.socketPort}",
                exitCode = 1,
                infoMessage = "Ensure Termux is running and socat bridge is listening on port ${config.socketPort}."
            )
        }
    }

    suspend fun getBestMove(fen: String, config: EngineConfig): String? = withContext(Dispatchers.Default) {
        activeEngineDescription = "Direct Termux Socket (127.0.0.1:${config.socketPort})"
        termuxSocketEngine.getBestMove(fen, config)
    }

    fun isTermuxAppInstalled(): Boolean {
        return TermuxUtils.isTermuxInstalled(context)
    }

    companion object {
        private const val TAG = "EngineManager"
    }
}
