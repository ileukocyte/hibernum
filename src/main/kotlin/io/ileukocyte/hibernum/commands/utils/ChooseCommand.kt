package io.ileukocyte.hibernum.commands.utils

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.sendEmbed
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class ChooseCommand : TextOnlyCommand {
    override val name = "choose"
    override val description = "N/A"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.split(Regex("((?:\\s)?\\|(?:\\s)?)"))
            ?.filter { it.isNotEmpty() }
            ?.takeUnless { it.isEmpty() }
            ?: throw NoArgumentsException

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = input.random()
        }.queue()
    }
}