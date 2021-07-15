package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.KEmbedBuilder

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

/* SlashCommandEvent extensions */
inline fun SlashCommandEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun SlashCommandEvent.replySuccess(desc: String) = replyEmbed {
    color = Immutable.SUCCESS
    author { name = "Success!" }
    description = desc
}

fun SlashCommandEvent.replyFailure(desc: String) = replyEmbed {
    color = Immutable.FAILURE
    author { name = "Failure!" }
    description = desc
}

fun SlashCommandEvent.replyConfirmation(desc: String) = replyEmbed {
    color = Immutable.CONFIRMATION
    author { name = "Confirmation!" }
    description = desc
}

fun SlashCommandEvent.replyWarning(desc: String) = replyEmbed {
    color = Immutable.WARNING
    author { name = "Warning!" }
    description = desc
}

/* ButtonClickEvent extensions */
inline fun ButtonClickEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun ButtonClickEvent.replySuccess(desc: String) = replyEmbed {
    color = Immutable.SUCCESS
    author { name = "Success!" }
    description = desc
}

fun ButtonClickEvent.replyFailure(desc: String) = replyEmbed {
    color = Immutable.FAILURE
    author { name = "Failure!" }
    description = desc
}

fun ButtonClickEvent.replyConfirmation(desc: String) = replyEmbed {
    color = Immutable.CONFIRMATION
    author { name = "Confirmation!" }
    description = desc
}

fun ButtonClickEvent.replyWarning(desc: String) = replyEmbed {
    color = Immutable.WARNING
    author { name = "Warning!" }
    description = desc
}

/* SelectionMenuEvent extensions */
inline fun SelectionMenuEvent.replyEmbed(block: KEmbedBuilder.() -> Unit) =
    replyEmbeds(KEmbedBuilder().apply(block)())

fun SelectionMenuEvent.replySuccess(desc: String) = replyEmbed {
    color = Immutable.SUCCESS
    author { name = "Success!" }
    description = desc
}

fun SelectionMenuEvent.replyFailure(desc: String) = replyEmbed {
    color = Immutable.FAILURE
    author { name = "Failure!" }
    description = desc
}

fun SelectionMenuEvent.replyConfirmation(desc: String) = replyEmbed {
    color = Immutable.CONFIRMATION
    author { name = "Confirmation!" }
    description = desc
}

fun SelectionMenuEvent.replyWarning(desc: String) = replyEmbed {
    color = Immutable.WARNING
    author { name = "Warning!" }
    description = desc
}