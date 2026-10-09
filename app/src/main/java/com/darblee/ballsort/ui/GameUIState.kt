package com.darblee.ballsort.ui

/**
 * The UI state of game screen
 *
 * @param _mode The public field is [mode] (read-only access). The current game mode. Possible game
 * mode is defined at [GameMode]
 */
data class GameUIState(
    private var _mode: GameMode = GameMode.Initialization,
    private var _stateId: Long = 0L,
    val gridChange: Boolean = false,
    val popBall: Boolean = false,
    val announceVictory: Boolean = false,
    val undoEnabled: Boolean = false,
) {
    var mode = _mode
        private set

    var stateId = _stateId
        private set

    /**
     * Represents the various states of the game flow and UI logic.
     *
     */
    sealed class GameMode {

        data object Initialization : GameMode()
        data object NewGame: GameMode()
        data object UpdatedGameBoard : GameMode()
        data object ResetGame: GameMode()
        data object RevertMoveEnableUndo : GameMode()
        data object RevertMoveDisableUndo : GameMode()
        data object PopBall : GameMode()
        data object WonGame : GameMode()
        data object WaitingToPushBall : GameMode()
    }
}