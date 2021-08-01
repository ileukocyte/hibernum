package io.ileukocyte.hibernum.commands.utils

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.remove
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class CharacterCommand : TextOnlyCommand {
    override val name = "char"
    override val description = "char"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.remove(" ")
            ?.run { takeIf { it.length <= 20 } ?: throw CommandException("too long") }
            ?: throw NoArgumentsException

        event.channel.sendEmbed {
            for (codePoint in input.codePoints()) {
                //val char = codePoint.toChar()
                var hex = Integer.toHexString(codePoint)

                while (hex.length < 4)
                    hex = "0$hex"

                appendln("`${codePoint.toChar()}`: `\\u$hex` ${Character.getName(codePoint) ?: "unknown"}")
            }
        }.queue()
    }
}