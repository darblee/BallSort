package com.darblee.ballsort

import com.darblee.ballsort.domain.model.GameViewModel

lateinit var gGameViewModel : GameViewModel

internal object Global {
    const val DEBUG_PREFIX = "BallSort"
    const val MAX_COLUMNS = 14
    const val MAX_BALL_PER_COLUMN = 4
    const val GAME_BOARD_FILENAME = "MainBoard.txt"
    const val GAME_HISTORY_FILENAME = "MainHistory.txt"
}