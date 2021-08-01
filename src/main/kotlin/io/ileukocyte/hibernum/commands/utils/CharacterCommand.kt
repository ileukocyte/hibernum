package io.ileukocyte.hibernum.commands.utils

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.capitalizeAll
import io.ileukocyte.hibernum.extensions.remove
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class CharacterCommand : TextOnlyCommand {
    override val name = "char"
    override val description = "Sends some information about the provided character(s) (including built-in emojis)"
    override val aliases = setOf("character")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.remove(" ")
            ?.run { takeIf { it.length <= 20 } ?: throw CommandException("The amount of characters must not exceed 20!") }
            ?: throw NoArgumentsException

        event.channel.sendEmbed {
            color = Immutable.SUCCESS

            for (codePoint in input.codePoints().distinct()) {
                val chars = Character.toChars(codePoint)
                var mainHex = Integer.toHexString(codePoint).uppercase()

                while (mainHex.length < 4)
                    mainHex = "0$mainHex"

                val hex = if (chars.size > 1) {
                    "`\\u$mainHex` (`" + chars.joinToString("") {
                        var hex = Integer.toHexString(it.code).uppercase()

                        while (hex.length < 4)
                            hex = "0$hex"

                        "\\u$hex"
                    } + "`)"
                } else "`\\u$mainHex`"

                field {
                    title = Character.getName(codePoint)?.capitalizeAll() ?: "Unknown Character"
                    description = buildString { appendLine("${String(chars)} — $hex") }
                }
            }
        }.queue()
    }
}