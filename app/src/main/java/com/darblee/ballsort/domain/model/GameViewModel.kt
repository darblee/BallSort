package com.darblee.ballsort.domain.model

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.ballsort.Global
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
import com.darblee.ballsort.utilities.PairArgsSingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
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
    private var gameBoard = Array(Global.MAX_COLUMNS) { Array(Global.MAX_SLOT_PER_COLUMN) { 0 } }

    /**
     * Determine if the column is empty or not
     *
     * @return
     * - `True` column is empty
     * - `false` column is NOT empty
     */
    private fun Int.isEmpty() = gameBoard[this][0] == 0


    /**
     * Determine if the column is full or not
     *
     * @return
     * - `True` column is empty
     * - `false` column is NOT empty
     */
    private fun Int.isFull() = gameBoard[this][Global.MAX_SLOT_PER_COLUMN - 1] != 0


    /**
     * Determine if the column is full or not
     *
     * @return
     * - `True` column is empty
     * - `false` column is NOT empty
     */
    fun columnIsFull(col: Int): Boolean
    {
        return (col.isFull())
    }

    /**
     * Game has floating ball and is ready to get push to a new column
     */
    fun hasFloatingBall(): Boolean
    {
        return (floatingBallColorInt != 0)
    }

    var floatingBallColumn = 0
        private set

    var floatingBallColorInt = 0
        private set

    /**
     *  Determine if the column has available slot to push the ball into
     *
     *  @return
     *  - `True` This column has available slot
     *  - `false` This column does not have any available slot
     */
    private fun Int.hasOpenSlot() = gameBoard[this][Global.MAX_SLOT_PER_COLUMN - 1] == 0


    /**
     * Determine if the column is shallow homogenous or not. A shallow homogenous column is a column that has
     * multiple balls with the same color on top
     *
     * @param col Specified column number
     * @return
     * - `true` There are 2 or more balls on column and the top 2 balls has the same color
     * - `false` There is less than 2 balls or the top 2 balls has mixed color
     */
    private fun isShallowHomogenous(col: Int): Boolean
    {
        if (col.isEmpty()) return false

        if (gameBoard[col][1] == 0) return false

        if (gameBoard[col][2] == 0) {
            return gameBoard[col][0] == gameBoard[col][1]
        }

        // At this point, we have 3 or more balls.
        if (gameBoard[col][0] == gameBoard[col][1]) return true

        if (gameBoard[col][1] == gameBoard[col][2]) return true

        if (gameBoard[col][2] == gameBoard[col][3]) return true

        return false
    }

    /**
     * Determine if the column has 3 or more balls with the same color
     */
    private fun isDeepHomogenous(col: Int): Boolean
    {
        if (col.isEmpty()) return false

        // If this column only has 2 balls, then this does nto qualify
        if (gameBoard[col][2] == 0) return false

        val color1 = gameBoard[col][0]
        val color2 = gameBoard[col][1]
        val color3 = gameBoard[col][2]
        val color4 = gameBoard[col][3]

        if ((color1 == color2) && (color1 == color3)) return true

        if ((color1 == color2) && (color1 == color4)) return true

        if ((color1 == color3) && (color1 == color4)) return true

        if ((color2 == color3) && (color2 == color4)) return true

        return false
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
        return (colorList[(gameBoard[col][slot])])
    }

    /**
     * Initialize the GameViewModel.
     */
    init {
        setGameFile(gGameFile)

        resetToWinningGameBoard()
        setMode(GameUIState.GameMode.Initialization)
    }

    /**
     * Reset the game board to the initial winning state
     */
    private fun resetToWinningGameBoard()
    {
        for (curCol in 0 ..< (Global.MAX_COLUMNS - 2)) {
            for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[curCol][curSlot] = curCol+1
        }

        // Put in no color for the last 2 columns
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[Global.MAX_COLUMNS-2][curSlot] = 0
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[Global.MAX_COLUMNS-1][curSlot] = 0

        floatingBallColorInt = 0

        floatingBallColumn = -1
    }

    /**
     * Select column to pop from
     */
    fun selectColumnToPop(col: Int)
    {
        if (col.isEmpty()) return

        // if selecting same column that was selected earlier, then ignore
        if (col == floatingBallColumn) return

        floatingBallColumn = col
        floatingBallColorInt = popColorInt(col)

        setMode(GameUIState.GameMode.PopBall)
    }

    /**
     * Check to see if floating ball can move to this column.
     * It is valid if there is room. If the column has a ball, then the floating ball
     * needs to match the same color as the top ball in the column.
     */
    fun validColumnToMoveTo(col: Int): Boolean
    {
        if (col.isEmpty()) return true

        if (col.isFull()) return false

        if (gameBoard[col][2] != 0) {
            return gameBoard[col][2] == floatingBallColorInt
        }

        if (gameBoard[col][1] != 0) {
            return gameBoard[col][1] == floatingBallColorInt
        }

        return gameBoard[col][0] == floatingBallColorInt
    }

    /**
     * Select column to push onto
     */
    fun selectColumnToPush(col: Int)
    {
        if (col.isFull()) return

        pushColorInt(col, floatingBallColorInt)

        floatingBallColorInt = 0
        floatingBallColumn = -1

        setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**
     * Determine whether it can safely exit the GameViewModel
     *
     * @return
     * - `true` Clean-up is done. It is safe to exit the view model
     * - `false` Unable to clean-up or in a middle of doing something. Do not exit the view model
     */
    fun canExitGameScreen(): Boolean {
        return when (gameUIState.value.mode) {
            GameUIState.GameMode.Initialization -> false
            GameUIState.GameMode.WonGame -> false
            GameUIState.GameMode.UpdatedGameBoard -> true
            GameUIState.GameMode.WaitingToPushBall -> true
            GameUIState.GameMode.PopBall -> true
            GameUIState.GameMode.ShowHint -> {
                true
            }
        }
    }

    /**
     * Start a new game Randomize game board
     */
    fun newGame()
    {
        resetToWinningGameBoard()
        repeat(100) {
            makeOneRandomMove()
        }
        packBoard()

        viewModelScope.launch (Dispatchers.IO){
            saveGameBoardToFile()
        }
        setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**
     * Consolidate all balls to 7 columns
     */
    private fun packBoard()
    {
        var ballColorInt: Int

        var col = Global.MAX_COLUMNS - 1
        while (!col.isEmpty()) {
            ballColorInt = popColorInt(col)
            pushToFirstAvailableSlot(ballColorInt)
        }

        col = Global.MAX_COLUMNS -2
        while (!col.isEmpty()) {
            ballColorInt = popColorInt(col)
            pushToFirstAvailableSlot(ballColorInt)
        }
    }

    /**
     * Push the ball to the first available slot
     */
    private fun pushToFirstAvailableSlot(ballColorInt: Int)
    {
        var curCol = 0
        while (curCol < Global.MAX_COLUMNS) {
            if (curCol.hasOpenSlot()) {
                pushColorInt(curCol, ballColorInt)
                return
            }
            curCol++
        }
    }

    /**
     * Move one ball randomly
     */
    private fun makeOneRandomMove() {
        val (curColForPop, ballColorInt) = randomPopBall(homogenousOnly = true)
        if (ballColorInt != 0) {
            randomPush(ballColorInt, curColForPop)
            return
        }

        val (curColForPop1, ballColorInt1) = randomPopBall(homogenousOnly = false)
        randomPush(ballColorInt1, curColForPop1)

        return
    }

    /**
     * Pop the ball from one of the columns
     *
     * @param homogenousOnly Try to pop from homogenous column
     *
     * @return
     * - Column that was popped from
     * - Color of the ball. If this is 0, then popping did not happen
     */
    private fun randomPopBall(homogenousOnly: Boolean = false): Pair<Int, Int>
    {
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
                if (!curColForPop.isEmpty()) {
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

        if  ((homogenousOnly) && (shallowHomogenousCount == 0)) return Pair(0, 0)

        var randomIndexForPop =
            if (homogenousOnly) {
                Random.nextInt(1, (shallowHomogenousCount + 1))
            } else {
                Random.nextInt(1, (getNonEmptyColumnCount() + 1))
            }

        while (curColForPop < Global.MAX_COLUMNS) {
            if (!curColForPop.isEmpty()) {
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

        return  Pair(curColForPop, ballColorInt)
    }

    /**
     * Push the ball in a random destination, with a preference to put
     * the ball on another ball that has different color
     *
     * @param ballColorInt Ball color (integer representation)
     * @param avoidColumnNumber Avoid pushing to this column. We do not want to push the ball on
     * same column where we just pop it from
     */
    private fun randomPush(ballColorInt: Int, avoidColumnNumber: Int)
    {
        var randomIndexForPush = Random.nextInt(1, getOpenColumnCount())
        var curColForPush = 0
        softPushLoop1@ while (curColForPush < Global.MAX_COLUMNS) {
            if  ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (pushColorInt(curColForPush, ballColorInt, soft = true)) {
                        // Completed the process of moving the ball. We can exit this function
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
            if  ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {

                randomIndexForPush--
                if (randomIndexForPush == 0) {

                    if (pushColorInt(curColForPush, ballColorInt, soft = true)) {
                        // Completed the process of moving the ball. We can exit this function
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
            if ((curColForPush != avoidColumnNumber) && (curColForPush.hasOpenSlot())) {
                randomIndexForPush--
                if (randomIndexForPush == 0) {
                    pushColorInt(curColForPush, ballColorInt, soft = false)

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
     * Determine the number of shallow homogenous columns.
     * Shallow homogenous type is 2 balls with the same color on the top
     *
     * @return
     * -  Number of shallow homogenous column
     */
    private fun getShallowHomogenousColumnCount(): Int
    {
        var count = 0

        for (curCol in 0 ..< Global.MAX_COLUMNS ) {
            if (isShallowHomogenous(curCol)) count++
        }

        return count
    }

    /**
     * Determine the number of columns that is a deep homogenous type.
     * Deep homogenous means there is 3 or more of the same color in the same column
     *
     * @return
     * -  Number of deep homogenous column
     */
    private fun getDeepHomogenousColumnCount(): Int
    {
        var count = 0

        for (curCol in 0 ..< Global.MAX_COLUMNS ) {
            if (isDeepHomogenous(curCol)) count++
        }

        return count
    }

    /**
     * Push the ball onto the specified column
     *
     * @param col Column number to push the ball into
     * @param ballColorInt Ball color
     * @param soft Soft push. Reject the push if we are pushing on top of ball that
     * has the same color
     *
     * @return
     * - `true`  Push was successful
     * - `false`  Push failed. Either there is no room, or this was a soft push where we attempt
     * to push the ball on top of another ball with the same color
     */
    private fun pushColorInt(col: Int, ballColorInt: Int, soft: Boolean = false): Boolean
    {
        if ((col >= Global.MAX_COLUMNS) || (col < 0)) {
            Log.i(Global.DEBUG_PREFIX, "Got unexpected column value of $col")
            return false
        }

        if (gameBoard[col][0] == 0) {
            gameBoard[col][0] = ballColorInt
            return (true)
        }

        if (gameBoard[col][1] == 0) {
            if ((soft) && (gameBoard[col][0] == ballColorInt)) return false
            gameBoard[col][1] = ballColorInt
            return (true)
        }

        if (gameBoard[col][2] == 0) {
            // (soft push only)  If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (gameBoard[col][1] == ballColorInt)) return false

            // (soft push only) If we are attempting to add ball on top of homogenous column, then reject it
            if ((soft) && (gameBoard[col][0] == gameBoard[col][1])) return false


            gameBoard[col][2] = ballColorInt
            return (true)
        }

        if (gameBoard[col][3] == 0) {
            // (soft push only)  If we are attempting to add ball on top of another of same color, reject it
            if ((soft) && (gameBoard[col][2] == ballColorInt)) return false

            // (soft push only) If we are attempting to add ball on top of shallow homogenous column, then reject it
            if ((soft) && (isShallowHomogenous(col))) return false

            gameBoard[col][3] = ballColorInt
            return (true)
        }
        return (false)
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
    private fun popColorInt(col:Int) : Int
    {
        var curSlot = Global.MAX_SLOT_PER_COLUMN - 1

        while (curSlot >= 0) {
            if (gameBoard[col][curSlot] != 0) {
                val ballColorInt = gameBoard[col][curSlot]
                gameBoard[col][curSlot] = 0
                return (ballColorInt)
            }
            curSlot--
        }

        return (0)
    }


    /**
     * Print the game board. This is used primarily for debugging purposes
     */
    private fun printGameBoardInt()
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

    fun readyToPushBall() {
        setMode(GameUIState.GameMode.WaitingToPushBall)
    }

    /**************** File operation routines *************************/
    private var _gameFile : File? = null

    /**
     * Set the game file
     *
     * @param file Game file
     */
    private fun setGameFile(file: File)
    {
        _gameFile = file
    }

    /**
     * Same the game to the file
     */
    private fun saveGameBoardToFile() {
        if (_gameFile == null) return

        try {
            val format = Json { prettyPrint = true }
            val ballList = mutableListOf<Int>()
            for (curCol in 0..<Global.MAX_COLUMNS) {
                for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                    ballList += gameBoard[curCol][curSlot]
                }
            }

            val output = format.encodeToString(ballList)
            val writer = FileWriter(_gameFile)
            writer.write(output)
            writer.close()
        } catch (e: SerializationException) {
            Log.i(Global.DEBUG_PREFIX, "Serialization error. Unable to encode ball list. Reason: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.i(Global.DEBUG_PREFIX, "Serialization error. Detected non-compliant format while saving game file. Reason: ${e.message}")
        } catch (e: IOException) {
            Log.i(Global.DEBUG_PREFIX, "File I/O error. Unable to save game file. Reason: ${e.message}")
        } catch (e: Exception) {
            Log.i(Global.DEBUG_PREFIX, "Unable to save game. Reason: ${e.message}")
        }
    }

    /**
     * Load the start of the game
     */
    private fun loadGameBoardFromFile()
    {
        if (_gameFile == null) return

        try {
            val reader = FileReader(_gameFile)
            val data = reader.readText()
            reader.close()

            val ballList = Json.decodeFromString<List<Int>>(data)
            var index = 0
            for (curCol in 0..<Global.MAX_COLUMNS) {
                for (curSlot in 0..<Global.MAX_SLOT_PER_COLUMN) {
                    gameBoard[curCol][curSlot] = ballList[index]
                    index++
                }
            }
        } catch (e: SerializationException) {
            Log.i(Global.DEBUG_PREFIX, "Serialization error. Unable to decode ball list when loading the game file. Reason: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.i(Global.DEBUG_PREFIX, "Serialization error. Detected non-compliant format while loading game file. Reason: ${e.message}")
        } catch (e: IOException) {
            Log.i(Global.DEBUG_PREFIX, "File I/O error. Unable to load game file. Reason: ${e.message}")
        } catch (e: Exception) {
            Log.i(Global.DEBUG_PREFIX, "An error while trying to load the saved gam. Reason: ${e.message}")
        }
    }

    /**
     * Reset the game. Retrieve from saved game
     */
    fun resetGame() {
        viewModelScope.launch(Dispatchers.IO) {
            loadGameBoardFromFile()
            setMode(GameUIState.GameMode.UpdatedGameBoard)
        }
    }
}

