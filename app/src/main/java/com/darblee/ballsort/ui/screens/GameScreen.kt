package com.darblee.ballsort.ui.screens

import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darblee.ballsort.Global
import com.darblee.ballsort.domain.model.GameViewModel
import com.darblee.ballsort.gAudio_victory
import com.darblee.ballsort.gGameViewModel
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
import com.darblee.ballsort.utilities.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    var announceVictory = false
    var gridChange = false
    var popBall = false

    var undoButtonState by remember { mutableStateOf(true) }

    var backPressed by remember { mutableStateOf(false) }
    BackPressHandler(onBackPressed = {backPressed = true})
    if (backPressed) {
        exitProcess(1)
    }

    val historyFile = File(LocalContext.current.filesDir, Global.GAME_HISTORY_FILENAME)

    gGameViewModel = GameViewModel.getInstance(historyFile)

    val gameUIState by gGameViewModel.gameUIState.collectAsStateWithLifecycle()

    when (gameUIState.mode) {

        // Because "initial data loading" mode is only set at initialization, this is only called once.
        // WHen singleton object class GameViewModel get instantiated, it will load the game files
        // After the completion of file loading, it will set to "UpdatedGameBoard" mode.
        GameUIState.GameMode.Initialization -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Initializing...")
            gridChange = true
            undoButtonState = gGameViewModel.ableToUndo()
        }

        GameUIState.GameMode.NewGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : New Game")
            announceVictory = false
            undoButtonState = false
        }

        GameUIState.GameMode.ResetGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Restart Game")
            announceVictory = false
            undoButtonState = false
        }

        GameUIState.GameMode.WonGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Announce Victory")
            announceVictory = true
            undoButtonState = gGameViewModel.ableToUndo()
        }

        GameUIState.GameMode.UpdatedGameBoard -> {
            Log.i(
                Global.DEBUG_PREFIX,
                "Recompose - ${gameUIState.mode} : Board has been modified. Typically start a new user move."
            )
            gridChange = true
            undoButtonState = gGameViewModel.ableToUndo()
        }

        GameUIState.GameMode.RevertMoveEnableUndo -> {
            Log.i(
                Global.DEBUG_PREFIX,
                "Recompose - ${gameUIState.mode} : Revert move. Board has been modified. May need to refresh undo button"
            )
            gridChange = true
            undoButtonState = true
        }

        GameUIState.GameMode.RevertMoveDisableUndo -> {
            Log.i(
                Global.DEBUG_PREFIX,
                "Recompose - ${gameUIState.mode} : Revert move. Board has been modified. May need to refresh undo button"
            )
            gridChange = true
            undoButtonState = false
        }

        GameUIState.GameMode.WaitingToPushBall -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Ready to push ball")
            popBall = true
            undoButtonState = gGameViewModel.ableToUndo()

        }

        GameUIState.GameMode.PopBall -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Process popping ball")
            gridChange = true
            popBall = true
            undoButtonState = gGameViewModel.ableToUndo()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawButtons(undoButtonState)

        DrawGameBoard(Modifier, gridChange, popBall, announceVictory)
    }
}

/**
 * Draw the buttons
 */
@Composable
private fun DrawButtons(undoButtonRequestState: Boolean)
{
    Log.i(Global.DEBUG_PREFIX, "Draw Button called undoMade = $undoButtonRequestState")
    var undoButtonState by remember { mutableStateOf(undoButtonRequestState) }

    val view = LocalView.current

    if (undoButtonState == undoButtonRequestState) {
        Log.i(Global.DEBUG_PREFIX, "No change to the undo button. It is currently in $undoButtonRequestState state")
    } else {
        undoButtonState = undoButtonRequestState
        Log.i(Global.DEBUG_PREFIX, "Change to the undo button to $undoButtonRequestState")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Button(
            onClick = {
                view.click()
                gGameViewModel.newGame()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "New game",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
            Text("New Game", style = MaterialTheme.typography.titleSmall)
        }
        Button(
            onClick = {
                view.click()
                gGameViewModel.resetGame()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Restart",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
            Text("Restart", style = MaterialTheme.typography.titleSmall)
        }
        Button(
            onClick = {
                view.click()
                gGameViewModel.userRevertToPreviousMove()
            },
            enabled = undoButtonState
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Undo",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
            Text("Undo", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private const val gBallRadius = 65f
private var gWidthSpacing = 0F
private var gVerticalSpacing = 200f

/**
 * Draw the game
 */
@Composable
private fun DrawGameBoard(
    modifier: Modifier = Modifier,
    updatedGameBoard: Boolean,
    popBall: Boolean,
    announceVictory: Boolean, )
{
    Log.i(Global.DEBUG_PREFIX, "Draw board called. updateGameBoard = $updatedGameBoard")

    val columnLength = gBallRadius * 10
    val verticalMidpoint = (gVerticalSpacing * 2) + columnLength
    var drawBoardToggle by remember { mutableStateOf(true) }

    if (popBall) animatePopBallSetup()

    /**
     * textMeasurer is used to draw text on canvas.  This is used for animated victory message.
     */
    val textMeasurer = rememberTextMeasurer()
    val animatedVictoryMessage = remember { Animatable(initialValue = 0f) }
    if (announceVictory) {
        AnimateVictoryMessageSetup(animatedVictoryMessage)
    } else {
        AnimateVictoryMessageReset(animatedVictoryMessage)
    }

    Box {
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

                                // Check if we are pushing same ball back to its own column, essentially undoing the move.
                                if (column == gGameViewModel.floatingBallColumn) {
                                    view.click()
                                    gGameViewModel.userSelectColumnToPush(column)
                                }

                                // Only push if this is valid column to move to
                                if (gGameViewModel.validColumnToMoveTo(column))
                                {
                                    view.click()
                                    gGameViewModel.userSelectColumnToPush(column)
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
                        drawBoardToggle = !drawBoardToggle
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
                    animatePopBallPerform(this)
                }

                if (announceVictory) {
                    animateVictoryMsgInvoke(
                        drawScope,
                        animatedVictoryMessage,
                        textMeasurer)
                }
            }
        }
    }
}

/**
 * Setup victory message animation. Define animation specification
 *
 * @param animateCtl Animate object that control animation state of the victory message
 */
@Composable
private fun AnimateVictoryMessageSetup(animateCtl: Animatable<Float, AnimationVector1D>)
{
    // Run this set-up only once
    LaunchedEffect(Unit) {
        // Use coroutine to ensure both animation and sound happen in parallel
        coroutineScope {
            launch(Dispatchers.Main) {
                animateCtl.snapTo(0f)
                animateCtl.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1500,
                        easing = LinearOutSlowInEasing
                    )
                )
                gGameViewModel.setModeUpdateGameBoard()

                animateCtl.snapTo(0f)
                animateCtl.stop()

                // Pause for 0.5 second to allow user to see victory message before it disappear
                delay(500)
            }  // launch

            launch(Dispatchers.Main) {
                gAudio_victory.start()
            }
        }
    }
}

/**
 * Perform the actual victory message animation
 *
 * @param drawScope Canvas to draw the text animation on
 * @param animateCtl  Object that animation control state of the victory message
 * @param textMeasurer Responsible for measuring a text in its entirety so that it
 * can be drawn on the canvas (drawScope)
 */
private fun animateVictoryMsgInvoke(
    drawScope: DrawScope,
    animateCtl: Animatable<Float, AnimationVector1D>,
    textMeasurer: TextMeasurer
)
{
    val animationValue = animateCtl.value
    with (drawScope) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val text = "You won!"
        val animatedTextSize = 75 * animationValue
        val textStyle = TextStyle(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = animatedTextSize.sp
        )
        val textLayoutResult: TextLayoutResult =
            textMeasurer.measure(text = AnnotatedString(text), style = textStyle)
        val textSize = textLayoutResult.size
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = Offset(
                x = (canvasWidth - textSize.width) * 0.5f, // in center
                y = (canvasHeight * 0.25f)
            ),
            style = textStyle
        )
    }
}

/**
 * Stop and reset the victory animation control
 *
 * @param animateCtl Animate object that control animation state of the victory message
 */
@Composable
private fun AnimateVictoryMessageReset(animateCtl: Animatable<Float, AnimationVector1D>)
{
    LaunchedEffect(Unit) {
        animateCtl.stop()
        animateCtl.snapTo(0f)
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
private fun animatePopBallSetup()
{

}

/**
 *
 */
private fun animatePopBallPerform(drawScope: DrawScope)
{
    drawBall(drawScope, gGameViewModel.floatingBallColumn, -1)
    gGameViewModel.readyToPushBall()
}

/**
 * BackPressHandler is used to intercept back press
 *
 * When doing back press on main screen, need to confirm with the user whether
 * it should exit the app or not. It uses the [BackPressHandler] function.
 *
 * We created [OnBackPressedCallback] and add it to the onBackPressDispatcher
 * that controls dispatching system back presses. We enable the callback whenever
 * our Composable is recomposed, which disables other internal callbacks responsible
 * for back press handling. The callback is added on any lifecycle owner change and removed
 * on dispose.
 */
@Composable
fun BackPressHandler(
    backPressedDispatcher: OnBackPressedDispatcher? =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
    onBackPressed: () -> Unit
) {
    val currentOnBackPressed by rememberUpdatedState(newValue = onBackPressed)

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    DisposableEffect(key1 = backPressedDispatcher) {
        backPressedDispatcher?.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }
}


@Preview(device = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420")
@Composable
fun Test()
{
    GameScreen()
}
