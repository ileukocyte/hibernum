package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class PingCommand : Command {
    override val name = "ping"
    override val description = "The command sends Hibernum's current response time separately from the statistics"

    override suspend fun invoke(event: SlashCommandEvent) {
        val restPing = event.jda.restPing.await()

        event.replyEmbed {
            color = Immutable.SUCCESS

            field {
                title = "Rest Ping"
                description = "$restPing ms"
                isInline = true
            }

            field {
                title = "WebSocket Ping"
                description = "${event.jda.gatewayPing} ms"
                isInline = true
            }
        }.queue()
    }

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val restPing = event.jda.restPing.await()

        event.channel.sendEmbed {
            color = Immutable.SUCCESS

            field {
                title = "Rest Ping"
                description = "$restPing ms"
                isInline = true
            }

            field {
                title = "WebSocket Ping"
                description = "${event.jda.gatewayPing} ms"
                isInline = true
            }
        }.queue()
    }
}
