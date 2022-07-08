package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.startDate
import io.ileukocyte.hibernum.extensions.uptime
import io.ileukocyte.hibernum.utils.asText

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class UptimeCommand : Command {
    override val name = "uptime"
    override val description = "Sends Hibernum's uptime separately from the statistics"

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        sendUptime(event)

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        sendUptime(event)

    private fun <E : Event> sendUptime(event: E) {
        val embed = buildEmbed {
            color = Immutable.SUCCESS
            timestamp = event.jda.startDate

            field {
                title = "Uptime"
                description = asText(event.jda.uptime)
            }

            footer { text = "Last Reboot" }
        }

        when (event) {
            is MessageReceivedEvent -> event.channel.sendMessageEmbeds(embed).queue()
            is SlashCommandInteractionEvent -> event.replyEmbeds(embed).queue()
        }
    }
}