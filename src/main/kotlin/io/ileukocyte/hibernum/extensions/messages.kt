package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.TimeoutCancellationException

const val CHECK_MARK = "\u2705"
const val CROSS_MARK = "\u274E"

/**
 * The extension bringing a faster way
 * to obtain the user's willing to do something with reactions rather than using raw event receiving.
 *
 * @receiver The [Message] that the confirmation reactions are going to be added to
 *
 * @param user
 * The user who the reactions are being received from
 * @param processCommand
 * The default command used as a process property
 * @param delay
 * A period throughout which the reaction answer must be received
 * @param unit
 * A time unit for the aforementioned parameter
 *
 * @return a nullable Boolean value (whether or not the user selected the check mark emoji) received via event awaiting
 *
 * @throws [TimeoutCancellationException] in case of no event obtained
 *
 * @author Alexander Oksanich
 */
@OptIn(ExperimentalTime::class)
@Throws(TimeoutCancellationException::class)
suspend fun Message.awaitConfirmationWithReactions(
    user: User,
    processCommand: Command? = null,
    delay: Long = 1,
    unit: DurationUnit = DurationUnit.MINUTES
): Boolean? {
    addReaction(CHECK_MARK).await()
    addReaction(CROSS_MARK).await()

    val name = jda.awaitEvent<GuildMessageReactionAddEvent>(
        delay = delay,
        unit = unit,
        waiterProcess = waiterProcess {
            users += user.idLong
            channel = this@awaitConfirmationWithReactions.channel.idLong
            command = processCommand
        }
    ) {
        it.user.idLong == user.idLong
                && it.messageIdLong == idLong
                && it.reactionEmote.name in setOf(CHECK_MARK, CROSS_MARK)
    }?.reactionEmote?.name

    return name?.let { it == CHECK_MARK }
}

inline fun Message.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun Message.replySuccess(desc: String) = replyEmbed {
    color = Immutable.SUCCESS
    author { name = "Success!" }
    description = desc
}

fun Message.replyFailure(desc: String) = replyEmbed {
    color = Immutable.FAILURE
    author { name = "Failure!" }
    description = desc
}

fun Message.replyConfirmation(desc: String) = replyEmbed {
    color = Immutable.CONFIRMATION
    author { name = "Confirmation!" }
    description = desc
}

fun Message.replyWarning(desc: String) = replyEmbed {
    color = Immutable.WARNING
    author { name = "Warning!" }
    description = desc
}