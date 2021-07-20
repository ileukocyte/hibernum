@file:JvmName("EventWaiter")
@file:Suppress("UNCHECKED_CAST", "UNUSED")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.commands.Command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener

import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors.newFixedThreadPool

import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.Message

// Context
private val waiterContextDispatcher = newFixedThreadPool(3).asCoroutineDispatcher()

object WaiterContext : CoroutineContext by waiterContextDispatcher, AutoCloseable by waiterContextDispatcher

// Process management functions
val JDA.processes get() = WaiterProcess.currentlyRunning.keys
val User.processes get() = jda.getUserProcesses(this)

inline fun waiterProcess(block: WaiterProcess.Builder.() -> Unit)  = WaiterProcess.Builder().apply(block)()

fun JDA.getProcessById(id: String) = processes.firstOrNull { it.id == id }

fun JDA.getProcessByEntitiesIds(usersIds: Set<Long>, channelId: Long) = processes.firstOrNull {
    it.users.containsAll(usersIds) && it.channel == channelId
}
fun JDA.getProcessByEntities(users: Set<User>, channel: MessageChannel) =
    getProcessByEntitiesIds(users.map { it.idLong }.toSet(), channel.idLong)

fun JDA.getProcessByEntitiesIds(userId: Long, channelId: Long) = getProcessByEntitiesIds(setOf(userId), channelId)
fun JDA.getProcessByEntities(user: User, channel: MessageChannel) = getProcessByEntities(setOf(user), channel)

fun JDA.getUserProcesses(user: User) = processes.filter { user.idLong in it.users }
fun JDA.getUserProcesses(userId: Long) = processes.filter { userId in it.users }

fun JDA.getProcessByMessage(message: Message) = getProcessByMessage(message.idLong)
fun JDA.getProcessByMessage(messageId: Long) = processes.firstOrNull { it.invoker == messageId }

fun WaiterProcess.kill(jda: JDA) = WaiterProcess.currentlyRunning[this]?.kill(jda, true)

/**
 * The unit of the bot's event waiter process management system containing the required data.
 *
 * @property users
 * A mutable list containing the ID's of the users being involved
 * @property channel
 * An ID of the channel the process is currently running in
 * @property command
 * The command the process was launched from or by
 * @property invoker
 * An ID of the message the process might have been launched from
 * @property id
 * The unique 4-digit identificational key of the process used in several contexts. **Strongly unrecommended to change!**
 * @property timeCreated
 * The date and time when the process was launched. **Strongly unrecommended to change!**
 *
 * @author Alexander Oksanich
 *
 * @see awaitEvent
 */
data class WaiterProcess(
    val users: MutableSet<Long>,
    val channel: Long,
    val command: Command?,
    val invoker: Long?,
    val id: String = "%04d".format((1..9999).filter {
        "%04d".format(it) !in currentlyRunning.keys.map { p -> p.id }
    }.random()),
    val timeCreated: OffsetDateTime = OffsetDateTime.now(ZoneId.of("Etc/GMT0")),
    var eventType: KClass<out GenericEvent>? = null
) {
    @DslMarker
    private annotation class WaiterDslMarker

    @WaiterDslMarker
    class Builder {
        val users = mutableSetOf<Long>()
        var channel = 0L
        var command: Command? = null
        var invoker: Long? = null

        operator fun invoke() = WaiterProcess(
            users = users,
            channel = channel,
            command = command,
            invoker = invoker
        )
    }

    companion object {
        @PublishedApi
        internal val currentlyRunning = mutableMapOf<WaiterProcess, AwaitableEventListener<*>>()
    }
}

class AwaitableEventListener<E : GenericEvent>(
    private val type: KClass<E>,
    private val deferred: CompletableDeferred<E?>,
    private val waiterProcess: WaiterProcess? = null,
    private val condition: suspend (E) -> Boolean
) : EventListener {
    init {
        waiterProcess?.let { WaiterProcess.currentlyRunning += it to this }
    }

    fun kill(jda: JDA, completeWithNull: Boolean = false) {
        waiterProcess?.let { WaiterProcess.currentlyRunning -= it }

        jda.removeEventListener(this)

        if (completeWithNull) deferred.complete(null)
    }

    override fun onEvent(event: GenericEvent) {
        if (event::class == type) {
            CoroutineScope(WaiterContext).launch {
                val casted = event as E
                if (condition(casted)) {
                    kill(casted.jda)
                    deferred.complete(casted)
                }
            }
        }
    }
}

/**
 * An extension that is intended to make awaiting of any JDA event while using the process management system possible.
 *
 * @param delay
 * A period throughout which an event is being awaited
 * @param unit
 * A time unit for the aforementioned parameter
 * @param waiterProcess
 * A process linked with the currently awaited event
 * @param condition
 * The filter accepting only proper events while awaiting them
 *
 * Returns a nullable (in case of the process being manually terminated) instance
 * of the needed event extracted from its [CompletableDeferred]
 *
 * Throws [TimeoutCancellationException] in case of no event being obtained
 *
 * @author Alexander Oksanich
 *
 * @see io.ileukocyte.hibernum.extensions.awaitConfirmationWithReactions
 * @see io.ileukocyte.hibernum.extensions.awaitMessage
 */
@OptIn(ExperimentalTime::class)
@Throws(TimeoutCancellationException::class)
suspend inline fun <reified E : GenericEvent> JDA.awaitEvent(
    delay: Long = -1,
    unit: DurationUnit = DurationUnit.MILLISECONDS,
    waiterProcess: WaiterProcess? = null,
    noinline condition: suspend (E) -> Boolean
): E? {
    waiterProcess?.eventType = E::class

    val deferred = CompletableDeferred<E?>()
    val listener = AwaitableEventListener(E::class, deferred, waiterProcess, condition)

    addEventListener(listener)

    return if (delay > 0) {
        try {
            withTimeout(unit.toMillis(delay)) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            waiterProcess?.let { WaiterProcess.currentlyRunning -= it }
            removeEventListener(listener)
            throw e
        }
    } else deferred.await()
}