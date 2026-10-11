package com.mhrahmankwoshik.chessy.data

import android.content.Context
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object OpeningDatabase {

    private var customOpeningsCache: MutableList<Opening>? = null

    val allOpenings: List<Opening>
        get() = customOpeningsCache ?: emptyList()

    val defaultSelectedIds: Set<String>
        get() = customOpeningsCache?.map { it.id }?.toSet() ?: emptySet()

    fun getOpenings(context: Context? = null): List<Opening> {
        if (customOpeningsCache != null) {
            return customOpeningsCache!!
        }
        if (context != null) {
            val loaded = loadCustomOpeningsFromDisk(context)
            customOpeningsCache = loaded.toMutableList()
            return customOpeningsCache!!
        }
        return emptyList()
    }

    fun getOpeningById(id: String, context: Context? = null): Opening? {
        return getOpenings(context).find { it.id == id }
    }

    fun saveOpening(
        context: Context,
        opening: Opening,
        fileContent: String? = null
    ): Opening {
        val openings = getOpenings(context).toMutableList()
        val index = openings.indexOfFirst { it.id == opening.id }
        if (index >= 0) {
            openings[index] = opening
        } else {
            openings.add(opening)
        }
        customOpeningsCache = openings
        saveCustomOpeningsToDisk(context, openings)

        if (fileContent != null) {
            OpeningFileManager.saveOpeningContent(context, opening, fileContent)
        } else {
            OpeningFileManager.getOrInitializeOpeningContent(context, opening)
        }

        return opening
    }

    fun deleteOpening(context: Context, openingId: String): Boolean {
        val openings = getOpenings(context).toMutableList()
        val removed = openings.removeAll { it.id == openingId }
        if (removed) {
            customOpeningsCache = openings
            saveCustomOpeningsToDisk(context, openings)
            OpeningFileManager.deleteOpeningFile(context, openingId)
        }
        return removed
    }

    fun setOpeningsForTesting(openings: List<Opening>) {
        customOpeningsCache = openings.toMutableList()
    }

    fun clearOpeningsForTesting() {
        customOpeningsCache = mutableListOf()
    }

    fun createSampleScotchGambit(context: Context): Opening {
        val sampleContent = """
1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Bc4
   Be7 5. c3 dxc3 6. Qd5 Nh6 7. Bxh6
      gxh6 8.Qf7#
      0-0 8. Bxg7 Kxg7 9. Nxc3
   Bb4 5. c3 dxc3 6. bxc3
      Bc5 7. Bxf7 Kxf7 8. Qd5+ Ke8 9. Qxc5
      Ba5 7. Qb3 Qe7 8. 0-0
        """.trimIndent()

        val parsed = OpeningVariationParser.parseFileContent(sampleContent)
        val baseUci = parsed.paths.firstOrNull { it.isBaseLine }?.movesUci
            ?: listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4", "e5d4", "f1c4")
        val baseSan = "1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Bc4"

        val opening = Opening(
            id = "scotch_gambit",
            name = "Scotch Gambit",
            eco = "C44",
            targetColor = PieceColor.WHITE,
            movesUci = baseUci,
            movesSan = baseSan,
            description = "High-tempo gambit sacrificing d4 pawn for rapid development and king attack."
        )

        return saveOpening(context, opening, sampleContent)
    }

    private fun getCustomOpeningsFile(context: Context): File {
        return File(context.filesDir, "custom_gambits.json")
    }

    private fun loadCustomOpeningsFromDisk(context: Context): List<Opening> {
        val file = getCustomOpeningsFile(context)
        if (!file.exists() || file.length() == 0L) {
            return emptyList()
        }
        return try {
            val jsonStr = file.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Opening>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uciArray = obj.getJSONArray("movesUci")
                val movesUci = mutableListOf<String>()
                for (j in 0 until uciArray.length()) {
                    movesUci.add(uciArray.getString(j))
                }
                list.add(
                    Opening(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        eco = obj.optString("eco", ""),
                        targetColor = PieceColor.valueOf(obj.getString("targetColor")),
                        movesUci = movesUci,
                        movesSan = obj.getString("movesSan"),
                        description = obj.optString("description", ""),
                        isUserFavorite = obj.optBoolean("isUserFavorite", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomOpeningsToDisk(context: Context, openings: List<Opening>) {
        try {
            val jsonArray = JSONArray()
            for (op in openings) {
                val obj = JSONObject()
                obj.put("id", op.id)
                obj.put("name", op.name)
                obj.put("eco", op.eco)
                obj.put("targetColor", op.targetColor.name)
                val uciArray = JSONArray()
                op.movesUci.forEach { uciArray.put(it) }
                obj.put("movesUci", uciArray)
                obj.put("movesSan", op.movesSan)
                obj.put("description", op.description)
                obj.put("isUserFavorite", op.isUserFavorite)
                jsonArray.put(obj)
            }
            val file = getCustomOpeningsFile(context)
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            // Write failure ignored
        }
    }

    fun findBookMove(
        currentHistoryUci: List<String>,
        engineColor: PieceColor,
        selectedIds: Set<String>,
        lockedOpening: Opening? = null,
        context: Context? = null
    ): Pair<String, Opening>? {
        val moveIndex = currentHistoryUci.size
        val isEngineTurnByMoveCount = if (engineColor == PieceColor.WHITE) {
            moveIndex % 2 == 0
        } else {
            moveIndex % 2 == 1
        }
        if (!isEngineTurnByMoveCount) return null

        // If user locked a specific opening for the engine to play
        if (lockedOpening != null) {
            if (lockedOpening.targetColor == engineColor) {
                val paths = if (context != null) {
                    OpeningFileManager.loadVariationPaths(context, lockedOpening)
                } else {
                    listOf(lockedOpening.movesUci)
                }

                val matchingPaths = paths.filter { path ->
                    path.size > moveIndex && path.take(moveIndex) == currentHistoryUci
                }

                if (matchingPaths.isNotEmpty()) {
                    val chosenPath = matchingPaths.random()
                    return Pair(chosenPath[moveIndex], lockedOpening)
                }
            }
            return null
        }

        val allAvailable = getOpenings(context)
        val colorFiltered = allAvailable.filter { it.targetColor == engineColor }
        val effectiveOpenings = if (selectedIds.isEmpty()) {
            colorFiltered
        } else {
            val selected = colorFiltered.filter { it.id in selectedIds }
            if (selected.isNotEmpty()) selected else colorFiltered
        }

        val candidateMoves = mutableListOf<Pair<String, Opening>>()
        for (opening in effectiveOpenings) {
            val paths = if (context != null) {
                OpeningFileManager.loadVariationPaths(context, opening)
            } else {
                listOf(opening.movesUci)
            }

            val matchingPaths = paths.filter { path ->
                path.size > moveIndex && path.take(moveIndex) == currentHistoryUci
            }

            for (path in matchingPaths) {
                candidateMoves.add(Pair(path[moveIndex], opening))
            }
        }

        if (candidateMoves.isEmpty()) return null
        return candidateMoves.random()
    }

    fun detectOpening(currentHistoryUci: List<String>, context: Context? = null): Opening? {
        if (currentHistoryUci.isEmpty()) return null

        return getOpenings(context)
            .filter { opening ->
                val len = minOf(opening.movesUci.size, currentHistoryUci.size)
                len > 0 && opening.movesUci.take(len) == currentHistoryUci.take(len)
            }
            .maxByOrNull { opening ->
                minOf(opening.movesUci.size, currentHistoryUci.size)
            }
    }
}
