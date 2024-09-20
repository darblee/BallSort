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
     * For reference, see [ https://dev.to/zachklipp/introduction-to-the-compose-snapshot-system-19cn ]
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
     * @return True if column is empty, otherwise return false
     */
    private fun Int.isEmpty() = gameBoard[this][0] == Color.Unspecified

    /**
     *  Determine if the column has available slot to push the ball into
     *
     *  @return True if iut has available slot, otherwise return false
     */
    private fun Int.hasOpenSlot() = gameBoard[this][Global.MAX_SLOT_PER_COLUMN - 1] == Color.Unspecified

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
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[0][curSlot] = Color.Red
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[1][curSlot] = Color.Blue
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[2][curSlot] = Color.Yellow
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[3][curSlot] = Color.Green
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[4][curSlot] = Color.Magenta
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[5][curSlot] = Color.Cyan
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[6][curSlot] = Color.LightGray
        for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) gameBoard[7][curSlot] = Color.DarkGray

        setMode(GameUIState.GameMode.Initialization)
    }

    /**
     * Determine whether it can safely exit the GameViewModel
     *
     * @return
     * - true Clean-up is done. It is safe to exit the view model
     * - false Unable to clean-up or in a middle of doing something. Do not exit the view model
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
        makeOneRandomMove()
        setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**
     * Move one ball randomly
     */
    private fun makeOneRandomMove() {
        var randomIndexForPop = Random.nextInt(1, (getNonEmptyColumnCount() + 1))
        var curColForPop = 0

        while (curColForPop < Global.MAX_COLUMNS) {
            if (!curColForPop.isEmpty()) {
                randomIndexForPop--
                if (randomIndexForPop == 0) {
                    val ballColor = pop(curColForPop)
                    Log.i(Global.DEBUG_PREFIX, "Pop from column $curColForPop and found color ${ballColor.toString()}")
                    var randomIndexForPush = Random.nextInt(1, (getOpenColumnCount() + 1))
                    var curColForPush = 0
                    while (curColForPush < Global.MAX_COLUMNS) {
                        if (curColForPush.hasOpenSlot()) {
                            randomIndexForPush--
                            if (randomIndexForPush == 0) {
                                push(curColForPush, ballColor)

                                // Completed the process of moving the ball. We can exit this function
                                return
                            }
                        }
                        curColForPush++
                    }
                }
            }
            curColForPop++
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
     * @return number of columns tht has open slot
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
     * Push the ball onto the specified column
     *
     * @param col Column number to push the ball into
     * @param color Ball color
     */
    private fun push(col: Int, ballColor: Color)
    {
        Log.i(Global.DEBUG_PREFIX, "Push into column $col. Ball color is ${ballColor.toString()}")
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

