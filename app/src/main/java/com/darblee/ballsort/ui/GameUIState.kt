package com.darblee.ballsort.ui

/**
 * The UI state of game screen
 *
 * @param _mode The public field is [mode] (read-only access). The current game mode. Possible game
 * mode is defined at [GameMode]
 */
data class GameUIState(
    private var _mode: GameMode = GameMode.Initialization,
) {
    var mode = _mode
        private set

    /**
     * Various game modes for UI state
     *
     * @property Initialization Initializing  Game View Model, such as loading game file content
     * @property UpdatedGameBoard There is an updated (or new) game board. Now waiting for user to
     * make a move
     * @property PopBall Processing ball movement
     * @property ShowHint Find a hint and now need to show the user with animation
     * @property WonGame One ball remaining. User has won the game
     * @property WaitingToPushBall There is no winning move. It will remain this way until there is a new
     * game or when user undo a move
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