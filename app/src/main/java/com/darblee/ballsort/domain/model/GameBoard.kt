package com.darblee.ballsort.domain.model

import android.util.Log
import com.darblee.ballsort.Global
import kotlin.random.Random

typealias gameSnapshot = MutableList<Int>

/**
 * Manages the game board state and rules for the Ball Sort game.
 *
 * This class encapsulates the board grid, floating ball state, column queries,
 * move validation, win detection, randomization, and snapshot operations.
 * It contains no Android framework dependencies beyond logging.
 */
class GameBoard {

    private var cells = Array(Global.MAX_COLUMNS) { Array(Global.MAX_SLOT_PER_COLUMN) { 0 } }

    /**
     * Column the floating ball was popped from. -1 when no ball is floating.
     */
    var floatingBallColumn = 0
        private set

    /**
     * Color ID of the floating ball. 0 when no ball is floating.
     */
    var floatingBallColorInt = 0
        private set

    /**
     * Whether a ball is currently floating (popped and waiting to be pushed).
     */
    fun hasFloatingBall(): Boolean = floatingBallColorInt != 0

    /**
     * Get the raw color integer at a specific board location.
     *
     * @param col Column number
     * @param slot Slot number (0 = bottom)
     * @return Color ID (0 = empty)
     */
    fun getBallColorInt(col: Int, slot: Int): Int = cells[col][slot]

    /**
     * Determine if the column is empty.
     */
    fun isColumnEmpty(col: Int): Boolean = cells[col][0] == 0

    /**
     * Determine if the column is full.
     */
    fun isColumnFull(col: Int): Boolean = cells[col][Global.MAX_SLOT_PER_COLUMN - 1] != 0

    /**
     * Check if the floating ball can move to this column.
     * Valid if there is room and the top ball matches the floating ball color (or column is empty).
     */
    fun validColumnToMoveTo(col: Int): Boolean {
        if (isColumnEmpty(col)) return true
        if (isColumnFull(col)) return false

        if (cells[col][2] != 0) {
            return cells[col][2] == floatingBallColorInt
        }

        if (cells[col][1] != 0) {
            return cells[col][1] == floatingBallColorInt
        }

        return cells[col][0] == floatingBallColorInt
    }

    /**
     * Pop the top ball from the specified column and set it as the floating ball.
     */
    fun popBall(col: Int) {
        floatingBallColumn = col
        floatingBallColorInt = popColorInt(col)
    }

    /**
     * Push the floating ball onto the specified column and clear floating ball state.
     */
    fun pushFloatingBall(col: Int) {
        pushColorInt(col, floatingBallColorInt)
        resetFloatingBall()
    }

    /**
     * Indicate whether the game is in a winning state.
     * Every column must be either empty or fully filled with the same color.
     */
    fun hasWon(): Boolean {
        for (curCol in 0..<Global.MAX_COLUMNS) {
            if (isColumnEmpty(curCol)) continue

            if (isColumnFull(curCol)) {
                if ((cells[curCol][0] == cells[curCol][1]) &&
                    (cells[curCol][0] == cells[curCol][2]) &&
                    (cells[curCol][0] == cells[curCol][3])) continue
            }
            return false
        }
        return true
    }

    /**
     * Randomize the board for a new game.
     * Starts from a winning state and makes 100 random moves to shuffle.
     */
    fun randomize() {
        resetToWinningState()
        repeat(100) {
            makeOneRandomMove()
        }
        packBoard()
    }

    /**
     * Create a snapshot of the current board state.
     */
    fun createSnapshot(): gameSnapshot {
        val snapshot: MutableList<Int> = mutableListOf()
        for (curCol in 0..<Global.MAX_COLUMNS) {
            for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                snapshot += cells[curCol][curSlot]
            }
        }
        return snapshot
    }

    /**
     * Restore the board from a snapshot and clear floating ball state.
     *
     * @param snapshot Specified game snapshot
     */
    fun restoreFromSnapshot(snapshot: gameSnapshot) {
        var index = 0
        for (curCol in 0..<Global.MAX_COLUMNS) {
            for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                cells[curCol][curSlot] = snapshot[index]
                index++
            }
        }
        resetFloatingBall()
    }

    /************************ Private implementation ************************/

    private fun hasOpenSlot(col: Int): Boolean = cells[col][Global.MAX_SLOT_PER_COLUMN - 1] == 0

    private fun resetFloatingBall() {
        floatingBallColorInt = 0
        floatingBallColumn = -1
    }

    /**
     * Reset the game board to the initial winning state.
     */
    private fun resetToWinningState() {
        for (curCol in 0..<(Global.MAX_COLUMNS - 2)) {
            for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) cells[curCol][curSlot] = curCol + 1
        }

        // Put in no color for the last 2 columns
        for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) cells[Global.MAX_COLUMNS - 2][curSlot] = 0
        for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) cells[Global.MAX_COLUMNS - 1][curSlot] = 0

        resetFloatingBall()
    }

    /**
     * Pop the top ball from specified column.
     *
     * @param col Column number to pop the ball from
     * @return Color ID of the popped ball (0 if column was empty)
     */
    private fun popColorInt(col: Int): Int {
        var curSlot = Global.MAX_SLOT_PER_COLUMN - 1

        while (curSlot >= 0) {
            if (cells[col][curSlot] != 0) {
                val ballColorInt = cells[col][curSlot]
                cells[col][curSlot] = 0
                return ballColorInt
            }
            curSlot--
        }

        return 0
    }

    /**
     * Push a ball onto the specified column.
     *
     * @param col Column number to push the ball into
     * @param ballColorInt Ball color
     * @param soft Soft push. Reject if pushing on top of a ball with the same color
     *
     * @return `true` if push was successful, `false` if no room or soft push rejected
     */
    private fun pushColorInt(col: Int, ballColorInt: Int, soft: Boolean = false): Boolean {
        if ((col >= Global.MAX_COLUMNS) || (col < 0)) {
            Log.i(Global.DEBUG_PREFIX, "Got unexpected column value of $col")
            return false
        }

        if (cells[col][0] == 0) {
            cells[col][0] = ballColorInt
            return true
        }

        if (cells[col][1] == 0) {
            if ((soft) && (cells[col][0] == ballColorInt)) return false
            cells[col][1] = ballColorInt
            return true
        }

        if (cells[col][2] == 0) {
            // (soft push only) If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (cells[col][1] == ballColorInt)) return false

            // (soft push only) If we are attempting to add ball on top of homogenous column, then reject it
            if ((soft) && (cells[col][0] == cells[col][1])) return false

            cells[col][2] = ballColorInt
            return true
        }

        if (cells[col][3] == 0) {
            // (soft push only) If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (cells[col][2] == ballColorInt)) return false

            // (soft push only) If we are attempting to add ball on top of shallow homogenous column, then reject it
            if ((soft) && (isShallowHomogenous(col))) return false

            cells[col][3] = ballColorInt
            return true
        }
        return false
    }

    /**
     * Determine if the column is shallow homogenous. A shallow homogenous column has
     * multiple balls with the same color adjacent on top.
     */
    private fun isShallowHomogenous(col: Int): Boolean {
        if (isColumnEmpty(col)) return false

        if (cells[col][1] == 0) return false

        if (cells[col][2] == 0) {
            return cells[col][0] == cells[col][1]
        }

        // At this point, we have 3 or more balls.
        if (cells[col][0] == cells[col][1]) return true

        if (cells[col][1] == cells[col][2]) return true

        if (cells[col][2] == cells[col][3]) return true

        return false
    }

    /**
     * Determines if a column is "deeply homogenous".
     *
     * A column is considered deeply homogenous if it contains at least three balls of the same color,
     * regardless of their position within the column.
     */
    private fun isDeepHomogenous(col: Int): Boolean {
        if (isColumnEmpty(col)) return false

        // If this column only has 2 balls, then this does not qualify
        if (cells[col][2] == 0) return false

        val color1 = cells[col][0]
        val color2 = cells[col][1]
        val color3 = cells[col][2]
        val color4 = cells[col][3]

        if ((color1 == color2) && (color1 == color3)) return true

        if ((color1 == color2) && (color1 == color4)) return true

        if ((color1 == color3) && (color1 == color4)) return true

        if ((color2 == color3) && (color2 == color4)) return true

        return false
    }

    private fun getNonEmptyColumnCount(): Int {
        var count = Global.MAX_COLUMNS

        for (curCol in 0..<Global.MAX_COLUMNS) {
            if (isColumnEmpty(curCol)) count--
        }
        return count
    }

    private fun getOpenColumnCount(): Int {
        var count = 0

        for (curCol in 0..<Global.MAX_COLUMNS) {
            if (hasOpenSlot(curCol)) count++
        }

        return count
    }

    private fun getShallowHomogenousColumnCount(): Int {
        var count = 0

        for (curCol in 0..<Global.MAX_COLUMNS) {
            if (isShallowHomogenous(curCol)) count++
        }

        return count
    }

    private fun getDeepHomogenousColumnCount(): Int {
        var count = 0

        for (curCol in 0..<Global.MAX_COLUMNS) {
            if (isDeepHomogenous(curCol)) count++
        }

        return count
    }

    /**
     * Consolidate all balls into the first available columns.
     */
    private fun packBoard() {
        var ballColorInt: Int

        var col = Global.MAX_COLUMNS - 1
        while (!isColumnEmpty(col)) {
            ballColorInt = popColorInt(col)
            pushToFirstAvailableSlot(ballColorInt)
        }

        col = Global.MAX_COLUMNS - 2
        while (!isColumnEmpty(col)) {
            ballColorInt = popColorInt(col)
            pushToFirstAvailableSlot(ballColorInt)
        }
    }

    private fun pushToFirstAvailableSlot(ballColorInt: Int) {
        var curCol = 0
        while (curCol < Global.MAX_COLUMNS) {
            if (hasOpenSlot(curCol)) {
                pushColorInt(curCol, ballColorInt)
                return
            }
            curCol++
        }
    }

    private fun makeOneRandomMove() {
        val (curColForPop, ballColorInt) = randomPopBall(homogenousOnly = true)
        if (ballColorInt != 0) {
            randomPush(ballColorInt, curColForPop)
            return
        }

        val (curColForPop1, ballColorInt1) = randomPopBall(homogenousOnly = false)
        randomPush(ballColorInt1, curColForPop1)
    }

    /**
     * Pop a ball from one of the columns randomly.
     *
     * @param homogenousOnly Try to pop from homogenous column
     * @return Pair of (column popped from, color of ball). Color 0 means pop did not happen.
     */
    private fun randomPopBall(homogenousOnly: Boolean = false): Pair<Int, Int> {
        var curColForPop = 0
        var ballColorInt = 0
        val deepHomogenousCount = getDeepHomogenousColumnCount()
        val shallowHomogenousCount = getShallowHomogenousColumnCount()

        if (homogenousOnly) {
            if ((deepHomogenousCount == 0) && (shallowHomogenousCount == 0))
                return Pair(0, 0)
        }

        if (deepHomogenousCount > 0) {
            var randomIndexForPop =
                if (homogenousOnly) {
                    Random.nextInt(1, (deepHomogenousCount + 1))
                } else {
                    Random.nextInt(1, (getNonEmptyColumnCount() + 1))
                }

            while (curColForPop < Global.MAX_COLUMNS) {
                if (!isColumnEmpty(curColForPop)) {
                    if (homogenousOnly) {
                        if (isDeepHomogenous(curColForPop)) randomIndexForPop--
                    } else {
                        randomIndexForPop--
                    }
                    if (randomIndexForPop == 0) {
                        ballColorInt = popColorInt(curColForPop)
                        return Pair(curColForPop, ballColorInt)
                    }
                }
                curColForPop++
            }
        }

        if ((homogenousOnly) && (shallowHomogenousCount == 0)) return Pair(0, 0)

        var randomIndexForPop =
            if (homogenousOnly) {
                Random.nextInt(1, (shallowHomogenousCount + 1))
            } else {
                Random.nextInt(1, (getNonEmptyColumnCount() + 1))
            }

        while (curColForPop < Global.MAX_COLUMNS) {
            if (!isColumnEmpty(curColForPop)) {
                if (homogenousOnly) {
                    if (isShallowHomogenous(curColForPop)) randomIndexForPop--
                } else {
                    randomIndexForPop--
                }
                if (randomIndexForPop == 0) {
                    ballColorInt = popColorInt(curColForPop)
                    return Pair(curColForPop, ballColorInt)
                }
            }
            curColForPop++
        }

        return Pair(curColForPop, ballColorInt)
    }

    /**
     * Push a ball to a random destination, preferring columns where the top ball
     * has a different color.
     *
     * @param ballColorInt Ball color (integer representation)
     * @param avoidColumnNumber Avoid pushing to this column (the one we just popped from)
     */
    private fun randomPush(ballColorInt: Int, avoidColumnNumber: Int) {
        var randomIndexForPush = Random.nextInt(1, getOpenColumnCount())
        var curColForPush = 0
        softPushLoop1@ while (curColForPush < Global.MAX_COLUMNS) {
            if ((curColForPush != avoidColumnNumber) && (hasOpenSlot(curColForPush))) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (pushColorInt(curColForPush, ballColorInt, soft = true)) {
                        return
                    } else {
                        break@softPushLoop1
                    }
                }
            }
            curColForPush++
        }

        // Soft push failed. Try another soft push
        randomIndexForPush = Random.nextInt(1, (getOpenColumnCount()))
        curColForPush = 0
        softPushLoop2@ while (curColForPush < Global.MAX_COLUMNS) {
            if ((curColForPush != avoidColumnNumber) && (hasOpenSlot(curColForPush))) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (pushColorInt(curColForPush, ballColorInt, soft = true)) {
                        return
                    } else {
                        break@softPushLoop2
                    }
                }
            }
            curColForPush++
        }

        // Soft push failed. Try again, but this time do a hard push
        randomIndexForPush = Random.nextInt(1, (getOpenColumnCount()))
        curColForPush = 0
        while (curColForPush < Global.MAX_COLUMNS) {
            if ((curColForPush != avoidColumnNumber) && (hasOpenSlot(curColForPush))) {
                randomIndexForPush--
                if (randomIndexForPush == 0) {
                    pushColorInt(curColForPush, ballColorInt, soft = false)
                    return
                }
            }
            curColForPush++
        }
    }

    /**
     * Print the game board for debugging purposes.
     */
    fun printBoard() {
        Log.i(Global.DEBUG_PREFIX, "======= Game Board =========")
        for (curCol in 0..<Global.MAX_COLUMNS) {
            for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                val ballColor = cells[curCol][curSlot]
                Log.i(Global.DEBUG_PREFIX, "$curCol, $curSlot = $ballColor")
            }
        }
    }
}
