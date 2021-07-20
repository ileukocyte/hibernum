@file:JvmName("EventExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder
import io.ileukocyte.hibernum.builders.KMessageBuilder
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.builders.buildMessage

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.interactions.components.Component

inline fun GenericInteractionCreateEvent.reply(block: KMessageBuilder.() -> Unit) =
    reply(buildMessage(block))

inline fun GenericInteractionCreateEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(buildEmbed(block))

fun GenericInteractionCreateEvent.replySuccess(desc: String, footer: String? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS, footer))

fun GenericInteractionCreateEvent.replyFailure(desc: String, footer: String? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE, footer))

fun GenericInteractionCreateEvent.replyConfirmation(desc: String, footer: String? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION, footer))

fun GenericInteractionCreateEvent.replyWarning(desc: String, footer: String? = null) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING, footer))

fun GenericInteractionCreateEvent.replyActionRow(vararg components: Component) =
    reply(ZERO_WIDTH_SPACE).addActionRow(*components)