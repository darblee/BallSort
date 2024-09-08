package com.darblee.ballsort.domain.model

import androidx.lifecycle.ViewModel
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.utilities.PairArgsSingletonHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
     * Initialize the GameViewModel
     */
    init {
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
}

