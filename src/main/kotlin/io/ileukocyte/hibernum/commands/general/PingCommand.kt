package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class PingCommand : Command {
    override val name = "ping"
    override val description = "Sends Hibernum's current response latency separately from the statistics"

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        sendPing(event)

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
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
                title = "Gateway Ping"
                description = "${event.jda.gatewayPing} ms"
                isInline = true
            }
        }

        when (event) {
            is MessageReceivedEvent -> event.channel.sendMessageEmbeds(embed).queue()
            is SlashCommandInteractionEvent -> event.replyEmbeds(embed).queue()
        }
    }
}
