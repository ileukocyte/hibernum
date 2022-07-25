@file:JvmName("RestActionExtensions")
package io.ileukocyte.hibernum.extensions

import java.util.concurrent.TimeUnit

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

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
suspend fun <T> RestAction<T>.awaitAfter(
    delay: Long,
    unit: TimeUnit,
): T {
    delay(unit.toMillis(delay))

    return await()
}

/**
 * Suspends the current coroutine while submitting the request
 *
 * @return the response value
 */
suspend fun <T> Task<T>.await() = suspendCancellableCoroutine<T> { continuation ->
    continuation.invokeOnCancellation { cancel() }
    onSuccess { continuation.resume(it) }
    onError { continuation.resumeWithException(it) }
}