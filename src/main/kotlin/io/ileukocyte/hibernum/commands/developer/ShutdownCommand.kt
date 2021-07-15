package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.commands.UniversalCommand
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendConfirmation

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

import kotlin.system.exitProcess

class ShutdownCommand : UniversalCommand {
    override val name = "shutdown"
    override val description = "The command shuts Hibernum down"

    override suspend fun invoke(event: SlashCommandEvent) =
        sendShutdownConfirmation(event.user.idLong, event)

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        sendShutdownConfirmation(event.author.idLong, event)

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "shut" -> {
                    event
                        .replySuccess("${event.jda.selfUser.name} has been successfully shut down!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .queue {
                            event.jda.shutdown()
                            exitProcess(0)
                        }
                }
                "exit" -> event.message?.delete()?.queue {
                    event.replySuccess("Successfully canceled!").setEphemeral(true).queue()
                }
            }
        } else event.replyFailure("You did not invoke the initial command!").setEphemeral(true).queue()
    }

    private fun <E : Event> sendShutdownConfirmation(userId: Long, event: E) {
        val description = "Are you sure you want to shut the bot down?"
        val buttons = setOf(
            Button.danger("$name-$userId-shut", "Yes"),
            Button.secondary("$name-$userId-exit", "No")
        )

        val restAction = when (event) {
            is GuildMessageReceivedEvent ->
                event.channel.sendConfirmation(description).setActionRow(buttons)
            is SlashCommandEvent ->
                event.replyConfirmation(description).addActionRow(buttons)
            else -> null // must never occur
        }

        restAction?.queue()
    }
}