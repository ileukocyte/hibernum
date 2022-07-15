package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendConfirmation

import kotlin.system.exitProcess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class ShutdownCommand : TextCommand {
    override val name = "shutdown"
    override val description = "Shuts the bot down"
    override val neglectProcessBlock = true

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val description = "Are you sure you want to shut the bot down?"
        val buttons = setOf(
            Button.danger("$name-${event.author.idLong}-shut", "Yes"),
            Button.secondary("$name-${event.author.idLong}-exit", "No"),
        )

        event.channel.sendConfirmation(description).setActionRow(buttons).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val description = "Are you sure you want to shut the bot down?"
        val buttons = setOf(
            Button.danger("$name-${event.user.idLong}-shut", "Yes"),
            Button.secondary("$name-${event.user.idLong}-exit", "No"),
        )

        event.replyConfirmation(description).addActionRow(buttons).queue()
    }

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
                "exit" -> event.message.delete().queue(null) {}
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }
}