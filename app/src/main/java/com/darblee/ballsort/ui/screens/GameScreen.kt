package com.darblee.ballsort.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darblee.ballsort.Global
import com.darblee.ballsort.domain.model.GameViewModel
import com.darblee.ballsort.gGameViewModel
import com.darblee.ballsort.ui.GameUIState
import java.io.File

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    var moveBallRec : GameUIState.GameMode.MoveBall? = null
    var hintBallRec : GameUIState.GameMode.ShowHint ?= null
    var noWinnableMove by remember {  mutableStateOf( false ) }
    var showNoWinnableMoveDialogBox by remember { mutableStateOf(false) }
    var initializing = false
    var announceVictory = false

    val gameBoardFile = File(LocalContext.current.filesDir, Global.GAME_BOARD_FILENAME)
    val historyFile = File(LocalContext.current.filesDir, Global.GAME_HISTORY_FILENAME)

    gGameViewModel = GameViewModel.getInstance(gameBoardFile, historyFile)

    val gameUIState by gGameViewModel.gameUIState.collectAsStateWithLifecycle()

    when (gameUIState.mode) {

        // Because "initial data loading" mode is only set at initialization, this is only called once.
        // WHen singleton object class GameViewModel get instantiated, it will load the game files
        // After the completion of file loading, it will set to "UpdatedGameBoard" mode.
        GameUIState.GameMode.Initialization -> {
            Log.i("Game Recompose: ", "${gameUIState.mode} : Initializing...")
            initializing = true
        }

        GameUIState.GameMode.WonGame -> {
            Log.i("Game Recompose: ", "${gameUIState.mode} : Announce Victory")
            announceVictory = true
        }

        GameUIState.GameMode.UpdatedGameBoard -> {
            Log.i(
                "Game Recompose: ",
                "${gameUIState.mode} : Board has been modified. Typically start a new user move."
            )
            noWinnableMove = false
        }

        GameUIState.GameMode.NoWinnableMove -> {
            noWinnableMove = true
            Log.i("Game Recompose: ", "${gameUIState.mode} : No winnable move. No dialog needed")

        }

        GameUIState.GameMode.MoveBall -> {
            Log.i("Game Recompose: ", "${gameUIState.mode} : Process moving ball")
            moveBallRec = gameUIState.mode.let { GameUIState.GameMode.MoveBall }
        }

        GameUIState.GameMode.ShowHint -> {
            Log.i(
                "Game Recompose: ",
                "${gameUIState.mode} : Show hint by do a shadow ball movement "
            )
            hintBallRec = gameUIState.mode.let {
                GameUIState.GameMode.ShowHint
            }
        }
    }
    Greeting(
        name = "Ball Sort",
        modifier = modifier
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}