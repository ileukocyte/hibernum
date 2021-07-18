@file:JvmName("EventExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.interactions.components.Component

inline fun GenericInteractionCreateEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun GenericInteractionCreateEvent.replySuccess(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS))

fun GenericInteractionCreateEvent.replyFailure(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE))

fun GenericInteractionCreateEvent.replyConfirmation(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION))

fun GenericInteractionCreateEvent.replyWarning(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING))

fun GenericInteractionCreateEvent.replyActionRow(vararg components: Component) =
    reply(ZERO_WIDTH_SPACE).addActionRow(*components)