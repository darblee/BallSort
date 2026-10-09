package com.darblee.ballsort.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import com.darblee.ballsort.ui.theme.BallSortTheme
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
import com.darblee.ballsort.gSoundOn
import com.darblee.ballsort.ui.GameUIState
import com.darblee.ballsort.ui.theme.colorList
import com.darblee.ballsort.utilities.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * The main entry point for the Ball Sort game screen.
 *
 * This composable manages the game state and UI layout, coordinating between the
 * [GameViewModel] and the visual components. It handles different game modes
 * (Initialization, New Game, Reset, Victory, etc.), responds to back press events,
 * and renders both the control buttons and the interactive game board.
 *
 * In portrait, the buttons sit above the board (stacked in a [Column]). In landscape,
 * the buttons move to a column on the left occupying 1/4 of the screen width, with the
 * game board filling the remaining 3/4 on the right (a [Row]), so the board stays fully
 * visible without competing with the buttons for vertical space.
 *
 * @param modifier The modifier to be applied to the root layout.
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

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            DrawButtons(
                gameUIState.undoEnabled,
                gameViewModel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                isLandscape = true
            )
            DrawGameBoard(
                Modifier.weight(3f).fillMaxHeight(),
                gameUIState.gridChange, gameUIState.popBall,
                gameUIState.announceVictory, gameViewModel, gameUIState
            )
        }
    } else {
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
}

/**
 * Fixed width applied to every action button (New Game / Restart / Undo) so they line
 * up evenly instead of each hugging its own label's width. Sized to comfortably fit the
 * widest label, "New Game", with its icon.
 */
private val ActionButtonWidth = 148.dp

/**
 * Size of the sound on/off icon. The action buttons render at Material3's default ~48.dp
 * height, so this is set well above 3/4 of that (36.dp) to keep the icon from looking tiny
 * next to them -- it previously used `SwitchDefaults.IconSize * 1.5f`, but `IconSize` is only
 * 16.dp, so that worked out to a mere 24.dp.
 */
private val SoundIconSize = 40.dp

/**
 * Renders the control buttons for the game, including options to start a new game,
 * restart the current level, undo the last move, and toggle sound.
 *
 * In portrait, the action buttons are laid out in a horizontal row with the sound
 * toggle centered beneath them. In landscape, where this composable only has 1/4 of
 * the screen width to work with (see [GameScreen]), they're stacked vertically instead
 * so none of the pill-shaped buttons get squeezed or clipped.
 *
 * @param undoButtonRequestState Determines whether the "Undo" button is currently enabled,
 * based on whether there are moves available in the game history.
 * @param modifier The modifier to be applied to the root layout.
 * @param isLandscape Whether to use the narrow, vertically-stacked landscape layout.
 */
@Composable
private fun DrawButtons(
    undoButtonRequestState: Boolean,
    gameViewModel: GameViewModel,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
)
{
    val view = LocalView.current
    var soundOn by remember { mutableStateOf(gSoundOn) }

    val actionButtons: @Composable () -> Unit = {
        ThreeDButton(
            onClick = {
                view.click()
                gameViewModel.newGame()
            },
            modifier = Modifier.width(ActionButtonWidth)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "New game",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
            Text("New Game", style = MaterialTheme.typography.titleSmall)
        }
        ThreeDButton(
            onClick = {
                view.click()
                gameViewModel.resetGame()
            },
            modifier = Modifier.width(ActionButtonWidth)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Restart",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
            Text("Restart", style = MaterialTheme.typography.titleSmall)
        }
        ThreeDButton(
            onClick = {
                view.click()
                gameViewModel.userRevertToPreviousMove()
            },
            modifier = Modifier.width(ActionButtonWidth),
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

    val soundToggle: @Composable () -> Unit = {
        IconButton(
            onClick = {
                view.click()
                soundOn = !soundOn
                gSoundOn = soundOn
            }
        ) {
            Icon(
                imageVector = if (soundOn) SoundOnIcon else SoundMuteIcon,
                contentDescription = if (soundOn) "Sound on, tap to mute" else "Sound muted, tap to unmute",
                modifier = Modifier.size(SoundIconSize)
            )
        }
    }

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actionButtons()
            soundToggle()
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actionButtons()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                soundToggle()
            }
        }
    }
}

/**
 * Speaker icon (cone only, no waves) used as the shared base shape for [SoundOnIcon]
 * and [SoundMuteIcon].
 */
private fun ImageVector.Builder.speakerCone() {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 9f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(4f)
        lineToRelative(5f, 5f)
        verticalLineTo(4f)
        lineTo(7f, 9f)
        horizontalLineTo(3f)
        close()
    }
}

/** Speaker-with-sound-waves icon shown when the game's sound effects are enabled. */
private val SoundOnIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SoundOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        speakerCone()
        // Two nested arcs bulging right of the cone, read as sound waves.
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14f, 8f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14f, y1 = 16f)
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17f, 5f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17f, y1 = 19f)
        }
    }.build()
}

/** Speaker-with-X icon shown when the game's sound effects are muted. */
private val SoundMuteIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SoundMute",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        speakerCone()
        // A small "X" where the sound waves would be, the common "muted" cue.
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14f, 8.5f)
            lineTo(21f, 15.5f)
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(21f, 8.5f)
            lineTo(14f, 15.5f)
        }
    }.build()
}

/**
 * A [Button] styled to look like a raised, glossy 3-D control instead of Material's flat
 * default.
 *
 * The convex look comes from a vertical gradient fill (light top edge fading to a darker
 * bottom edge, as if lit from above), a drop shadow for depth, and a thin bright top border
 * to catch the "light." This mirrors the glossy-sphere shading used for the balls on the
 * game board elsewhere in this file, so the controls and the board read as one visual style.
 * When [enabled] is false the button is flattened: no shadow and a desaturated, duller fill.
 *
 * @param onClick Invoked when the button is tapped.
 * @param modifier Modifier applied to the underlying [Button].
 * @param enabled Whether the button can be interacted with; also drives the raised/flat look.
 * @param content Button content, typically an [Icon] plus a [Text] label.
 */
@Composable
private fun ThreeDButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val shape = ButtonDefaults.shape
    val baseColor = MaterialTheme.colorScheme.primary.let {
        if (enabled) it else lerp(it, MaterialTheme.colorScheme.surface, 0.6f)
    }
    val lightEdge = lerp(baseColor, Color.White, 0.45f)
    val darkEdge = lerp(baseColor, Color.Black, 0.35f)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.75f), Color.Transparent)
            )
        ),
        modifier = modifier
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = shape,
                clip = false
            )
            .background(
                brush = Brush.verticalGradient(listOf(lightEdge, baseColor, darkEdge)),
                shape = shape
            ),
        content = content
    )
}

/**
 * Holds all layout scalars and pre-computed tube origins for a given canvas size.
 *
 * Computed once in [DrawGameBoard] whenever the canvas size changes (via [remember]),
 * then passed to drawing and hit-test helpers so they never re-derive these values.
 *
 * @property ballRadius Radius of each ball in pixels.
 * @property widthSpacing Pixel width allocated per column.
 * @property verticalSpacing Y-offset of the top row of tubes.
 * @property verticalMidpoint Y-offset of the bottom row of tubes (also the row divider for tap detection).
 * @property columnLength Height of one tube in pixels (ballRadius * 10).
 * @property tubeOrigins Pre-computed (x, y) origin for each column's tube, indexed by column number.
 */
private data class BoardLayout(
    val ballRadius: Float,
    val widthSpacing: Float,
    val verticalSpacing: Float,
    val verticalMidpoint: Float,
    val columnLength: Float,
    val tubeOrigins: List<Offset>
)

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
    // This mount gate bounds the victory effect's lifecycle. `announceVictory` is true
    // only while mode == WonGame (see GameViewModel.setMode), and reaching WonGame always
    // passes through another mode first, so each victory is exactly one false->true->false
    // cycle. That means AnimateVictoryMessageSetup enters composition once per victory and
    // its LaunchedEffect(Unit) restarts on each entry -- do not weaken this gate, or that
    // "run once per victory" guarantee breaks.
    if (announceVictory) {
        AnimateVictoryMessageSetup(animatedVictoryMessage, gameViewModel)
    } else {
        AnimateVictoryMessageReset(animatedVictoryMessage)
    }

    LaunchedEffect(popBall, updatedGameBoard, gameUIState) {
        if (popBall && updatedGameBoard) gameViewModel.readyToPushBall()
    }

    Box(modifier = modifier) {
        val view = LocalView.current

        // Recompute layout once per canvas size change, not on every draw frame.
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val layout = remember(canvasSize) {
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            val ws = w / ((Global.MAX_COLUMNS / 2) + 1)
            val br = minOf(h / 23f, ws * 0.45f)
            val vs = (h - 20f * br) / 3f
            val cl = br * 10f
            val midpoint = (vs * 2) + cl
            BoardLayout(
                ballRadius = br,
                widthSpacing = ws,
                verticalSpacing = vs,
                verticalMidpoint = midpoint,
                columnLength = cl,
                tubeOrigins = List(Global.MAX_COLUMNS) { col ->
                    val x = ((col % (Global.MAX_COLUMNS / 2)) + 1) * ws
                    val y = if (col < Global.MAX_COLUMNS / 2) vs else midpoint
                    Offset(x, y)
                }
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val column = getTapColumnIndex(tapOffset, layout)

                            if (gameViewModel.hasFloatingBall()) {

                                // Check if we are pushing same ball back to its own column, essentially undoing the move.
                                if (column == gameViewModel.floatingBallColumn) {
                                    view.click()
                                    gameViewModel.userSelectColumnToPush(column)

                                // Only push if this is valid column to move to
                                } else if (gameViewModel.validColumnToMoveTo(column)) {
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
            // Register a draw-phase dependency on the game state. stateId is bumped
            // by every setMode() transition (see GameUIState.stateId), so reading it
            // here forces the Canvas to redraw whenever the board changes — including
            // "New Game", whose randomized board lives in a non-observable array.
            // Suppress "Variable is never used" because we need the read to trigger redraw.
            @Suppress("UNUSED_EXPRESSION")
            currentGameUIState.value.stateId

            for (curCol in 0 until Global.MAX_COLUMNS) {
                val origin = layout.tubeOrigins[curCol]
                drawPole(
                    drawScope = this,
                    topTip = Offset(origin.x, origin.y + (1.5F * layout.ballRadius)),
                    bottomEnd = Offset(origin.x, origin.y + layout.columnLength),
                    poleWidth = layout.ballRadius * 0.22f
                )
                for (curSlot in 0 until Global.MAX_SLOT_PER_COLUMN) {
                    drawBall(this, curCol, curSlot, gameViewModel, layout)
                }
            }
            if (popBall) {
                animatePopBallPerform(this, gameViewModel, layout)
            }

            if (announceVictory) {
                animateVictoryMsgInvoke(this, animatedVictoryMessage, textMeasurer)
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
 * 4. Holds the fully visible message on screen for an additional 2 seconds.
 * 5. Updates the game mode to [GameUIState.GameMode.UpdatedGameBoard], which hides the message.
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
                // Pause for 0.5 second to allow user to see victory message before it disappears
                delay(500.milliseconds)

                gameViewModel.setModeUpdateGameBoard()

                animateCtl.snapTo(0f)
                animateCtl.stop()
            }  // launch

            launch(Dispatchers.Main) {
                if (gSoundOn) {
                    gAudio_victory.start()
                }
            }
        }
    }
}

/**
 * Maps a tap [offset] to a board column index (0 until [Global.MAX_COLUMNS]).
 *
 * The board is laid out in two rows of [Global.MAX_COLUMNS]/2 columns each.
 * Taps below [BoardLayout.verticalMidpoint] map to the bottom row (indices shifted by MAX_COLUMNS/2);
 * taps above it map to the top row, clamped to the last valid top-row column.
 *
 * @param offset Raw tap position in canvas pixels.
 * @param layout Pre-computed board layout providing widthSpacing and verticalMidpoint.
 * @return Column index in [0, MAX_COLUMNS).
 */
private fun getTapColumnIndex(offset: Offset, layout: BoardLayout): Int {
    val halfColumns = Global.MAX_COLUMNS / 2
    val xCol = ((offset.x - layout.widthSpacing / 2) / layout.widthSpacing).toInt().coerceIn(0, halfColumns - 1)
    return when {
        offset.y > layout.verticalMidpoint -> xCol + halfColumns
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
        val basePosition = Offset(
            x = (canvasWidth - textSize.width) * 0.5f, // in center
            y = (canvasHeight * 0.25f)
        )

        // Canvas drawText has no native stroke style, so fake a bold outline by
        // drawing the text in black at offsets all around the fill position first.
        val outlineWidth = (animatedTextSize * 0.06f).coerceAtLeast(1f)
        val outlineStyle = textStyle.copy(color = Color.Black)
        val outlineOffsets = listOf(
            Offset(-outlineWidth, -outlineWidth), Offset(0f, -outlineWidth), Offset(outlineWidth, -outlineWidth),
            Offset(-outlineWidth, 0f),                                       Offset(outlineWidth, 0f),
            Offset(-outlineWidth, outlineWidth),  Offset(0f, outlineWidth),  Offset(outlineWidth, outlineWidth)
        )
        outlineOffsets.forEach { offset ->
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                topLeft = basePosition + offset,
                style = outlineStyle
            )
        }

        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = basePosition,
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
private fun drawBall(
    drawScope: DrawScope,
    col: Int,
    slot: Int,
    gameViewModel: GameViewModel,
    layout: BoardLayout
) {
    val origin = layout.tubeOrigins[col]
    if (slot != -1) {
        val center = Offset(origin.x, origin.y + yDistance(slot, layout.ballRadius))
        drawSphere(drawScope, center, layout.ballRadius, gameViewModel.getBallColor(col, slot))
    } else {
        val center = Offset(origin.x, origin.y + layout.ballRadius * 0.5f)
        val color = colorList.getOrElse(gameViewModel.floatingBallColorInt) { Color.Unspecified }
        drawSphere(drawScope, center, layout.ballRadius, color)
    }
}

/**
 * Draws a shaded sphere to give a ball a 3D appearance.
 *
 * The illusion is built from three layers: a base fill in [baseColor], a radial
 * gradient shading the surface from a lighter top-left toward a darker bottom-right
 * (simulating a light source in the upper-left), and a small soft specular
 * highlight for the glossy reflection.
 *
 * @param drawScope Canvas scope to draw on.
 * @param center Center of the ball.
 * @param radius Radius of the ball.
 * @param baseColor The ball's color.
 */
private fun drawSphere(
    drawScope: DrawScope,
    center: Offset,
    radius: Float,
    baseColor: Color
) {
    if (baseColor == Color.Unspecified || radius <= 0f) return

    val light = lerp(baseColor, Color.White, 0.55f)
    val dark = lerp(baseColor, Color.Black, 0.45f)
    // Light source sits toward the upper-left of the ball.
    val lightOffset = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)

    with (drawScope) {
        // Base fill.
        drawCircle(color = baseColor, radius = radius, center = center)

        // Surface shading: bright near the light, falling off to the dark edge.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(light, baseColor, dark),
                center = lightOffset,
                radius = radius * 1.5f
            ),
            radius = radius,
            center = center
        )

        // Specular highlight (glossy reflection).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                center = lightOffset,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = lightOffset
        )
    }
}

/**
 * Draws the vertical spindle balls are stacked on as a 3-D looking white pole with a
 * sharp point at its top.
 *
 * The shaft is shaded with a left-to-right gradient (dark-light-dark) to read as a
 * rounded cylinder, with a thin specular highlight running down its length. The tip
 * is a narrow triangle in the same gradient so the shading continues unbroken into
 * the point.
 *
 * @param drawScope Canvas scope to draw on.
 * @param topTip Apex of the pole's pointed tip (top of the column).
 * @param bottomEnd Base of the pole, anchored at the bottom of the tube.
 * @param poleWidth Width (diameter) of the pole's shaft.
 */
private fun drawPole(
    drawScope: DrawScope,
    topTip: Offset,
    bottomEnd: Offset,
    poleWidth: Float
) {
    val halfWidth = poleWidth / 2f
    val tipLength = poleWidth * 3f
    val shaftTopY = topTip.y + tipLength
    val x = topTip.x

    val shadeBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF8A8A8A), Color.White, Color(0xFFB0B0B0)),
        start = Offset(x - halfWidth, 0f),
        end = Offset(x + halfWidth, 0f)
    )

    with(drawScope) {
        // Shaft.
        drawRect(
            brush = shadeBrush,
            topLeft = Offset(x - halfWidth, shaftTopY),
            size = Size(poleWidth, bottomEnd.y - shaftTopY)
        )

        // Sharp pointed tip, shaded the same way so it reads as one continuous spike.
        val tip = Path().apply {
            moveTo(x, topTip.y)
            lineTo(x - halfWidth, shaftTopY)
            lineTo(x + halfWidth, shaftTopY)
            close()
        }
        drawPath(path = tip, brush = shadeBrush)

        // Thin specular highlight down the shaft for extra roundness.
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(x - halfWidth * 0.25f, shaftTopY),
            end = Offset(x - halfWidth * 0.25f, bottomEnd.y),
            strokeWidth = (poleWidth * 0.2f).coerceAtLeast(1f),
            cap = StrokeCap.Round
        )
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
    layout: BoardLayout
) {
    drawBall(drawScope, gameViewModel.floatingBallColumn, -1, gameViewModel, layout)
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

/********************* Previews ************************************/

@Preview(name = "Buttons – Undo disabled", showBackground = true)
@Composable
private fun DrawButtonsUndoDisabledPreview() {
    val context = LocalContext.current
    val viewModel = remember { GameViewModel(File(context.cacheDir, "preview_hist.txt")) }
    BallSortTheme {
        DrawButtons(undoButtonRequestState = false, gameViewModel = viewModel)
    }
}

@Preview(name = "Buttons – Undo enabled", showBackground = true)
@Composable
private fun DrawButtonsUndoEnabledPreview() {
    val context = LocalContext.current
    val viewModel = remember { GameViewModel(File(context.cacheDir, "preview_hist.txt")) }
    BallSortTheme {
        DrawButtons(undoButtonRequestState = true, gameViewModel = viewModel)
    }
}

@Preview(name = "GameScreen – Light", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GameScreenLightPreview() {
    val historyFile = File(LocalContext.current.cacheDir, "preview_hist.txt")
    BallSortTheme(darkTheme = false) {
        GameScreen(historyFile = historyFile)
    }
}

@Preview(name = "GameScreen – Dark", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GameScreenDarkPreview() {
    val historyFile = File(LocalContext.current.cacheDir, "preview_hist.txt")
    BallSortTheme(darkTheme = true) {
        GameScreen(historyFile = historyFile)
    }
}