package com.darblee.ballsort.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val Teal = Color(0xFF008080)
val Orange = Color(0xFFe28743)
val Brown = Color(0xFF8B4513)
val Indigo = Color(0xFF4B0082)
val Lime = Color(0xFFBFFF00)
val Maroon = Color(0xFF800000)

/**
 * Palette used to render balls. Index 0 ([Color.Unspecified]) represents an empty slot;
 * indices 1..N map to ball color IDs.
 *
 * The board only needs `MAX_COLUMNS - 2` distinct colors, but the list is kept
 * intentionally larger than that so there is headroom if [Global.MAX_COLUMNS] is
 * increased. Callers should still look colors up defensively (e.g. [List.getOrElse])
 * to tolerate stray color IDs from persisted snapshots.
 */
val colorList = mutableListOf<Color>(
    Color.Unspecified,
    Color.Red,
    Color.Blue,
    Color.Yellow,
    Color.Green,
    Color.Magenta,
    Color.Cyan,
    Color.LightGray,
    Pink40,
    Teal,
    Orange,
    Brown,
    Indigo,
    Lime,
    Maroon
)
