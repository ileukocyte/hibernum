package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class ChooseCommand : Command {
    override val name = "choose"
    override val description = "Chooses a random option among the provided ones split with the \"|\" character"
    override val aliases = setOf("random")
    override val usages = setOf(setOf("option 1> | <option 2> | ... | <option n"))
    override val options = setOf(
        OptionData(OptionType.STRING, "options", "The options provided (split with \"|\")", true))

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.split(Regex("\\s?\\|\\s?"))
            ?.filter { it.isNotEmpty() }
            ?.takeUnless { it.isEmpty() }
            ?: throw NoArgumentsException

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = input.random()
        }.queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val input = event.getOption("options")?.asString
            ?.split(Regex("\\s?\\|\\s?"))
            ?.filter { it.isNotEmpty() }
            ?.takeUnless { it.isEmpty() }
            ?: throw NoArgumentsException

        event.replyEmbed {
            color = Immutable.SUCCESS
            description = input.random()
        }.queue()
    }
}