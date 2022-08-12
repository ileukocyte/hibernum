package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class ChooseCommand : TextCommand {
    override val name = "choose"
    override val description = "Chooses a random option among the provided ones split with the \"|\" character"
    override val fullDescription =
        "$description (if you want to use \"|\" within any option, you can prefix the character with \"\\\\\")"
    override val aliases = setOf("random")
    override val usages = setOf(
        usageGroupOf("<option 1> | <option 2> | ... | <option N>".toClassicTextUsage(applyDefaultAffixes = false)),
    )
    override val options = setOf(
        OptionData(OptionType.STRING, "options", "The options provided (split with \"|\")", true))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val input = args?.split(Regex("\\s?(?<!\\\\)\\|\\s?"))
            ?.filter { it.isNotEmpty() }
            ?.takeUnless { it.isEmpty() }
            ?: throw NoArgumentsException

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = input.random()
        }.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val input = event.getOption("options")?.asString
            ?.split(Regex("\\s?(?<!\\\\)\\|\\s?"))
            ?.filter { it.isNotEmpty() }
            ?.takeUnless { it.isEmpty() }
            ?: throw NoArgumentsException

        event.replyEmbed {
            color = Immutable.SUCCESS
            description = input.random()
        }.queue()
    }
}