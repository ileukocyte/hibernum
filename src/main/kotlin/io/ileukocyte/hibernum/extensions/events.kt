@file:JvmName("EventExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.builders.KEmbedBuilder

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

/* SlashCommandEvent extensions */
inline fun SlashCommandEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun SlashCommandEvent.replySuccess(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS))

fun SlashCommandEvent.replyFailure(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE))

fun SlashCommandEvent.replyConfirmation(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION))

fun SlashCommandEvent.replyWarning(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING))

/* ButtonClickEvent extensions */
inline fun ButtonClickEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun ButtonClickEvent.replySuccess(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS))

fun ButtonClickEvent.replyFailure(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE))

fun ButtonClickEvent.replyConfirmation(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION))

fun ButtonClickEvent.replyWarning(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING))

/* SelectionMenuEvent extensions */
inline fun SelectionMenuEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun SelectionMenuEvent.replySuccess(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.SUCCESS))

fun SelectionMenuEvent.replyFailure(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.FAILURE))

fun SelectionMenuEvent.replyConfirmation(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.CONFIRMATION))

fun SelectionMenuEvent.replyWarning(desc: String) =
    replyEmbeds(defaultEmbed(desc, EmbedType.WARNING))