package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class PingCommand : Command {
    override val name = "ping"
    override val description = "The command sends Hibernum's current response time separately from the statistics"

    override suspend fun invoke(event: SlashCommandEvent) =
        sendPing(event)

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        sendPing(event)

    private suspend fun <E : Event> sendPing(event: E) {
        val embed = buildEmbed {
            color = Immutable.SUCCESS

            field {
                title = "Rest Ping"
                description = "${event.jda.restPing.await()} ms"
                isInline = true
            }

            field {
                title = "WebSocket Ping"
                description = "${event.jda.gatewayPing} ms"
                isInline = true
            }
        }

        val restAction = when (event) {
            is GuildMessageReceivedEvent -> event.channel.sendMessageEmbeds(embed)
            is SlashCommandEvent -> event.replyEmbeds(embed)
            else -> null // must never occur
        }

        restAction?.queue()
    }
}
