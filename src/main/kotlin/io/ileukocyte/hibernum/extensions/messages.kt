@file:JvmName("MessageExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent

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
@Throws(TimeoutCancellationException::class)
suspend fun Message.awaitConfirmationWithReactions(
    user: User,
    processCommand: Command? = null,
    delay: Long = 1,
    unit: TimeUnit = TimeUnit.MINUTES,
): Boolean? {
    addReaction(Emoji.fromUnicode(CHECK_MARK)).await()
    addReaction(Emoji.fromUnicode(CROSS_MARK)).await()

    val name = jda.awaitEvent<MessageReactionAddEvent>(
        delay = delay,
        unit = unit,
        waiterProcess = waiterProcess {
            users += user.idLong
            channel = this@awaitConfirmationWithReactions.channel.idLong
            command = processCommand
        },
    ) {
        it.user?.idLong == user.idLong
                && it.messageIdLong == idLong
                && it.emoji.name in setOf(CHECK_MARK, CROSS_MARK)
    }?.emoji?.name

    return name?.let { it == CHECK_MARK }
}

inline fun Message.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun Message.replySuccess(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun Message.replyFailure(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun Message.replyConfirmation(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun Message.replyWarning(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))