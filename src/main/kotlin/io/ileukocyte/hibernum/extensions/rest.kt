package io.ileukocyte.hibernum.extensions

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay

import net.dv8tion.jda.api.requests.RestAction

import java.util.concurrent.TimeUnit

/**
 * Suspends the current coroutine while submitting the request
 *
 * Returns the response value
 */
suspend fun <T> RestAction<T>.await() = suspendCoroutine<T> {
    queue(
        { success -> it.resume(success) },
        { failure -> it.resumeWithException(failure) }
    )
}

/**
 * Suspends the current coroutine for the specified delay while submitting the request
 * and calls [RestAction#await()][io.ileukocyte.hibernum.extensions.await] once the delay is reached
 *
 * Returns the response value
 */
suspend fun <T> RestAction<T>.awaitAfter(
    delay: Long,
    unit: TimeUnit
): T {
    delay(unit.toMillis(delay))
    return await()
}