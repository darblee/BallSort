package com.darblee.ballsort.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    var gridChange = false


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
            gridChange = true

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
            gridChange = true
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

    Log.i(Global.DEBUG_PREFIX, "gridchange = $gridChange")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,

    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = {
                gGameViewModel.randomizeGameBoard()
            }) {
                Text("New Game")
            }
            Button(onClick = { /*TODO*/ }) {
                Text("Undo")
            }
        }
        Slider(
            modifier = Modifier.padding(5.dp),
            enabled = true,
            valueRange = 1f .. 100f,
            steps = 10,
            onValueChange = { /* TODO */ },
            onValueChangeFinished = {
                /* TODO */
            },
            value = 20f
        )
         DrawGameBoard(gridChange , Modifier)
    }
}

private const val gBallRadius = 65f
private var gWidthSpacing = 0F
private var gHorizontalSpacing = 200f

/**
 * Draw the game
 */
@Composable
fun DrawGameBoard(updatedGameBoard: Boolean, modifier: Modifier = Modifier) {

    val drawBoardToggle = remember { mutableStateOf(true) }

    Log.i(Global.DEBUG_PREFIX, "Draw game board")

    Box() {
        Canvas(
            modifier = modifier.fillMaxSize(),
        ) {
            val drawScope = this
            var startX: Float
            var startY: Float
            val columnLength = gBallRadius * 10

            with (drawScope) {
                gWidthSpacing = size.width / ((Global.MAX_COLUMNS / 2) + 1)
                for (curCol in 0..< Global.MAX_COLUMNS) {
                    startY = if (curCol < (Global.MAX_COLUMNS / 2)) {
                        gHorizontalSpacing
                    } else {
                        (gHorizontalSpacing * 2) + columnLength
                    }
                    startX = ((curCol % (Global.MAX_COLUMNS / 2)) + 1) * gWidthSpacing

                    if (updatedGameBoard) {
                        // Force recompose to redraw canvas-based game board
                        drawBoardToggle.value = !drawBoardToggle.value
                    }

                    drawLine(
                        color = Color.White,
                        start = Offset(startX, startY),
                        end = Offset(startX, (startY + columnLength)),
                        strokeWidth = 10f
                    )

                    for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) {
                        drawBall(this, curCol, curSlot)
                    }
                }
            }
        }
    }
}

/**
 * Draw the ball
 */
fun drawBall(drawScope: DrawScope, col: Int, slot: Int)
{
    var startX: Float
    var startY: Float

    val columnLength = gBallRadius * 10

    with (drawScope) {
        startY = if (col < (Global.MAX_COLUMNS / 2)) {
            gHorizontalSpacing
        } else {
            (gHorizontalSpacing * 2) + columnLength
        }
        startX = ((col % (Global.MAX_COLUMNS / 2)) + 1) * gWidthSpacing

        drawCircle(
            color = gGameViewModel.getBallColor(col, slot),
            radius = gBallRadius,
            center = Offset(startX, startY + yDistance(slot))
        )
    }
}

/**
 * Distance from base of column
 */
fun yDistance(slot: Int): Float
{
    val ballRadius = 65f
    val columnLength = ballRadius * 10
    return (columnLength - ballRadius - (slot * (2 * ballRadius)))
}

@Preview(device = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420")
@Composable
fun Test()
{
    GameScreen()
}
