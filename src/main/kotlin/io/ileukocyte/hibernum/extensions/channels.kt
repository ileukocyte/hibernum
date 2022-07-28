@file:JvmName("ChannelExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.builders.KMessageBuilder
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.builders.buildMessage
import io.ileukocyte.hibernum.commands.GenericCommand
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.ActionComponent

inline fun MessageChannel.sendMessage(block: KMessageBuilder.() -> Unit) =
    sendMessage(buildMessage(block = block))

inline fun MessageChannel.sendEmbed(block: KEmbedBuilder.() -> Unit) =
    sendMessageEmbeds(buildEmbed(block))

fun MessageChannel.sendSuccess(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    sendMessageEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun MessageChannel.sendFailure(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    sendMessageEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun MessageChannel.sendConfirmation(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    sendMessageEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun MessageChannel.sendWarning(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    sendMessageEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))

fun MessageChannel.sendActionRow(vararg components: ActionComponent) =
    sendMessage(ZERO_WIDTH_SPACE).setActionRow(*components)

/**
 * The extension bringing a faster way to obtain an awaited message rather than using raw event receiving.
 *
 * @receiver The [MessageChannel] in which the message is going to be awaited
 *
 * @param authors
 * A set of users who the message is being received from
 * @param processCommand
 * The default command used as a process property
 * @param invokingMessage
 * The message that invokes the awaiting process
 * @param delay
 * A period throughout which the message must be received
 * @param unit
 * A time unit for the aforementioned parameter
 * @param processId
 * A custom process ID in case it is necessary to preserve the same ID throughout
 * repetitive message awaiting sessions
 * @param predicate
 * The additional filter accepting only proper events while awaiting them
 *
 * @return a nullable (in case of the process being manually terminated) [Message][net.dv8tion.jda.api.entities.Message] entity
 * received via event awaiting
 *
 * @throws [TimeoutCancellationException] in case of no event being obtained
 *
 * @author Alexander Oksanich
 *
 * @see io.ileukocyte.hibernum.utils.awaitEvent
 */
@Throws(TimeoutCancellationException::class)
suspend inline fun MessageChannel.awaitMessage(
    authors: Set<User>,
    processCommand: GenericCommand? = null,
    invokingMessage: Message? = null,
    delay: Long = 5,
    unit: TimeUnit = TimeUnit.MINUTES,
    processId: Int? = null,
    noinline predicate: (MessageReceivedEvent) -> Boolean = { true },
) = jda.awaitEvent<MessageReceivedEvent>(
    delay = delay,
    unit = unit,
    waiterProcess = waiterProcess {
        users += authors.map { it.idLong }
        channel = idLong
        command = processCommand
        invoker = invokingMessage?.idLong
        id = processId
    },
) { it.author in authors && it.channel.idLong == idLong && predicate(it) }?.message

/**
 * The extension bringing a faster way to obtain an awaited message rather than using raw event receiving.
 *
 * @receiver The [MessageChannel] in which the message is going to be awaited
 *
 * @param author
 * The user who the message is being received from
 * @param processCommand
 * The default command used as a process property
 * @param invokingMessage
 * The message that invokes the awaiting process
 * @param delay
 * A period throughout which the message must be received
 * @param unit
 * A time unit for the aforementioned parameter
 * @param predicate
 * The additional filter accepting only proper events while awaiting them
 *
 * @return a nullable (in case of the process being manually terminated) [Message][net.dv8tion.jda.api.entities.Message] entity
 * received via event awaiting
 *
 * @throws [TimeoutCancellationException] in case of no event being obtained
 *
 * @author Alexander Oksanich
 *
 * @see io.ileukocyte.hibernum.utils.awaitEvent
 */
@Throws(TimeoutCancellationException::class)
suspend inline fun MessageChannel.awaitMessage(
    author: User,
    processCommand: GenericCommand? = null,
    invokingMessage: Message? = null,
    delay: Long = 5,
    unit: TimeUnit = TimeUnit.MINUTES,
    processId: Int? = null,
    noinline predicate: (MessageReceivedEvent) -> Boolean = { true },
) = awaitMessage(setOf(author), processCommand, invokingMessage, delay, unit, processId, predicate)