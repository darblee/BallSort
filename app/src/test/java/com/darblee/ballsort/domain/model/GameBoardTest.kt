package com.darblee.ballsort.domain.model

import com.darblee.ballsort.Global
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local unit tests for [GameBoard].
 *
 * [GameBoard] is pure game logic with no Android dependencies on the paths
 * exercised here, so these run on the host JVM. Board states are set up through
 * the public [GameBoard.restoreFromSnapshot], which also verifies the snapshot
 * layout used throughout the suite.
 */
class GameBoardTest {

    /**
     * Build a [GameBoard] from per-column color lists given bottom-up.
     *
     * Each column list is padded to [Global.MAX_SLOT_PER_COLUMN] with empties (0),
     * and any columns beyond those supplied are left empty. The resulting snapshot
     * is column-major, matching [GameBoard.createSnapshot].
     */
    private fun boardOf(vararg columns: List<Int>): GameBoard {
        require(columns.size <= Global.MAX_COLUMNS)
        val snapshot = mutableListOf<Int>()
        for (col in 0..<Global.MAX_COLUMNS) {
            val column = columns.getOrElse(col) { emptyList() }
            require(column.size <= Global.MAX_SLOT_PER_COLUMN)
            for (slot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                snapshot += column.getOrElse(slot) { 0 }
            }
        }
        return GameBoard().apply { restoreFromSnapshot(snapshot) }
    }

    private fun fullColumn(color: Int) = List(Global.MAX_SLOT_PER_COLUMN) { color }

    /** Matches the prettyPrint format GameViewModel.saveGameHistoryToFile uses. */
    private val json = Json { prettyPrint = true }

    // ---------- hasWon ----------

    @Test
    fun hasWon_emptyBoard_isTrue() {
        // Every column empty counts as won (nothing is out of place).
        assertTrue(boardOf().hasWon())
    }

    @Test
    fun hasWon_allColumnsFullAndSingleColor_isTrue() {
        val board = boardOf(fullColumn(1), fullColumn(2), fullColumn(3))
        assertTrue(board.hasWon())
    }

    @Test
    fun hasWon_fullColumnWithMixedColors_isFalse() {
        val board = boardOf(listOf(1, 1, 2, 1))
        assertFalse(board.hasWon())
    }

    @Test
    fun hasWon_partiallyFilledColumn_isFalse() {
        // Correct color but column not full.
        val board = boardOf(listOf(1, 1))
        assertFalse(board.hasWon())
    }

    // ---------- validColumnToMoveTo ----------

    @Test
    fun validColumnToMoveTo_emptyColumn_isTrueRegardlessOfColor() {
        // Column 0 holds a single color-3 ball to pop; column 11 is empty.
        val board = boardOf(listOf(3))
        board.popBall(0)
        assertTrue(board.validColumnToMoveTo(Global.MAX_COLUMNS - 1))
    }

    @Test
    fun validColumnToMoveTo_matchingTopColor_isTrue() {
        // Source column 0: color 3 on top. Target column 1: top color also 3, has room.
        val board = boardOf(listOf(3), listOf(3))
        board.popBall(0)
        assertTrue(board.validColumnToMoveTo(1))
    }

    @Test
    fun validColumnToMoveTo_differentTopColor_isFalse() {
        // Source color 3 floating; target top color 5.
        val board = boardOf(listOf(3), listOf(5))
        board.popBall(0)
        assertFalse(board.validColumnToMoveTo(1))
    }

    @Test
    fun validColumnToMoveTo_fullColumn_isFalse() {
        // Target is full of the matching color: no room, so still invalid.
        val board = boardOf(listOf(3), fullColumn(3))
        board.popBall(0)
        assertFalse(board.validColumnToMoveTo(1))
    }

    @Test
    fun validColumnToMoveTo_noFloatingBall_emptyColumn_isTrue() {
        // With no ball popped, an empty column is still reported as a valid target:
        // the emptiness check short-circuits before the color comparison.
        val board = boardOf(listOf(3))
        assertFalse(board.hasFloatingBall())
        assertTrue(board.validColumnToMoveTo(Global.MAX_COLUMNS - 1))
    }

    @Test
    fun validColumnToMoveTo_noFloatingBall_nonEmptyColumn_isFalse() {
        // Without a floating ball the floating color is 0, which matches no real
        // ball, so a non-empty (non-full) column is not a valid target.
        val board = boardOf(listOf(3))
        assertFalse(board.hasFloatingBall())
        assertFalse(board.validColumnToMoveTo(0))
    }

    @Test
    fun validColumnToMoveTo_columnIndexTooHigh_throws() {
        val board = boardOf(listOf(3))
        board.popBall(0)
        assertThrows(IndexOutOfBoundsException::class.java) {
            board.validColumnToMoveTo(Global.MAX_COLUMNS)
        }
    }

    @Test
    fun validColumnToMoveTo_negativeColumnIndex_throws() {
        val board = boardOf(listOf(3))
        board.popBall(0)
        assertThrows(IndexOutOfBoundsException::class.java) {
            board.validColumnToMoveTo(-1)
        }
    }

    // ---------- snapshot round-tripping ----------

    @Test
    fun snapshot_roundTrip_preservesBoard() {
        val original = boardOf(
            fullColumn(1),
            listOf(2, 2),
            listOf(7, 3, 3),
        )
        val snapshot = original.createSnapshot()

        val restored = GameBoard().apply { restoreFromSnapshot(snapshot) }

        assertEquals(snapshot, restored.createSnapshot())
    }

    @Test
    fun snapshot_hasExpectedSize() {
        val size = boardOf().createSnapshot().size
        assertEquals(Global.MAX_COLUMNS * Global.MAX_SLOT_PER_COLUMN, size)
    }

    @Test
    fun restoreFromSnapshot_clearsFloatingBall() {
        val board = boardOf(listOf(3))
        board.popBall(0)
        assertTrue(board.hasFloatingBall())

        board.restoreFromSnapshot(board.createSnapshot())

        assertFalse(board.hasFloatingBall())
    }

    // ---------- JSON persistence round-tripping ----------
    //
    // These exercise the same serialization format GameViewModel persists to
    // disk (a List<List<Int>> move history via kotlinx.serialization), without
    // pulling in the ViewModel's Android/coroutine dependencies. The encode uses
    // prettyPrint to match saveGameHistoryToFile; the decode target type matches
    // loadHistoryFromFile's Json.decodeFromString<List<List<Int>>>.

    @Test
    fun jsonRoundTrip_singleSnapshot_preservesBoard() {
        val original = boardOf(fullColumn(4), listOf(2, 5), listOf(9, 9, 1))
        val snapshot = original.createSnapshot()

        val history: List<List<Int>> = listOf(snapshot)
        val encoded = json.encodeToString(history)
        val decoded = json.decodeFromString<List<List<Int>>>(encoded)

        assertEquals(history, decoded)

        val restored = GameBoard().apply { restoreFromSnapshot(decoded.last().toMutableList()) }
        assertEquals(snapshot, restored.createSnapshot())
    }

    @Test
    fun jsonRoundTrip_moveHistory_preservesAllSnapshots() {
        // A three-move history, as would accumulate through play.
        val history: List<List<Int>> = listOf(
            boardOf(fullColumn(1)).createSnapshot(),
            boardOf(fullColumn(1), listOf(2)).createSnapshot(),
            boardOf(fullColumn(1), listOf(2, 3)).createSnapshot(),
        )

        val encoded = json.encodeToString(history)
        val decoded = json.decodeFromString<List<List<Int>>>(encoded)

        assertEquals(history, decoded)

        // On load, GameViewModel restores the board from the last snapshot.
        val restored = GameBoard().apply { restoreFromSnapshot(decoded.last().toMutableList()) }
        assertEquals(history.last(), restored.createSnapshot())
    }

    @Test
    fun jsonRoundTrip_emptyHistory_decodesToEmptyList() {
        // loadHistoryFromFile treats an empty decoded list as "no usable history";
        // confirm the format itself round-trips an empty move history cleanly.
        val history: List<List<Int>> = emptyList()
        val encoded = json.encodeToString(history)
        val decoded = json.decodeFromString<List<List<Int>>>(encoded)

        assertTrue(decoded.isEmpty())
    }
}
