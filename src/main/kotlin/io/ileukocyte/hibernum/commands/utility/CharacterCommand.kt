package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle

class CharacterCommand : TextCommand {
    override val name = "char"
    override val description = "Sends some information about the provided character(s) (including built-in emojis)"
    override val aliases = setOf("character")
    override val options = setOf(
        OptionData(OptionType.STRING, "input", "The characters provided"))
    override val usages = setOf(usageGroupOf("input".toClassicTextUsage()))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val input = args
            ?.remove(" ")
            ?.let {
                it.takeIf { it.toCharArray().distinct().size <= 25 }
                    ?: throw CommandException("The amount of unique characters must not exceed 25!")
            } ?: throw NoArgumentsException

        event.channel.sendMessageEmbeds(charEmbed(input)).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val input = event.getOption("input")?.asString
            ?.remove(" ")
            ?.let {
                it.takeIf { it.toCharArray().distinct().size <= 25 }
                    ?: throw CommandException("The amount of unique characters must not exceed 25!")
            }

        if (input !== null) {
            try {
                event.deferReply().await()
                    .editOriginalEmbeds(charEmbed(input))
                    .queue()
            } catch (_: ErrorResponseException) {
                event.channel.sendMessageEmbeds(charEmbed(input)).queue()
            }
        } else {
            val modalInput = TextInput
                .create("input", "Enter Your Text (up to 25 unique characters):", TextInputStyle.PARAGRAPH)
                .build()
            val modal = Modal
                .create("$interactionName-modal", "Character Information")
                .addActionRow(modalInput)
                .build()

            event.replyModal(modal).queue()
        }
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val input = event.getValue("input")?.asString
            ?.takeIf { it.toCharArray().distinct().size <= 25 }
            ?: throw CommandException("The amount of unique characters must not exceed 25!")

        try {
            event.deferReply().await()
                .editOriginalEmbeds(charEmbed(input))
                .queue()
        } catch (_: ErrorResponseException) {
            event.messageChannel.sendMessageEmbeds(charEmbed(input)).queue()
        }
    }

    private fun charEmbed(input: String) = buildEmbed {
        color = Immutable.SUCCESS

        for (codePoint in input.codePoints().distinct()) {
            val chars = codePoint.toChars()
            val mainHex = "%04X".format(codePoint)

            val hex = if (chars.size > 1) {
                "`\\u$mainHex` (`" + chars.joinToString("") {
                    val hex = "%04X".format(it.code)

                    "\\u$hex"
                } + "`)"
            } else {
                "`\\u$mainHex`"
            }

            field {
                title = codePoint.charName?.capitalizeAll() ?: "Unknown Character"
                description = buildString { appendLine("${String(chars)} — $hex") }
            }
        }
    }
}