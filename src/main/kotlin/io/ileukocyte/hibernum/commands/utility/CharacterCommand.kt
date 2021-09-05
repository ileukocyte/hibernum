package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class CharacterCommand : Command {
    override val name = "char"
    override val description = "Sends some information about the provided character(s) (including built-in emojis)"
    override val aliases = setOf("character")
    override val options = setOf(OptionData(OptionType.STRING, "input", "The characters provided", true))
    override val usages = setOf("input")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.remove(" ")
            ?.run { takeIf { it.length <= 20 } ?: throw CommandException("The amount of characters must not exceed 20!") }
            ?: throw NoArgumentsException

        event.channel.sendMessageEmbeds(charEmbed(input)).queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val input = event.getOption("input")?.asString?.remove(" ")
            ?.run { takeIf { it.length <= 20 } ?: throw CommandException("The amount of characters must not exceed 20!") }
            ?: return

        event.replyEmbeds(charEmbed(input)).queue()
    }

    private fun charEmbed(input: String) = buildEmbed {
        color = Immutable.SUCCESS

        for (codePoint in input.codePoints().distinct()) {
            val chars = codePoint.toChars()
            var mainHex = codePoint.toString(16).uppercase()

            while (mainHex.length < 4)
                mainHex = "0$mainHex"

            val hex = if (chars.size > 1) {
                "`\\u$mainHex` (`" + chars.joinToString("") {
                    var hex = it.code.toString(16).uppercase()

                    while (hex.length < 4)
                        hex = "0$hex"

                    "\\u$hex"
                } + "`)"
            } else "`\\u$mainHex`"

            field {
                title = codePoint.charName?.capitalizeAll() ?: "Unknown Character"
                description = buildString { appendLine("${String(chars)} — $hex") }
            }
        }
    }
}