package com.darblee.ballsort.domain

typealias  RootError = Error

sealed interface Result<out D, out E: RootError> {
    data class Success<out D, out E: RootError>(val  data: D): Result<D, E>
    data class Error<out D, out  E: RootError>(val error: E): Result<D, E>
}

/**
 * Possible error from findFreeSpaceCount routine
 *
 * @property NO_SPACE There is space available to move
 */
enum class FindFreeSpaceCountError: RootError {
    NO_SPACE,
}