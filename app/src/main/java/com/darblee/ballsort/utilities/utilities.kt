package com.darblee.ballsort.utilities

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View

/********************************* Singleton helper functions *************************************/

/**
 * SingletonHolder - This is used to pass parameter to Singleton class
 */
open class SingletonHolder<out T : Any, in A>(creator: (A) -> T) {

    private var creator: ((A) -> T)? = creator

    @Volatile
    private var instance: T? = null
    protected fun getInstanceInternal(arg: A): T {
        val checkInstance = instance
        if (checkInstance != null) return checkInstance
        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) checkInstanceAgain
            else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}

/**
 * If you need to pass only ONE argument to the constructor of the singleton class.
 * Make companion object extended from [SingleArgSingletonHolder] for best match.
 * Ex:
class AppRepository private constructor(private val db: Database) {
companion object : SingleArgSingletonHolder<AppRepository, Database>(::AppRepository)
}

 * Uses:
val appRepository =  AppRepository.getInstance(db)
 */
open class SingleArgSingletonHolder<out T : Any, in A>(creator: (A) -> T) :
    SingletonHolder<T, A>(creator) {
    fun getInstance(arg: A): T = getInstanceInternal(arg)
}

/**
 * Perform haptic feedback
 */
fun View.click() = run {
    this.let { this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    this.playSoundEffect(SoundEffectConstants.CLICK)
}
