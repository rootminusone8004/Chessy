package com.mhrahmankwoshik.chessy.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.mhrahmankwoshik.chessy.data.model.EngineConfig
import com.mhrahmankwoshik.chessy.util.TermuxUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class DiagnosticResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val infoMessage: String
)

class TermuxStockfishEngine(
    private val context: Context
) : ChessEngine {

    override val engineName: String = "Stockfish (Termux)"

    override suspend fun isAvailable(): Boolean {
        return isTermuxInstalled()
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo("com.termux", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("com.termux", 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun buildEngineResolutionScript(customPath: String): String {
        return buildString {
            append("export PATH=/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/home/bin:\$PATH; ")
            append("CUSTOM=\"${customPath.trim()}\"; ")
            append("if [ -n \"\$CUSTOM\" ] && [ -x \"\$CUSTOM\" ]; then ENG=\"\$CUSTOM\"; ")
            append("elif [ -n \"\$CUSTOM\" ] && command -v \"\$CUSTOM\" >/dev/null 2>&1; then ENG=\"\$CUSTOM\"; ")
            append("elif command -v stockfish >/dev/null 2>&1; then ENG=\"\$(command -v stockfish)\"; ")
            append("elif [ -f /data/data/com.termux/files/usr/bin/stockfish ]; then ENG=\"/data/data/com.termux/files/usr/bin/stockfish\"; ")
            append("elif [ -f /data/data/com.termux/files/home/stockfish ]; then ENG=\"/data/data/com.termux/files/home/stockfish\"; ")
            append("elif [ -f /data/data/com.termux/files/home/storage/shared/stockfish ]; then ENG=\"/data/data/com.termux/files/home/storage/shared/stockfish\"; ")
            append("elif [ -f /data/data/com.termux/files/home/storage/downloads/stockfish ]; then ENG=\"/data/data/com.termux/files/home/storage/downloads/stockfish\"; ")
            append("elif [ -f /sdcard/Download/stockfish ]; then ENG=\"/sdcard/Download/stockfish\"; ")
            append("else ENG=\"stockfish\"; fi; ")
        }
    }

    suspend fun runDiagnostic(config: EngineConfig): DiagnosticResult {
        if (!isTermuxInstalled()) {
            return DiagnosticResult(
                success = false,
                stdout = "",
                stderr = "Termux package (com.termux) is not installed on this Android device.",
                exitCode = -1,
                infoMessage = "Please install Termux from F-Droid."
            )
        }

        if (!TermuxUtils.hasRunCommandPermission(context)) {
            return DiagnosticResult(
                success = false,
                stdout = "",
                stderr = "Permission com.termux.permission.RUN_COMMAND is not granted to Chessy.",
                exitCode = -1,
                infoMessage = "Grant 'Run commands in Termux environment' in Android Settings -> Apps -> Chessy -> Permissions -> Additional permissions, OR use Method 1 (Direct Socket) which requires no permissions."
            )
        }

        val resolutionScript = buildEngineResolutionScript(config.customEnginePath)
        val testCommand = "$resolutionScript echo \"[ENGINE_PATH]=\$ENG\"; printf 'uci\\nquit\\n' | \"\$ENG\""

        val result = withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<DiagnosticResult> { continuation ->
                TermuxResultReceiver.listener = { stdout, stderr, exitCode ->
                    TermuxResultReceiver.listener = null
                    val success = (exitCode == 0 || stdout.contains("uciok", ignoreCase = true) || stdout.contains("Stockfish", ignoreCase = true))
                    val info = if (success) {
                        "Stockfish responded successfully!"
                    } else if (exitCode == 127) {
                        "Stockfish binary not found at specified path. Run 'pkg install stockfish' in Termux."
                    } else if (stderr.contains("permission", ignoreCase = true)) {
                        "Permission issue inside Termux. Ensure allow-external-apps=true is in ~/.termux/termux.properties."
                    } else {
                        "Stockfish execution failed with exit code $exitCode."
                    }

                    if (continuation.isActive) {
                        continuation.resume(DiagnosticResult(success, stdout, stderr, exitCode, info))
                    }
                }

                continuation.invokeOnCancellation {
                    TermuxResultReceiver.listener = null
                }

                val (launched, launchError) = sendRunCommand(testCommand)
                if (!launched && continuation.isActive) {
                    continuation.resume(
                        DiagnosticResult(
                            success = false,
                            stdout = "",
                            stderr = launchError ?: "Failed to send Intent to com.termux.RunCommandService.",
                            exitCode = -1,
                            infoMessage = "Ensure Termux app is running in background and allows external apps."
                        )
                    )
                }
            }
        }

        return result ?: DiagnosticResult(
            success = false,
            stdout = "",
            stderr = "Termux did not respond within timeout (5s).",
            exitCode = -1,
            infoMessage = "Termux dropped the command. Add 'allow-external-apps = true' to ~/.termux/termux.properties and run 'termux-reload-settings', or use Method 1 (Direct Socket)."
        )
    }

    suspend fun testStockfishInstalled(config: EngineConfig = EngineConfig()): Boolean {
        if (!isTermuxInstalled()) return false
        val diag = runDiagnostic(config)
        return diag.success
    }

    override suspend fun getBestMove(fen: String, config: EngineConfig): String? {
        val resolutionScript = buildEngineResolutionScript(config.customEnginePath)
        val bashCommand = buildString {
            append(resolutionScript)
            append("printf 'uci\\nsetoption name Skill Level value ${config.skillLevel}\\n")
            append("position fen $fen\\n")
            append("go depth ${config.depth} movetime ${config.moveTimeMs}\\n' | ")
            append("\"\$ENG\"")
        }

        val timeoutMs = (config.moveTimeMs + 5000L).coerceAtLeast(TIMEOUT_MS)
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                TermuxResultReceiver.listener = { stdout, stderr, exitCode ->
                    TermuxResultReceiver.listener = null
                    Log.d(TAG, "Termux finished with exitCode=$exitCode")
                    if (stdout.isNotBlank()) {
                        val move = parseBestMove(stdout)
                        if (continuation.isActive) {
                            continuation.resume(move)
                        }
                    } else {
                        Log.w(TAG, "Termux returned empty stdout. stderr: $stderr")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    TermuxResultReceiver.listener = null
                }

                val (launched, launchError) = sendRunCommand(bashCommand)
                if (!launched && continuation.isActive) {
                    Log.e(TAG, "Failed to launch getBestMove command: $launchError")
                    continuation.resume(null)
                }
            }
        }
    }

    fun sendRunCommand(command: String): Pair<Boolean, String?> {
        if (!TermuxUtils.hasRunCommandPermission(context)) {
            val msg = "Permission com.termux.permission.RUN_COMMAND is NOT granted. Go to Settings -> Apps -> Chessy -> Permissions -> Additional permissions -> Run commands in Termux environment -> Allow."
            Log.e(TAG, msg)
            return Pair(false, msg)
        }

        return try {
            val callbackIntent = Intent(context, TermuxResultReceiver::class.java).apply {
                action = TermuxResultReceiver.ACTION_TERMUX_RESULT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                callbackIntent,
                flags
            )

            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            Pair(true, null)
        } catch (e: SecurityException) {
            val msg = "SecurityException: ${e.localizedMessage ?: e.message}. Ensure RUN_COMMAND permission is granted in Settings."
            Log.e(TAG, msg, e)
            Pair(false, msg)
        } catch (e: Exception) {
            val msg = "Failed to launch RunCommandService (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message}"
            Log.e(TAG, msg, e)
            Pair(false, msg)
        }
    }

    private fun parseBestMove(output: String): String? {
        val pattern = Pattern.compile("bestmove\\s+([a-h][1-8][a-h][1-8][qrbn]?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(output)
        return if (matcher.find()) {
            matcher.group(1)?.lowercase()
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "TermuxStockfishEngine"
        private const val REQUEST_CODE = 8004
        private const val TIMEOUT_MS = 6000L
    }
}
