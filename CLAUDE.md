# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build                    # Build the project
./gradlew installDebug             # Install debug APK to connected device/emulator
./gradlew test                     # Run unit tests
./gradlew connectedAndroidTest     # Run instrumented tests on device/emulator
./gradlew test --tests "com.darblee.ballsort.ExampleUnitTest"  # Run a single test class
```

- AGP 9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.01
- compileSdk 36, minSdk 34, targetSdk 35, Java 11
- Version catalog in `gradle/libs.versions.toml`

## Architecture

Single-module Android app (`app/`) using **MVVM** with Jetpack Compose.

**Package layout** (`app/src/main/java/com/darblee/ballsort/`):

| Package | Role |
|---------|------|
| root | `MainActivity`, `Constants.kt` (global constants & `gGameViewModel` singleton) |
| `domain/model` | `GameViewModel` — all game logic, state, persistence |
| `ui` | `GameUIState` sealed class (game mode states) |
| `ui/screens` | `GameScreen.kt` — sole screen, Canvas-based rendering |
| `ui/theme` | Material3 theme (Color, Theme, Type) |
| `utilities` | Singleton holder pattern, haptic/audio helpers |

**Data flow**: `GameViewModel` exposes `StateFlow<GameUIState>` consumed by `GameScreen` via `collectAsStateWithLifecycle()`. The ViewModel owns the game board (`Array<Array<Int>>`, 12 columns x 4 slots), move history, and file I/O.

**Persistence**: JSON files in `filesDir` — board state and `MainHistory.txt` (move history for undo/redo, defined as `GAME_HISTORY_FILENAME` in `Global`). Uses kotlinx.serialization.

**Single-screen app**: No navigation graphs. `MainActivity` renders `GameScreen` directly inside a `Scaffold`. The navigation-compose dependency is included but unused.

## Game Domain

- Board: 12 columns, 4 slots each. Slot values are color IDs (0 = empty, 1–10 = colors).
- Win condition: every column is either empty or filled with 4 balls of the same color.
- Floating ball: a ball "popped" from a column, waiting to be pushed onto a valid target column (matching top color or empty).
- Undo/redo via `_moveHistory` (list of board snapshots persisted to JSON).

## Key Globals

- `gGameViewModel` — late-init global ViewModel instance (initialized in `GameScreen`)
- `gAudio_victory` — MediaPlayer for victory sound
- `Global` object — constants: `MAX_COLUMNS=12`, `MAX_SLOT_PER_COLUMN=4`, `GAME_HISTORY_FILENAME`, `DEBUG_PREFIX`

## Conventions

- Kotlin with Jetpack Compose (Canvas-based custom drawing for the game board)
- Coroutines with `viewModelScope` for async work; `Dispatchers.IO` for file operations
- Portrait-only orientation enforced in `MainActivity`
- KDoc documentation on public composables and ViewModel methods
