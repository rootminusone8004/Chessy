package com.mhrahmankwoshik.chessy.engine

import android.util.Log
import com.mhrahmankwoshik.chessy.data.model.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

class TermuxSocketEngine : ChessEngine {

    override val engineName: String = "Stockfish (Termux Direct Socket)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        isAvailableOnPort(2222)
    }

    suspend fun isAvailableOnPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testSocket(port: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 1000)
            socket.soTimeout = 1500

            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.write("uci\n")
            writer.flush()

            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    response.append(it).append("\n")
                    if (it.contains("uciok", ignoreCase = true)) {
                        return@withContext Pair(true, response.toString())
                    }
                }
            }
            Pair(true, response.toString())
        } catch (e: Exception) {
            Pair(false, "Could not connect to 127.0.0.1:$port: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    override suspend fun getBestMove(fen: String, config: EngineConfig): String? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", config.socketPort), 2500)
            socket.soTimeout = maxOf(8000, config.moveTimeMs + 4000)

            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            // Initialize UCI
            writer.write("uci\n")
            writer.write("setoption name Skill Level value ${config.skillLevel}\n")
            writer.write("position fen $fen\n")
            writer.write("go depth ${config.depth} movetime ${config.moveTimeMs}\n")
            writer.flush()

            val pattern = Pattern.compile("bestmove\\s+([a-h][1-8][a-h][1-8][qrbn]?)", Pattern.CASE_INSENSITIVE)
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val matcher = pattern.matcher(it)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)?.lowercase()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Socket engine error on port ${config.socketPort}: ${e.message}")
            null
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        private const val TAG = "TermuxSocketEngine"
    }
}
