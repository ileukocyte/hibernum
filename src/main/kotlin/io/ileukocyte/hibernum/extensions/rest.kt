@file:JvmName("RestActionExtensions")
package io.ileukocyte.hibernum.extensions

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay

import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.concurrent.Task

/**
 * Suspends the current coroutine while submitting the request
 *
 * @return the response value
 */
suspend fun <T> RestAction<T>.await() = suspendCoroutine<T> {
    queue(
        { success -> it.resume(success) },
        { failure -> it.resumeWithException(failure) },
    )
}

/**
 * Suspends the current coroutine for the specified delay while submitting the request
 * and calls [RestAction#await()][io.ileukocyte.hibernum.extensions.await] once the delay is reached
 *
 * @param delay
 * A period after which the action must be completed
 * @param unit
 * A time unit for the aforementioned parameter
 *
 * @return the response value
 */
@OptIn(ExperimentalTime::class)
suspend fun <T> RestAction<T>.awaitAfter(
    delay: Long,
    unit: DurationUnit
): T {
    delay(unit.toMillis(delay))

    return await()
}

/**
 * Suspends the current coroutine while submitting the request
 *
 * @return the response value
 */
suspend fun <T> Task<T>.await() = suspendCoroutine<T> { c ->
    this
        .onSuccess { c.resume(it) }
        .onError { c.resumeWithException(it) }
}