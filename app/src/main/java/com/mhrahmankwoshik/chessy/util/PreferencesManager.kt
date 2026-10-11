package com.mhrahmankwoshik.chessy.util

import android.content.Context
import android.content.SharedPreferences
import com.mhrahmankwoshik.chessy.data.model.EngineConfig
import com.mhrahmankwoshik.chessy.data.model.EngineMode
import com.mhrahmankwoshik.chessy.data.model.PieceColor

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chessy_prefs", Context.MODE_PRIVATE)

    var playerColor: PieceColor
        get() {
            val name = prefs.getString("player_color", PieceColor.WHITE.name)
            return try { PieceColor.valueOf(name!!) } catch (e: Exception) { PieceColor.WHITE }
        }
        set(value) = prefs.edit().putString("player_color", value.name).apply()

    var engineDepth: Int
        get() = prefs.getInt("engine_depth", 10)
        set(value) = prefs.edit().putInt("engine_depth", value).apply()

    var engineMode: EngineMode
        get() {
            val name = prefs.getString("engine_mode", EngineMode.TERMUX_SOCKET.name)
            return try { EngineMode.valueOf(name!!) } catch (e: Exception) { EngineMode.TERMUX_SOCKET }
        }
        set(value) = prefs.edit().putString("engine_mode", value.name).apply()

    var socketPort: Int
        get() = prefs.getInt("socket_port", 2222)
        set(value) = prefs.edit().putInt("socket_port", value).apply()

    var customEnginePath: String
        get() = prefs.getString("custom_engine_path", "stockfish") ?: "stockfish"
        set(value) = prefs.edit().putString("custom_engine_path", value).apply()

    var isManuallyVerified: Boolean
        get() = prefs.getBoolean("is_manually_verified", false)
        set(value) = prefs.edit().putBoolean("is_manually_verified", value).apply()

    var selectedOpeningIds: Set<String>
        get() {
            return prefs.getStringSet("selected_openings", null) ?: emptySet()
        }
        set(value) = prefs.edit().putStringSet("selected_openings", value).apply()

    var isOpeningBookEnabled: Boolean
        get() = prefs.getBoolean("is_opening_book_enabled", true)
        set(value) = prefs.edit().putBoolean("is_opening_book_enabled", value).apply()

    fun getEngineConfig(): EngineConfig {
        return EngineConfig(
            mode = engineMode,
            depth = engineDepth,
            socketPort = socketPort,
            customEnginePath = customEnginePath,
            isManuallyVerified = isManuallyVerified
        )
    }
}
