package com.darblee.ballsort

import com.darblee.ballsort.domain.model.GameViewModel

lateinit var gGameViewModel : GameViewModel

internal object Global {
    const val DEBUG_PREFIX = "BallSort-debug"
    const val MAX_COLUMNS = 12
    const val MAX_SLOT_PER_COLUMN = 4
    const val GAME_BOARD_FILENAME = "MainBoard.txt"
    const val GAME_HISTORY_FILENAME = "MainHistory.txt"
}