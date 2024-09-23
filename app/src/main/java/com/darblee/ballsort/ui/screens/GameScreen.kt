package com.darblee.ballsort.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darblee.ballsort.Global
import com.darblee.ballsort.domain.model.GameViewModel
import com.darblee.ballsort.gGameViewModel
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
import com.darblee.ballsort.utilities.click
import java.io.File

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    var hintBallRec : GameUIState.GameMode.ShowHint ?= null
    var noWinnableMove by remember {  mutableStateOf( false ) }
    var showNoWinnableMoveDialogBox by remember { mutableStateOf(false) }
    var initializing = false
    var announceVictory = false
    var gridChange = false
    var popBall = false

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
            gridChange = true
        }

        GameUIState.GameMode.WaitingToPushBall -> {
            popBall = true
            Log.i(Global.DEBUG_PREFIX, "${gameUIState.mode} : Ready to push ball")
        }

        GameUIState.GameMode.PopBall -> {
            Log.i("Game Recompose: ", "${gameUIState.mode} : Process popping ball")
            gridChange = true
            popBall = true
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

    val view = LocalView.current

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
                view.click()
                gGameViewModel.newGame()
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
        DrawGameBoard(Modifier, gridChange, popBall)
    }
}

private const val gBallRadius = 65f
private var gWidthSpacing = 0F
private var gVerticalSpacing = 200f

/**
 * Draw the game
 */
@Composable
fun DrawGameBoard(
    modifier: Modifier = Modifier,
    updatedGameBoard: Boolean,
    popBall: Boolean, )
{
    val columnLength = gBallRadius * 10
    val verticalMidpoint = (gVerticalSpacing * 2) + columnLength
    val drawBoardToggle = remember { mutableStateOf(true) }

    if (popBall) AnimatePopballSetup()

    Box() {
        val view = LocalView.current

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            var xCol = ((tapOffset.x - (gWidthSpacing / 2)) / gWidthSpacing).toInt()
                            if (xCol > (Global.MAX_COLUMNS - 1))  { xCol = (Global.MAX_COLUMNS - 1) }

                            val column = if (tapOffset.y > verticalMidpoint) { xCol + (Global.MAX_COLUMNS / 2) } else { xCol }

                            if (gGameViewModel.hasFloatingBall()) {
                                if (column == gGameViewModel.floatingBallColumn) {
                                    view.click()
                                    gGameViewModel.selectColumnToPush(column)
                                }

                                // Only push if the column is not full
                                if (!gGameViewModel.columnIsFull(column))
                                {
                                    view.click()
                                    gGameViewModel.selectColumnToPush(column)
                                }
                            } else {
                                view.click()
                                gGameViewModel.selectColumnToPop(column)
                            }
                        }
                    )
                }  // .pointerInput
        ) {
            val drawScope = this
            var startX: Float
            var startY: Float

            with (drawScope) {
                gWidthSpacing = size.width / ((Global.MAX_COLUMNS / 2) + 1)
                for (curCol in 0..< Global.MAX_COLUMNS) {
                    startY = if (curCol < (Global.MAX_COLUMNS / 2)) {
                        gVerticalSpacing
                    } else {
                        verticalMidpoint
                    }
                    startX = ((curCol % (Global.MAX_COLUMNS / 2)) + 1) * gWidthSpacing

                    if (updatedGameBoard) {
                        // Force recompose to redraw canvas-based game board
                        drawBoardToggle.value = !drawBoardToggle.value
                    }

                    drawLine(
                        color = Color.White,
                        start = Offset(startX, startY + (1.5F * gBallRadius)),
                        end = Offset(startX, (startY + columnLength)),
                        strokeWidth = 10f
                    )

                    for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) {
                        drawBall(this, curCol, curSlot)
                    }
                }
                if (popBall) {
                    animatePopballPerform(this)
                }
            }
        }
    }
}

/**
 * Draw the ball
 *
 * @param drawScope Canvas scope to draw on
 * @param col Specified column to draw ball on
 * @param slot Specified slot to draw ball on. If the slot is -1, then this is a floating ball
 */
fun drawBall(drawScope: DrawScope, col: Int, slot: Int)
{
    var startX: Float
    var startY: Float

    val columnLength = gBallRadius * 10

    with (drawScope) {
        startY = if (col < (Global.MAX_COLUMNS / 2)) {
            gVerticalSpacing
        } else {
            (gVerticalSpacing * 2) + columnLength
        }
        startX = ((col % (Global.MAX_COLUMNS / 2)) + 1) * gWidthSpacing

        if (slot != -1) {
            drawCircle(
                color = gGameViewModel.getBallColor(col, slot),
                radius = gBallRadius,
                center = Offset(startX, startY + yDistance(slot))
            )
        } else {
            drawCircle(
                color = colorList[gGameViewModel.floatingBallColorInt],
                radius = gBallRadius,
                center = Offset(startX, startY )
            )
        }
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

/********************* Animation Routine ************************************/

/**
 *
 */
private fun AnimatePopballSetup()
{

}

/**
 *
 */
private fun animatePopballPerform(drawScope: DrawScope)
{
    drawBall(drawScope, gGameViewModel.floatingBallColumn, -1)
    gGameViewModel.readyToPushBall()
}



@Preview(device = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420")
@Composable
fun Test()
{
    GameScreen()
}
