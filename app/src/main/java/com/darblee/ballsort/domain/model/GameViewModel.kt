package com.darblee.ballsort.domain.model

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.darblee.ballsort.Global
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.utilities.PairArgsSingletonHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.random.Random

/**
 * **View Model for the  Game**
 */
class GameViewModel(gGameFile: File, gHistFile: File) : ViewModel() {
    companion object : PairArgsSingletonHolder<GameViewModel, File, File>(::GameViewModel)

    /********************************* GAME MANAGEMENT ********************************************/

    /**
     * Contain the UI state of the solver game. This is used by the game screens to display
     * proper UI elements. Various composable will automatically update when that state changes
     *
     * For reference, see [Compose Snapshot System](https://blog.zachklipp.com/introduction-to-the-compose-snapshot-system/)
     */
    private val _uiGameState = MutableStateFlow(GameUIState())

    /**
     * Holds the [_uiGameState] as a state flow.
     */
    var gameUIState: StateFlow<GameUIState> = _uiGameState.asStateFlow()
        private set

    /**
     *  Game board. The first array index is the column number starting from index -
     *  The second array index is the slot number. Slot 0 is the bottom of the column.
     */
    var gameBoard = Array(Global.MAX_COLUMNS) { Array<Color>(Global.MAX_SLOT_PER_COLUMN) { Color.Unspecified } }

    /**
     * Determine if the column is empty or not
     *
     * @return
     * - `True` column is empty
     * - `false` column is NOT empty
     */
    private fun Int.isEmpty() = gameBoard[this][0] == Color.Unspecified

    /**
     *  Determine if the column has available slot to push the ball into
     *
     *  @return
     *  - `True` This column has available slot
     *  - `false` This column does not have any available slot
     */
    private fun Int.hasOpenSlot() = gameBoard[this][Global.MAX_SLOT_PER_COLUMN - 1] == Color.Unspecified

    /**
     * Determine if the column is homogenous or not. A homogenous column is a column that has
     * multiple balls with the same color on top
     *
     * @param col Specified column number
     * @return
     * - `true` There are 2 or more balls on column and the top 2 balls has the same color
     * - `false` There is less than 2 balls or the top 2 balls has mixed color
     */
    private fun hasSameColorBallsOnTop(col: Int): Boolean
    {
        if (col.isEmpty()) return false

        if (gameBoard[col][1] == Color.Unspecified) return false

        if (gameBoard[col][2] == Color.Unspecified) {
            return if (gameBoard[col][0] == gameBoard[col][1]) true else false
        }

        // At this point, we have 2 or more balls.
        if (gameBoard[col][3] == Color.Unspecified) {
            // The top 2 balls is slot 1 & 2
            return (gameBoard[col][1] == gameBoard[col][2])
        } else {
            // The top 2 balls is slot 2 & 3
            return (gameBoard[col][2] == gameBoard[col][3])
        }
    }

    /**
     * Get the ball color at s specific location in the game board
     *
     * @param col Specified Column number location
     * @param slot Specified slot location
     * @return Ball color
     */
    fun getBallColor(col: Int, slot: Int): Color
    {
        return (gameBoard[col][slot])
    }

    /**
     * Initialize the GameViewModel.
     */
    init {
        resetToWinningGameBoard()
        setMode(GameUIState.GameMode.Initialization)
    }

    /**
     * Reset the game board to the initial winning state
     */
    private fun resetToWinningGameBoard()
    {
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[0][curSlot] = Color.Red
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[1][curSlot] = Color.Blue
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[2][curSlot] = Color.Yellow
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[3][curSlot] = Color.Green
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[4][curSlot] = Color.Magenta
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[5][curSlot] = Color.Cyan
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[6][curSlot] = Color.LightGray
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[7][curSlot] = Color(0xFF7D5260)
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[8][curSlot] = Color.Unspecified
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[9][curSlot] = Color.Unspecified
    }

    /**
     * Determine whether it can safely exit the GameViewModel
     *
     * @return
     * - `true` Clean-up is done. It is safe to exit the view model
     * - `false` Unable to clean-up or in a middle of doing something. Do not exit the view model
     */
    fun canExitGameScreen(): Boolean {
        when (gameUIState.value.mode) {
            GameUIState.GameMode.Initialization -> return false
            GameUIState.GameMode.WonGame -> return false
            GameUIState.GameMode.UpdatedGameBoard -> return true
            GameUIState.GameMode.NoWinnableMove -> return true
            GameUIState.GameMode.MoveBall -> return true
            GameUIState.GameMode.ShowHint -> { return true }
        }
    }

    /**
     * Randomize game board
     */
    fun randomizeGameBoard()
    {
        resetToWinningGameBoard()
        repeat(30) {
            makeOneRandomMove()
        }
        packBoard()
        setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**
     * Consolidate all balls to 7 columns
     */
    private fun packBoard()
    {
        var ballColor: Color

        var col = Global.MAX_COLUMNS - 1
        while (!col.isEmpty()) {
            ballColor = pop(col)
            pushToFirstAvailableSlot(ballColor)
        }

        col = Global.MAX_COLUMNS -2
        while (!col.isEmpty()) {
            ballColor = pop(col)
            pushToFirstAvailableSlot(ballColor)
        }
    }

    /**
     * Push the ball to the first available slot
     */
    private fun pushToFirstAvailableSlot(ballColor: Color)
    {
        var curCol = 0
        while (curCol < Global.MAX_COLUMNS) {
            if (curCol.hasOpenSlot()) {
                push(curCol, ballColor)
                return
            }
            curCol++
        }

    }

    /**
     * Move one ball randomly
     */
    private fun makeOneRandomMove() {
        val (curColForPop, ballColor) = randomPopBall(homogenousOnly = true)
        if (ballColor != Color.Unspecified) {
            randomPush(ballColor, curColForPop)
            return
        }

        val (curColForPop1, ballColor1) = randomPopBall(homogenousOnly = false)

        randomPush(ballColor1, curColForPop1)

        return
    }

    /**
     * Pop the ball from one of the columns
     *
     * @param homogenousOnly Try to avoid popping from homogenous column
     *
     * @return
     * - Column that was popped from
     * - Color of the ball. If this is [Color.Unspecified], then popping did not happen
     */
    private fun randomPopBall(homogenousOnly: Boolean = false): Pair<Int, Color>
    {
        var curColForPop = 0
        var ballColor = Color.Unspecified
        val homogenousCount = getHomogenousColumnCount()

        if (homogenousOnly) {
            if (homogenousCount == 0) return Pair(0, Color.Unspecified)
        }

        var randomIndexForPop =
            if (homogenousOnly) {
                Random.nextInt(1, (homogenousCount + 1))
            } else {
                Random.nextInt(1, (getNonEmptyColumnCount() + 1))
            }

        while (curColForPop < Global.MAX_COLUMNS) {
            if (!curColForPop.isEmpty()) {
                if (homogenousOnly)  {
                    if (hasSameColorBallsOnTop(curColForPop)) randomIndexForPop--
                } else {
                    randomIndexForPop--
                }
                if (randomIndexForPop == 0) {
                    ballColor = pop(curColForPop)
                    return  Pair(curColForPop, ballColor)
                }
            }
            curColForPop++
        }

        return  Pair(curColForPop, ballColor)
    }

    /**
     * Push the ball in a random destination, with a preference to put
     * the ball on another ball that has different color
     *
     * @param ballColor Ball color
     * @param avoidColumnNumber Avoid pushing to this column. We do not want to push the ball on
     * same column where we just pop it from
     */
    private fun randomPush(ballColor: Color, avoidColumnNumber: Int)
    {
        var randomIndexForPush = Random.nextInt(1, getOpenColumnCount())
        var curColForPush = 0
        softPushLoop1@ while (curColForPush < Global.MAX_COLUMNS) {
            if  ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (push(curColForPush, ballColor, soft = true)) {
                        // Completed the process of moving the ball. We can exit this function
                        return
                    } else {
                        Log.i(Global.DEBUG_PREFIX, "Soft push fail #1 - try again")
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
            if  ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (push(curColForPush, ballColor, soft = true)) {
                        // Completed the process of moving the ball. We can exit this function
                        return
                    } else {
                        Log.i(Global.DEBUG_PREFIX, "Soft push fail #2 - try again")
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
            if ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {
                randomIndexForPush--
                if (randomIndexForPush == 0) {
                    push(curColForPush, ballColor, soft = false)

                    // Completed the process of moving the ball. We can exit this function
                    return
                }
            }
            curColForPush++
        }
    }

    /**
     * Get the number of columns that is not empty
     *
     * @return number of columns that is not empty
     */
    private fun getNonEmptyColumnCount(): Int
    {
        var count = Global.MAX_COLUMNS

        for (curCol in 0 ..< Global.MAX_COLUMNS ) {
            if (curCol.isEmpty()) count--
        }
        return (count)
    }

    /**
     * Get the number of columns tht has open slot
     *
     * @return
     * number of columns tht has open slot
     */
    private fun getOpenColumnCount():Int
    {
        var count = 0

        for (curCol in 0 ..< Global.MAX_COLUMNS ) {
            if (curCol.hasOpenSlot()) count++
        }

        return (count)
    }

    /**
     * Determine the number of homogenous columns
     *
     * @return
     * -  Number of homogenous column
     */
    private fun getHomogenousColumnCount(): Int
    {
        var count = 0

        for (curCol in 0 ..< Global.MAX_COLUMNS ) {
            if (hasSameColorBallsOnTop(curCol)) count++
        }

        return count
    }

    /**
     * Push the ball onto the specified column
     *
     * @param col Column number to push the ball into
     * @param ballColor Ball color
     * @param soft Soft push. Reject the push if we are pushing on top of ball that
     * has the same color
     *
     * @return
     * - `true`  Push was successful
     * - `false`  Push failed. Either there is no room, or this was a soft push where we attempt
     * to push the ball on top of another ball with the same color
     */
    private fun push(col: Int, ballColor: Color, soft: Boolean = false): Boolean
    {
        if ((col >= Global.MAX_COLUMNS) || (col < 0)) {
            Log.i(Global.DEBUG_PREFIX, "Got unexpected column value of $col")
            return false
        }

        if (gameBoard[col][0] == Color.Unspecified) {
            gameBoard[col][0] = ballColor
            return (true)
        }

        if (gameBoard[col][1] == Color.Unspecified) {
            if ((soft) && (gameBoard[col][0] == ballColor)) return false
            gameBoard[col][1] = ballColor
            return (true)

        }

        if (gameBoard[col][2] == Color.Unspecified) {
            // (soft push only)  If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (gameBoard[col][1] == ballColor)) return false

            // (soft push only) If we are attempting to add ball on top of homogenous column, then reject it
            if ((soft) && (gameBoard[col][0] == gameBoard[col][1])) return false

            gameBoard[col][2] = ballColor
            return (true)

        }

        if (gameBoard[col][3] == Color.Unspecified) {
            // (soft push only)  If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (gameBoard[col][2] == ballColor)) return false

            // (soft push only) If we are attempting to add ball on top of homogenous column, then reject it
            if ((soft) && (gameBoard[col][1] == gameBoard[col][2])) return false

            gameBoard[col][3] = ballColor
            return (true)
        }

        return false
    }

    /**
     * Pop the ball from specified column
     *
     * @param col Column number to pop the ball from
     *
     * @return
     * Color of the ball that got pop from. If there is no ball,
     * then it will return unspecified color
     */
    private fun pop(col:Int) : Color
    {
        var ballColor = Color.Unspecified
        var curSlot = Global.MAX_SLOT_PER_COLUMN - 1

        while (curSlot >= 0) {
            if (gameBoard[col][curSlot] != Color.Unspecified ) {
                ballColor = gameBoard[col][curSlot]
                gameBoard[col][curSlot] = Color.Unspecified
                return (ballColor)
            }
            curSlot--
        }

        return (Color.Unspecified)
    }

    /**
     * Print the game board. This is used primarily for debugging purposes
     */
    private fun printGameBoard()
    {
        Log.i(Global.DEBUG_PREFIX, "======= Game Board =========")
        for (curCol in 0..< Global.MAX_COLUMNS) {
            for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) {
                val ballColor = gameBoard[curCol][curSlot]
                Log.i(Global.DEBUG_PREFIX, "$curCol, $curSlot = $ballColor")
            }

        }

    }

    /*************** Set mode routines ***********************/

    /**
     * Update [_uiGameState] to a specified mode
     *
     * For more details, see [gameUIState]
     */
    private fun setMode(mode: GameUIState.GameMode)
    {
        _uiGameState.update { curState ->
            curState.copy(_mode = mode)
        }
    }
}

