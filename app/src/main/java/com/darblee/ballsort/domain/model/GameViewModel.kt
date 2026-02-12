package com.darblee.ballsort.domain.model

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darblee.ballsort.Global
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
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

/**
 * ViewModel responsible for coordinating the Ball Sort game.
 *
 * Delegates board state and game rules to [GameBoard], and manages
 * UI state, move history, and file persistence.
 *
 * @param gHistFile The file used to persist and load the game's move history.
 */
class GameViewModel(gHistFile: File) : ViewModel() {
    companion object {
        fun factory(histFile: File): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(histFile) as T
                }
            }
        }
    }

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

    private val board = GameBoard()

    /**
     * List of all the moves in a single game
     */
    private var _moveHistory: MutableList<gameSnapshot> = mutableListOf()

    /************************ Board state accessors for the UI ************************/

    val floatingBallColumn: Int get() = board.floatingBallColumn

    val floatingBallColorInt: Int get() = board.floatingBallColorInt

    fun hasFloatingBall(): Boolean = board.hasFloatingBall()

    /**
     * Get the ball color at a specific location in the game board.
     *
     * @param col Specified Column number location
     * @param slot Specified slot location
     * @return Ball color
     */
    fun getBallColor(col: Int, slot: Int): Color = colorList[board.getBallColorInt(col, slot)]

    /**
     * Check to see if floating ball can move to this column.
     * It is valid if there is room. If the column has a ball, then the floating ball
     * needs to match the same color as the top ball in the column.
     */
    fun validColumnToMoveTo(col: Int): Boolean = board.validColumnToMoveTo(col)

    /**
     * Initialize the GameViewModel.
     */
    init {
        viewModelScope.launch(Dispatchers.IO) {
            setHistoryFile(gHistFile)

            viewModelScope.launch(Dispatchers.IO) {

                loadHistoryFromFile()
                if (_moveHistory.isEmpty()) {
                    newGame()
                } else {
                    setMode(GameUIState.GameMode.UpdatedGameBoard)
                }
            }
        }
    }

    /************************ User action handlers ************************/

    /**
     * Select column to pop from.
     */
    fun selectColumnToPop(col: Int) {
        if (board.isColumnEmpty(col)) return

        // if selecting same column that was selected earlier, then ignore
        if (col == board.floatingBallColumn) return

        board.popBall(col)

        setMode(GameUIState.GameMode.PopBall)
    }

    /**
     * Select column to push onto.
     */
    fun userSelectColumnToPush(col: Int) {
        if (board.isColumnFull(col)) return

        val revertBackToSameColumn = (col == board.floatingBallColumn)

        board.pushFloatingBall(col)

        // Check if user just want to undo the operation by putting
        // the ball back to the same slot. If so, then this is NOT
        // a new move. No need to add snapshot.
        if (revertBackToSameColumn) {
            if (ableToUndo())
                setMode(GameUIState.GameMode.RevertMoveEnableUndo)
            else
                setMode(GameUIState.GameMode.RevertMoveDisableUndo)

            return
        }

        addCurrentSnapshotToHistory()

        if (board.hasWon())
            setMode(GameUIState.GameMode.WonGame)
        else
            setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**
     * Determine if user is able to perform undo operation.
     */
    fun ableToUndo(): Boolean {
        if (board.hasFloatingBall()) return true

        val moveCount = _moveHistory.count()
        return (moveCount > 1)
    }

    /**
     * User revert to last move. Undo operation.
     */
    fun userRevertToPreviousMove() {
        // If we have a floating ball, then the undo operation is to simply remove the floating ball
        if (board.hasFloatingBall()) {
            board.pushFloatingBall(board.floatingBallColumn)
            setMode(GameUIState.GameMode.UpdatedGameBoard)
            return
        }

        val moveCount = _moveHistory.count()
        if (moveCount < 2) return

        val prevGameSnapshot = _moveHistory[moveCount - 2]
        board.restoreFromSnapshot(prevGameSnapshot)
        _moveHistory.removeAt(moveCount - 1)

        if (ableToUndo())
            setMode(GameUIState.GameMode.RevertMoveEnableUndo)
        else
            setMode(GameUIState.GameMode.RevertMoveDisableUndo)

        viewModelScope.launch(Dispatchers.IO) {
            saveGameHistoryToFile()
        }
    }

    /**
     * Start a new game. Randomize game board.
     */
    fun newGame() {
        _moveHistory.clear()

        board.randomize()

        addCurrentSnapshotToHistory()
        setMode(GameUIState.GameMode.NewGame)
    }

    /**
     * Reset the game. Retrieve from saved game.
     */
    fun resetGame() {
        if (_moveHistory.isEmpty()) return

        // Need to make a new copy of game snapshot
        val firstSnapshot = (_moveHistory[0]).toMutableList()
        board.restoreFromSnapshot(firstSnapshot)
        _moveHistory.clear()
        _moveHistory.add(firstSnapshot)

        viewModelScope.launch(Dispatchers.IO) {
            saveGameHistoryToFile()
            setMode(GameUIState.GameMode.ResetGame)
        }
    }

    /*************** Set mode routines ***********************/

    /**
     * Update [_uiGameState] to a specified mode.
     *
     * For more details, see [gameUIState]
     */
    private var _stateCounter = 0L

    private fun setMode(mode: GameUIState.GameMode) {
        _uiGameState.update { curState ->
            curState.copy(_mode = mode, _stateId = ++_stateCounter)
        }
    }

    fun readyToPushBall() {
        setMode(GameUIState.GameMode.WaitingToPushBall)
    }

    /**
     * Set mode to "Update Game Board" mode.
     */
    fun setModeUpdateGameBoard() {
        setMode(GameUIState.GameMode.UpdatedGameBoard)
    }

    /**************** File operation routines *************************/
    private var _historyFile: File? = null

    private fun setHistoryFile(file: File) {
        _historyFile = file
    }

    /**
     * Save the entire game history to file.
     */
    private fun saveGameHistoryToFile() {
        if (_historyFile == null) return

        try {
            val format = Json { prettyPrint = true }
            val output = format.encodeToString(_moveHistory)
            val writer = FileWriter(_historyFile)
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
     * Load the saved history from file.
     */
    private fun loadHistoryFromFile() {
        if (_historyFile == null) return

        try {
            val reader = FileReader(_historyFile)
            val data = reader.readText()
            reader.close()

            val historyList = Json.decodeFromString<List<List<Int>>>(data)
            _moveHistory.clear()

            historyList.forEach { curSnapshot ->
                val newGameSnapshot: gameSnapshot = mutableListOf()
                curSnapshot.forEach { curBallColorInt ->
                    newGameSnapshot.add(curBallColorInt)
                }
                _moveHistory.add(newGameSnapshot)
            }
            board.restoreFromSnapshot(_moveHistory.last())

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
     * Create a snapshot based on current state of game and add it to
     * history records.
     */
    private fun addCurrentSnapshotToHistory() {
        val curGameSnapshot = board.createSnapshot()
        _moveHistory.add(curGameSnapshot)

        viewModelScope.launch(Dispatchers.IO) {
            saveGameHistoryToFile()
        }
    }
}
