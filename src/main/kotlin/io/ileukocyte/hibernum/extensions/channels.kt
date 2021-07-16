package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess
import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

inline fun MessageChannel.sendEmbed(block: KEmbedBuilder.() -> Unit) =
    sendMessageEmbeds(KEmbedBuilder().apply(block)())

fun MessageChannel.sendSuccess(desc: String) = sendEmbed {
    color = Immutable.SUCCESS
    author { name = "Success!" }
    description = desc
}

fun MessageChannel.sendFailure(desc: String) = sendEmbed {
    color = Immutable.FAILURE
    author { name = "Failure!" }
    description = desc
}

fun MessageChannel.sendConfirmation(desc: String) = sendEmbed {
    color = Immutable.CONFIRMATION
    author { name = "Confirmation!" }
    description = desc
}

fun MessageChannel.sendWarning(desc: String) = sendEmbed {
    color = Immutable.WARNING
    author { name = "Warning!" }
    description = desc
}

/**
 * The extension bringing a faster way to obtain an awaited message rather than using raw event receiving.
 *
 * @receiver The [MessageChannel] in which the message is going to be awaited
 *
 * @param authors
 * A set of users who the message is being received from
 * @param processCommand
 * The default command used as a process property
 * @param delay
 * A period throughout which the message must be received
 * @param unit
 * A time unit for the aforementioned parameter
 *
 * @return a nullable (in the case of the process being manually terminated) [Message][net.dv8tion.jda.api.entities.Message] entity
 * received via event awaiting
 *
 * @throws [TimeoutCancellationException] in the case of no event being obtained
 *
 * @author Alexander Oksanich
 */
@OptIn(ExperimentalTime::class)
@Throws(TimeoutCancellationException::class)
suspend inline fun MessageChannel.awaitMessage(
    authors: Set<User>,
    processCommand: Command? = null,
    delay: Long = 1,
    unit: DurationUnit = DurationUnit.MINUTES
) = jda.awaitEvent<GuildMessageReceivedEvent>(
    delay = delay,
    unit = unit,
    waiterProcess = waiterProcess {
        users += authors.map { it.idLong }
        channel = idLong
        command = processCommand
    }
) { it.author in authors && it.channel.idLong == idLong }?.message

/**
 * The extension bringing a faster way to obtain an awaited message rather than using raw event receiving.
 *
 * @receiver The [MessageChannel] in which the message is going to be awaited
 *
 * @param author
 * A user who the message is being received from
 * @param processCommand
 * The default command used as a process property
 * @param delay
 * A period throughout which the message must be received
 * @param unit
 * A time unit for the aforementioned parameter
 *
 * @return a nullable (in case of the process being manually terminated) [Message][net.dv8tion.jda.api.entities.Message] entity
 * received via event awaiting
 *
 * @throws [TimeoutCancellationException] in the case of no event being obtained
 *
 * @author Alexander Oksanich
 */
@OptIn(ExperimentalTime::class)
@Throws(TimeoutCancellationException::class)
suspend inline fun MessageChannel.awaitMessage(
    author: User,
    processCommand: Command? = null,
    delay: Long = 1,
    unit: DurationUnit = DurationUnit.MINUTES
) = awaitMessage(setOf(author), processCommand, delay, unit)