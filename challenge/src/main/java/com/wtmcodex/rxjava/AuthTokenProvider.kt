package com.wtmcodex.rxjava


import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.TimeUnit

class AuthTokenProvider(

    /**
     * Scheduler used for background operations. Execute time relevant operations on this one,
     * so we can use [io.reactivex.rxjava3.schedulers.TestScheduler] within unit tests.
     */
    private val computationScheduler: Scheduler,

    /**
     * Single to be observed in order to get a new token.
     */
    private val refreshAuthToken: Single<AuthToken>,

    /**
     * Observable for the login state of the user. Will emit true, if he is logged in.
     */
    private val isLoggedInObservable: Observable<Boolean>,

    /**
     * Function that returns you the current time, whenever you need it. Please use this whenever you check the
     * current time, so we can manipulate time in unit tests.
     */
    private val currentTime: () -> ZonedDateTime,


    ) {

    private var cacheToken: AuthToken? = null
    private var subscribeCount: Int = 0

    private val mResultObservable: BehaviorSubject<String> = BehaviorSubject.create()

    private val resultObservable: Observable<String> = mResultObservable


    /**
     * @return the observable auth token as a string
     */
    fun observeToken(): Observable<String> {


        subscribeCount++


        isLoggedInObservable.subscribe { isLoggedIn ->
            if (isLoggedIn) {

                //Logged in


                /**
                 * Gets the token from cache if not expired
                 * Else clears cache and fetches new token
                 */
                if (cacheToken != null) {

                    if (cacheToken!!.isValid(currentTime)) {
                        mResultObservable.onNext(cacheToken!!.token)
                    } else {
                        clearTokenCache()
                        fetchNewToken()
                    }

                } else {

                    /**
                     * Checks no more than one subscriber can call token fetch at one time to prevent multiple calls
                     */

                    if (subscribeCount < 2) {
                        fetchNewToken()
                    }
                }


            } else {
                //Logged out
                clearTokenCache()
            }
        }

        mResultObservable.doOnDispose { computationScheduler.shutdown() }

        return resultObservable
    }

    /**
     * Fetches new token and updates cache
     * In case of Error fetches the token again
     */
    private fun fetchNewToken() {
        refreshAuthToken.subscribe({ newAuthToken ->
            cacheToken = newAuthToken
            scheduleTokenExpiry()
            mResultObservable.onNext(cacheToken!!.token)
        }, {
            fetchNewToken()
        })
    }

    /**
     * Initialize Scheduler to be run after the token has expired
     */
    private fun scheduleTokenExpiry() {
        computationScheduler.scheduleDirect(
            {
                if (mResultObservable.hasObservers()) {

                    isLoggedInObservable.subscribe { isLoggedIn ->

                        if (isLoggedIn)
                            fetchNewToken()
                        else
                            clearTokenCache()

                    }
                }

            },
            cacheToken!!.millisUntilInvalid(currentTime),
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Clears the token cache
     */
    private fun clearTokenCache() {
        //Reset cache
        cacheToken = null
    }

}