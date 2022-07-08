package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendConfirmation

import kotlin.system.exitProcess

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class ShutdownCommand : Command {
    override val name = "shutdown"
    override val description = "Shuts the bot down"

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        sendShutdownConfirmation(event.user.idLong, event)

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        sendShutdownConfirmation(event.author.idLong, event)

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "shut" -> {
                    event.replySuccess("${event.jda.selfUser.name} has been shut down!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .queue({
                            event.jda.shutdown()
                            exitProcess(0)
                        }) {
                            event.jda.shutdown()
                            exitProcess(0)
                        }
                }
                "exit" -> event.message.delete().queue {
                    event.replySuccess("Successfully canceled!").setEphemeral(true).queue()
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun <E : Event> sendShutdownConfirmation(userId: Long, event: E) {
        val description = "Are you sure you want to shut the bot down?"
        val buttons = setOf(
            Button.danger("$name-$userId-shut", "Yes"),
            Button.secondary("$name-$userId-exit", "No"),
        )

        when (event) {
            is MessageReceivedEvent ->
                event.channel.sendConfirmation(description).setActionRow(buttons).queue()
            is SlashCommandInteractionEvent ->
                event.replyConfirmation(description).addActionRow(buttons).queue()
        }
    }
}