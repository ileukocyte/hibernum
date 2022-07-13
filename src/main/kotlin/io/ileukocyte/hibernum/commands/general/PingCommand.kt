package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class PingCommand : TextCommand {
    override val name = "ping"
    override val description = "Sends the bot's current response latency separately from the statistics"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        event.channel.sendMessageEmbeds(pingEmbed(event.jda)).queue()

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        event.replyEmbeds(pingEmbed(event.jda)).queue()

    private suspend fun pingEmbed(jda: JDA) = buildEmbed {
        color = Immutable.SUCCESS

        field {
            title = "Rest Ping"
            description = "${jda.restPing.await()} ms"
            isInline = true
        }

        field {
            title = "Gateway Ping"
            description = "${jda.gatewayPing} ms"
            isInline = true
        }
    }
}
