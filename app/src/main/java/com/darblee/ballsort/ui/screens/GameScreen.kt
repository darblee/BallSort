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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darblee.ballsort.Global
import com.darblee.ballsort.domain.model.GameViewModel
import com.darblee.ballsort.gAudio_victory
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
import com.darblee.ballsort.utilities.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Activity
import java.io.File

/**
 * The main entry point for the Ball Sort game screen.
 *
 * This composable manages the game state and UI layout, coordinating between the
 * [GameViewModel] and the visual components. It handles different game modes
 * (Initialization, New Game, Reset, Victory, etc.), responds to back press events,
 * and renders both the control buttons and the interactive game board.
 *
 * @param modifier The modifier to be applied to the root Column layout.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    var announceVictory = false
    var gridChange = false
    var popBall = false

    var undoButtonState by remember { mutableStateOf(true) }

    val activity = LocalContext.current as Activity
    BackPressHandler(onBackPressed = { activity.finish() })

    val historyFile = File(LocalContext.current.filesDir, Global.GAME_HISTORY_FILENAME)

    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModel.factory(historyFile)
    )

    val gameUIState by gameViewModel.gameUIState.collectAsStateWithLifecycle()

    when (gameUIState.mode) {

        // Because "initial data loading" mode is only set at initialization, this is only called once.
        // WHen singleton object class GameViewModel get instantiated, it will load the game files
        // After the completion of file loading, it will set to "UpdatedGameBoard" mode.
        GameUIState.GameMode.Initialization -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Initializing...")
            gridChange = true
            undoButtonState = gameViewModel.ableToUndo()
        }

        GameUIState.GameMode.NewGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : New Game")
            announceVictory = false
            undoButtonState = false
            gridChange = true
        }

        GameUIState.GameMode.ResetGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Restart Game")
            announceVictory = false
            undoButtonState = false
            gridChange = true

        }

        GameUIState.GameMode.WonGame -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Announce Victory")
            announceVictory = true
            undoButtonState = gameViewModel.ableToUndo()
        }

        GameUIState.GameMode.UpdatedGameBoard -> {
            Log.i(
                Global.DEBUG_PREFIX,
                "Recompose - ${gameUIState.mode} : Board has been modified. Typically start a new user move."
            )
            gridChange = true
            undoButtonState = gameViewModel.ableToUndo()
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
            undoButtonState = gameViewModel.ableToUndo()
        }

        GameUIState.GameMode.PopBall -> {
            Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode} : Process popping ball")
            gridChange = true
            popBall = true
            undoButtonState = gameViewModel.ableToUndo()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawButtons(undoButtonState, gameViewModel)

        DrawGameBoard(Modifier, gridChange, popBall, announceVictory, gameViewModel)
    }
}

/**
 * Renders the control buttons for the game, including options to start a new game,
 * restart the current level, and undo the last move.
 *
 * @param undoButtonRequestState Determines whether the "Undo" button is currently enabled,
 * based on whether there are moves available in the game history.
 */
@Composable
private fun DrawButtons(undoButtonRequestState: Boolean, gameViewModel: GameViewModel)
{
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Button(
            onClick = {
                view.click()
                gameViewModel.newGame()
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
                gameViewModel.resetGame()
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
                gameViewModel.userRevertToPreviousMove()
            },
            enabled = undoButtonRequestState
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
 * Renders the interactive game board using a Canvas and handles user touch input.
 *
 * This composable draws the test tubes (columns) and the balls based on the current game state.
 * It detects tap gestures to determine which column a user is interacting with, allowing
 * balls to be "popped" from or "pushed" into tubes. It also coordinates ball movement
 * animations and the victory message overlay.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param updatedGameBoard A flag indicating if the board state has changed and requires a redraw.
 * @param popBall A flag indicating if a ball is currently being moved or "popped."
 * @param announceVictory A flag indicating if the victory animation should be displayed.
 */
@Composable
private fun DrawGameBoard(
    modifier: Modifier = Modifier,
    updatedGameBoard: Boolean,
    popBall: Boolean,
    announceVictory: Boolean,
    gameViewModel: GameViewModel, )
{
    Log.i(Global.DEBUG_PREFIX, "Draw board called. updateGameBoard = $updatedGameBoard")

    val columnLength = gBallRadius * 10
    val verticalMidpoint = (gVerticalSpacing * 2) + columnLength
    var drawBoardToggle by remember { mutableStateOf(true) }

    if (popBall) animatePopBallSetup(
        // TODO: Set-up to do ball movement animation
    )

    /**
     * textMeasurer is used to draw text on canvas.  This is used for animated victory message.
     */
    val textMeasurer = rememberTextMeasurer()
    val animatedVictoryMessage = remember { Animatable(initialValue = 0f) }
    if (announceVictory) {
        AnimateVictoryMessageSetup(animatedVictoryMessage, gameViewModel)
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
                            if (xCol > (Global.MAX_COLUMNS - 1)) {
                                xCol = (Global.MAX_COLUMNS - 1)
                            }

                            val middleColNum = (Global.MAX_COLUMNS / 2) - 1

                            val column = if (tapOffset.y > verticalMidpoint) {
                                xCol + (Global.MAX_COLUMNS / 2)
                            } else if ((tapOffset.y < verticalMidpoint) && (xCol >= middleColNum )) {
                                middleColNum
                            } else {
                                xCol
                            }

                            if (gameViewModel.hasFloatingBall()) {

                                // Check if we are pushing same ball back to its own column, essentially undoing the move.
                                if (column == gameViewModel.floatingBallColumn) {
                                    view.click()
                                    gameViewModel.userSelectColumnToPush(column)
                                }

                                // Only push if this is valid column to move to
                                if (gameViewModel.validColumnToMoveTo(column)) {
                                    view.click()
                                    gameViewModel.userSelectColumnToPush(column)
                                }
                            } else {
                                view.click()
                                gameViewModel.selectColumnToPop(column)
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
                        drawBall(this, curCol, curSlot, gameViewModel)
                    }
                }
                if (popBall) {
                    animatePopBallPerform(this, gameViewModel)
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
 * Sets up and triggers the victory message animation and accompanying sound effects.
 *
 * This composable uses a [LaunchedEffect] to run a sequence of events when the user wins:
 * 1. Resets the animation state.
 * 2. Animates the victory message scale/progress from 0 to 1 over 1500ms.
 * 3. Plays the victory audio clip in parallel with the animation.
 * 4. Updates the game mode to [GameUIState.GameMode.UpdatedGameBoard] once complete.
 * 5. Provides a brief delay before resetting the animation controller.
 *
 * @param animateCtl The [Animatable] instance used to control the animation's float value.
 */
@Composable
private fun AnimateVictoryMessageSetup(animateCtl: Animatable<Float, AnimationVector1D>, gameViewModel: GameViewModel)
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
                gameViewModel.setModeUpdateGameBoard()

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
 * Resets the victory message animation state.
 *
 * This function ensures that the animation is stopped and the progress is snapped back
 * to the initial value (0f), effectively hiding the victory message and preparing
 * the controller for its next use.
 *
 * @param animateCtl The [Animatable] object used to control the animation state of the victory message.
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
fun drawBall(drawScope: DrawScope, col: Int, slot: Int, gameViewModel: GameViewModel)
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
                color = gameViewModel.getBallColor(col, slot),
                radius = gBallRadius,
                center = Offset(startX, startY + yDistance(slot))
            )
        } else {
            drawCircle(
                color = colorList[gameViewModel.floatingBallColorInt],
                radius = gBallRadius,
                center = Offset(startX, startY )
            )
        }
    }
}

/**
 * Calculates the vertical offset for a ball within a column based on its slot index.
 *
 * This distance is measured from the top of the column to the center of the ball,
 * ensuring that balls are stacked from the bottom up.
 *
 * @param slot The index of the slot in the column (0 being the bottom-most slot).
 * @return The vertical Y-axis distance from the column's starting Y-coordinate.
 */
fun yDistance(slot: Int): Float
{
    val ballRadius = 65f
    val columnLength = ballRadius * 10
    return (columnLength - ballRadius - (slot * (2 * ballRadius)))
}

/********************* Animation Routine ************************************/


/**
 *  Setup to do the ball animation
 */
private fun animatePopBallSetup()
{
// TODO: Setup the ball animation specification
}

/**
 * Renders the floating ball at the top of its origin column.
 *
 * This function is called within the animation loop when a ball has been "popped"
 * from a column. It draws the ball in its floating position, visually indicating
 * to the user that it is selected and ready to be moved to a destination column.
 * After drawing, it updates the game state to `WaitingToPushBall`, signifying
 * that the system is now waiting for the user to select a target column.
 *
 * @param drawScope The canvas scope on which to draw the floating ball.
 */
private fun animatePopBallPerform(drawScope: DrawScope, gameViewModel: GameViewModel)
{
    drawBall(drawScope, gameViewModel.floatingBallColumn, -1, gameViewModel)
    gameViewModel.readyToPushBall()
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