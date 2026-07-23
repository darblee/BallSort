package com.darblee.ballsort.utilities

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import com.darblee.ballsort.gSoundOn

/**
 * Perform haptic feedback and, if sound is enabled, the click sound effect.
 */
fun View.click() = run {
    this.let { this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    if (gSoundOn) {
        this.playSoundEffect(SoundEffectConstants.CLICK)
    }
}
