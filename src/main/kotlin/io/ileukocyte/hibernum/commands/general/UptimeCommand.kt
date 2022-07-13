package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.startDate
import io.ileukocyte.hibernum.extensions.uptime
import io.ileukocyte.hibernum.utils.asText

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class UptimeCommand : TextCommand {
    override val name = "uptime"
    override val description = "Sends the bot's uptime separately from the statistics"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        event.channel.sendMessageEmbeds(uptimeEmbed(event.jda)).queue()

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        event.replyEmbeds(uptimeEmbed(event.jda)).queue()

    private fun uptimeEmbed(jda: JDA) = buildEmbed {
        color = Immutable.SUCCESS
        timestamp = jda.startDate

        field {
            title = "Uptime"
            description = asText(jda.uptime)
        }

        footer { text = "Last Reboot" }
    }
}