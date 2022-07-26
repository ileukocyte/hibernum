@file:JvmName("ReplyCallbackExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.builders.KMessageBuilder
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.builders.buildMessage

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionComponent

inline fun IReplyCallback.reply(block: KMessageBuilder.() -> Unit) =
    reply(buildMessage(block = block))

inline fun IReplyCallback.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(buildEmbed(block))

fun IReplyCallback.replySuccess(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun IReplyCallback.replyFailure(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun IReplyCallback.replyConfirmation(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun IReplyCallback.replyWarning(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))

fun IReplyCallback.replyActionRow(vararg components: ActionComponent) =
    reply(ZERO_WIDTH_SPACE).addActionRow(*components)

fun InteractionHook.editOriginal(block: KMessageBuilder.() -> Unit) =
    editOriginal(KMessageBuilder().apply(block)().toEditData())

fun InteractionHook.editOriginalEmbed(block: KEmbedBuilder.() -> Unit) =
    editOriginalEmbeds(buildEmbed(block))

fun InteractionHook.setSuccessEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editOriginalEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun InteractionHook.setFailureEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editOriginalEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun InteractionHook.setConfirmationEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editOriginalEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun InteractionHook.setWarningEmbed(desc: String, footer: (KEmbedBuilder.Footer.() -> Unit)? = null) =
    editOriginalEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))