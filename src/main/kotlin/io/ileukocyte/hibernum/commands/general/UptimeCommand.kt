package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.startDate
import io.ileukocyte.hibernum.extensions.uptime
import io.ileukocyte.hibernum.utils.asText

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class UptimeCommand : Command {
    override val name = "uptime"
    override val description = "The command sends Hibernum's uptime separately from the statistics"

    override suspend fun invoke(event: SlashCommandEvent) =
        event.replyEmbed {
            color = Immutable.SUCCESS
            timestamp = event.jda.startDate

            field {
                title = "Uptime"
                description = asText(event.jda.uptime)
            }

            footer { text = "Last Reboot" }
        }.queue()

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            timestamp = event.jda.startDate

            field {
                title = "Uptime"
                description = asText(event.jda.uptime)
            }

            footer { text = "Last Reboot" }
        }.queue()
}