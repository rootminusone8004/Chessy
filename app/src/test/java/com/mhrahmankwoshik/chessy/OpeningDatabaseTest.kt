package com.mhrahmankwoshik.chessy

import com.mhrahmankwoshik.chessy.data.OpeningDatabase
import com.mhrahmankwoshik.chessy.data.model.Opening
import com.mhrahmankwoshik.chessy.data.model.PieceColor
import com.mhrahmankwoshik.chessy.game.ChessGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpeningDatabaseTest {

    private val userScotchGambit = Opening(
        id = "scotch_gambit",
        name = "Scotch Gambit",
        eco = "C44",
        targetColor = PieceColor.WHITE,
        movesUci = listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4", "e5d4", "f1c4"),
        movesSan = "1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Bc4",
        description = "High-tempo attacking gambit"
    )

    private val userRousseauGambit = Opening(
        id = "rousseau_gambit",
        name = "Rousseau Gambit",
        eco = "C50",
        targetColor = PieceColor.BLACK,
        movesUci = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f7f5"),
        movesSan = "1. e4 e5 2. Nf3 Nc6 3. Bc4 f5",
        description = "Counter-gambit by Black"
    )

    @Before
    fun setUp() {
        OpeningDatabase.setOpeningsForTesting(listOf(userScotchGambit, userRousseauGambit))
    }

    @Test
    fun testUserCustomOpeningsRegistration() {
        val scotch = OpeningDatabase.getOpeningById("scotch_gambit")
        assertNotNull(scotch)
        assertEquals("Scotch Gambit", scotch!!.name)
        assertEquals(PieceColor.WHITE, scotch.targetColor)

        val rousseau = OpeningDatabase.getOpeningById("rousseau_gambit")
        assertNotNull(rousseau)
        assertEquals("Rousseau Gambit", rousseau!!.name)
        assertEquals(PieceColor.BLACK, rousseau.targetColor)
    }

    @Test
    fun testEngineAsWhitePlaysWhiteOpeningsOnly() {
        val selectedIds = setOf("scotch_gambit", "rousseau_gambit")

        // Engine is White. Engine MUST play Scotch Gambit (targetColor = WHITE), never Rousseau
        val move0 = OpeningDatabase.findBookMove(emptyList(), PieceColor.WHITE, selectedIds)
        assertNotNull(move0)
        assertEquals("e2e4", move0!!.first)
        assertEquals("scotch_gambit", move0.second.id)
    }

    @Test
    fun testEngineAsBlackPlaysBlackOpeningsOnly() {
        val selectedIds = setOf("scotch_gambit", "rousseau_gambit")

        // Player is White, plays 1. e4
        val history1 = listOf("e2e4")
        // Engine is Black. Engine MUST play Rousseau Gambit (targetColor = BLACK), never Scotch Gambit
        val move1 = OpeningDatabase.findBookMove(history1, PieceColor.BLACK, selectedIds)
        assertNotNull(move1)
        assertEquals("e7e5", move1!!.first)
        assertEquals("rousseau_gambit", move1.second.id)
    }

    @Test
    fun testFindBookMoveRousseauGambitAsBlackEngine() {
        val selectedIds = setOf("rousseau_gambit")

        // Player (White) plays 1. e4
        val history1 = listOf("e2e4")
        val move1 = OpeningDatabase.findBookMove(history1, PieceColor.BLACK, selectedIds)
        assertNotNull(move1)
        assertEquals("e7e5", move1!!.first)

        // Player plays 2. Nf3 -> history [e4, e5, Nf3]
        val history3 = listOf("e2e4", "e7e5", "g1f3")
        val move3 = OpeningDatabase.findBookMove(history3, PieceColor.BLACK, selectedIds)
        assertNotNull(move3)
        assertEquals("b8c6", move3!!.first)

        // Player plays 3. Bc4 -> history [e4, e5, Nf3, Nc6, Bc4]
        val history5 = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")
        val move5 = OpeningDatabase.findBookMove(history5, PieceColor.BLACK, selectedIds)
        assertNotNull(move5)
        assertEquals("f7f5", move5!!.first) // Rousseau Gambit move played by Stockfish!
        assertEquals("rousseau_gambit", move5.second.id)
    }

    @Test
    fun testEmptyGambitListReturnsNullForFreelyEvaluatedPlay() {
        OpeningDatabase.clearOpeningsForTesting()
        val move = OpeningDatabase.findBookMove(emptyList(), PieceColor.WHITE, emptySet())
        assertNull(move)
    }

    @Test
    fun testLockedOpeningOverridesRepertoire() {
        val userKingsGambit = Opening(
            id = "kings_gambit",
            name = "King's Gambit",
            targetColor = PieceColor.WHITE,
            movesUci = listOf("e2e4", "e7e5", "f2f4"),
            movesSan = "1. e4 e5 2. f4"
        )

        // Even though scotch_gambit is in selectedIds, lockedOpening forces King's Gambit
        val move0 = OpeningDatabase.findBookMove(
            currentHistoryUci = emptyList(),
            engineColor = PieceColor.WHITE,
            selectedIds = setOf("scotch_gambit"),
            lockedOpening = userKingsGambit
        )
        assertNotNull(move0)
        assertEquals("e2e4", move0!!.first)
        assertEquals("kings_gambit", move0.second.id)
    }

    @Test
    fun testDetectOpening() {
        val scotchHistory = listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4")
        val detected = OpeningDatabase.detectOpening(scotchHistory)
        assertNotNull(detected)
        assertEquals("scotch_gambit", detected!!.id)

        val unknownHistory = listOf("h2h4", "h7h5")
        val detectedUnknown = OpeningDatabase.detectOpening(unknownHistory)
        assertNull(detectedUnknown)
    }

    @Test
    fun testPlayerFacesCustomGambit() {
        val game = ChessGame()

        // Engine plays Scotch Gambit (White). Player is BLACK.
        game.resetGame(newPlayerColor = PieceColor.BLACK, initialMovesUci = userScotchGambit.movesUci)

        assertEquals(7, game.moveHistory.size)
        assertEquals(PieceColor.BLACK, game.playerColor)
        // 7 half-moves played -> Black to move -> Player's turn to face the gambit!
        assertTrue(game.isPlayerTurn())
        assertFalse(game.isEngineTurn())
    }
}
