package com.darblee.ballsort.ui.screens

import android.app.Activity
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
fun GameScreen(historyFile: File, modifier: Modifier = Modifier) {
    val activity = LocalOnBackPressedDispatcherOwner.current as? Activity
    BackPressHandler(onBackPressed = { activity?.finish() })

    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModel.factory(historyFile)
    )

    val gameUIState by gameViewModel.gameUIState.collectAsStateWithLifecycle()

    Log.i(Global.DEBUG_PREFIX, "Recompose - ${gameUIState.mode}")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawButtons(gameUIState.undoEnabled, gameViewModel)

        DrawGameBoard(Modifier, gameUIState.gridChange, gameUIState.popBall,
            gameUIState.announceVictory, gameViewModel, gameUIState)
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
    gameViewModel: GameViewModel,
    gameUIState: GameUIState, )
{
    Log.i(Global.DEBUG_PREFIX, "Draw board called. updateGameBoard = $updatedGameBoard")

    // Track gameUIState as a Compose State so the Canvas draw phase redraws
    // when the board changes. Without this, Compose skips the Canvas redraw
    // because gameViewModel (same object reference) doesn't signal changes.
    val currentGameUIState = rememberUpdatedState(gameUIState)

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

    if (popBall && updatedGameBoard) {
        LaunchedEffect(gameUIState) {
            gameViewModel.readyToPushBall()
        }
    }

    Box {
        val view = LocalView.current

        // Capture canvas size so layout values can be computed once per size change,
        // not on every frame draw during animations.
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val layoutValues = remember(canvasSize) {
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            val ws = w / ((Global.MAX_COLUMNS / 2) + 1)
            val br = minOf(h / 23f, ws * 0.45f)
            val vs = (h - 20f * br) / 3f
            val cl = br * 10f
            floatArrayOf(ws, br, vs, (vs * 2) + cl)
        }
        val widthSpacing = layoutValues[0]
        val ballRadius = layoutValues[1]
        val verticalSpacing = layoutValues[2]
        val verticalMidpoint = layoutValues[3]

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val column = getTapColumnIndex(tapOffset, widthSpacing, verticalMidpoint)

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
            // Read Compose State to register a draw-phase dependency.
            // This ensures the Canvas redraws when game state changes.
            // Suppress "Variable is never used" because we need the read to trigger redraw
            @Suppress("UNUSED_EXPRESSION")
            currentGameUIState.value

            val drawScope = this
            var startX: Float
            var startY: Float

            with (drawScope) {
                val columnLength = ballRadius * 10

                for (curCol in 0..< Global.MAX_COLUMNS) {
                    startY = if (curCol < (Global.MAX_COLUMNS / 2)) {
                        verticalSpacing
                    } else {
                        verticalMidpoint
                    }
                    startX = ((curCol % (Global.MAX_COLUMNS / 2)) + 1) * widthSpacing

                    drawLine(
                        color = Color.White,
                        start = Offset(startX, startY + (1.5F * ballRadius)),
                        end = Offset(startX, (startY + columnLength)),
                        strokeWidth = 10f
                    )

                    for (curSlot in 0..< Global.MAX_SLOT_PER_COLUMN) {
                        drawBall(this, curCol, curSlot, gameViewModel, ballRadius, widthSpacing, verticalSpacing)
                    }
                }
                if (popBall) {
                    animatePopBallPerform(this, gameViewModel, ballRadius, widthSpacing, verticalSpacing)
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
 * Maps a tap [offset] to a board column index (0 until [Global.MAX_COLUMNS]).
 *
 * The board is laid out in two rows of [Global.MAX_COLUMNS]/2 columns each.
 * Taps below [verticalMidpoint] map to the bottom row (indices shifted by MAX_COLUMNS/2);
 * taps above it map to the top row, clamped to the last valid top-row column.
 *
 * @param offset Raw tap position in canvas pixels.
 * @param widthSpacing Pixel width allocated per column.
 * @param verticalMidpoint Y-coordinate separating the two rows.
 * @return Column index in [0, MAX_COLUMNS).
 */
private fun getTapColumnIndex(offset: Offset, widthSpacing: Float, verticalMidpoint: Float): Int {
    val halfColumns = Global.MAX_COLUMNS / 2
    val xCol = ((offset.x - widthSpacing / 2) / widthSpacing).toInt().coerceIn(0, halfColumns - 1)
    return when {
        offset.y > verticalMidpoint -> xCol + halfColumns
        else -> xCol
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
fun drawBall(
    drawScope: DrawScope,
    col: Int,
    slot: Int,
    gameViewModel: GameViewModel,
    ballRadius: Float,
    widthSpacing: Float,
    verticalSpacing: Float
) {
    var startX: Float
    var startY: Float

    val columnLength = ballRadius * 10

    with (drawScope) {
        startY = if (col < (Global.MAX_COLUMNS / 2)) {
            verticalSpacing
        } else {
            (verticalSpacing * 2) + columnLength
        }
        startX = ((col % (Global.MAX_COLUMNS / 2)) + 1) * widthSpacing

        if (slot != -1) {
            drawCircle(
                color = gameViewModel.getBallColor(col, slot),
                radius = ballRadius,
                center = Offset(startX, startY + yDistance(slot, ballRadius))
            )
        } else {
            drawCircle(
                color = colorList[gameViewModel.floatingBallColorInt],
                radius = ballRadius,
                center = Offset(startX, startY + ballRadius * 0.5f)
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
fun yDistance(slot: Int, ballRadius: Float): Float
{
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
private fun animatePopBallPerform(
    drawScope: DrawScope,
    gameViewModel: GameViewModel,
    ballRadius: Float,
    widthSpacing: Float,
    verticalSpacing: Float
) {
    drawBall(drawScope, gameViewModel.floatingBallColumn, -1, gameViewModel, ballRadius, widthSpacing, verticalSpacing)
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