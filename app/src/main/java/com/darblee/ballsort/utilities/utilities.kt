package com.darblee.ballsort.utilities

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
 * If you need to pass TWO arguments to the constructor of the singleton class.
 * Extended from [PairArgsSingletonHolder] for best match.
 * Ex:
class AppRepository private constructor(private val db: Database, private val apiService: ApiService) {
companion object : PairArgsSingletonHolder<AppRepository, Database, ApiService>(::AppRepository)
}
 *
 * Uses:
val appRepository =  AppRepository.getInstance(db, apiService)
 */
open class PairArgsSingletonHolder<out T : Any, in A, in B>(creator: (A, B) -> T) :
    SingletonHolder<T, Pair<A, B>>(creator = { (a, b) -> creator(a, b) }) {
    fun getInstance(arg1: A, arg2: B) = getInstanceInternal(Pair(arg1, arg2))
}
