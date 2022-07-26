@file:JvmName("MessageExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.builders.KMessageBuilder
import io.ileukocyte.hibernum.commands.GenericCommand
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

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
@[Deprecated("Deprecated in favor of Discord button interactions") Throws(TimeoutCancellationException::class)]
suspend fun Message.awaitConfirmationWithReactions(
    user: User,
    processCommand: GenericCommand? = null,
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

fun Message.toCreateData() = MessageCreateData.fromMessage(this)
fun MessageEditData.toCreateData() = MessageCreateData.fromEditData(this)

fun Message.toEditData() = MessageEditData.fromMessage(this)

fun MessageCreateData.toEditData() = MessageEditData.fromCreateData(this)

fun Message.editMessage(block: KMessageBuilder.() -> Unit) =
    editMessage(KMessageBuilder().apply(block)().toEditData())

fun Message.editMessageEmbed(block: KEmbedBuilder.() -> Unit) =
    editMessageEmbeds(KEmbedBuilder().apply(block)())

fun Message.setSuccessEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editMessageEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun Message.setFailureEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editMessageEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun Message.setConfirmationEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editMessageEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun Message.setWarningEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editMessageEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))

fun MessageEditAction.setEmbed(block: KEmbedBuilder.() -> Unit) =
    setEmbeds(KEmbedBuilder().apply(block)())

fun MessageEditAction.setSuccessEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun MessageEditAction.setFailureEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun MessageEditAction.setConfirmationEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun MessageEditAction.setWarningEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))

fun MessageEditCallbackAction.setSuccessEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun MessageEditCallbackAction.setFailureEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun MessageEditCallbackAction.setConfirmationEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun MessageEditCallbackAction.setWarningEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    setEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))