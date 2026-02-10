package com.darblee.ballsort.utilities

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View

/**
 * Perform haptic feedback
 */
fun View.click() = run {
    this.let { this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    this.playSoundEffect(SoundEffectConstants.CLICK)
}
