package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class ClearCommand : TextCommand {
    override val name = "clear"
    override val description = "Clears the queue while keeping the current song playing"
    override val aliases = setOf("clear-playlist", "clear-queue")

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (event.guild.audioPlayer?.scheduler?.queue?.isEmpty() == false) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val buttons = setOf(
                    Button.danger("$interactionName-${event.author.idLong}-clear", "Yes"),
                    Button.secondary("$interactionName-${event.author.idLong}-exit", "No"),
                )

                event.channel.sendConfirmation("Are you sure you want the bot to clear the queue?")
                    .setActionRow(buttons)
                    .queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The queue is empty!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.guild?.audioPlayer?.scheduler?.queue?.isEmpty() == false) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val buttons = setOf(
                    Button.danger("$interactionName-${event.user.idLong}-clear", "Yes"),
                    Button.secondary("$interactionName-${event.user.idLong}-exit", "No"),
                )

                event.replyConfirmation("Are you sure you want the bot to clear the queue?")
                    .addActionRow(buttons)
                    .queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The queue is empty!")
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "clear" -> {
                    event.guild?.audioPlayer?.scheduler?.queue?.clear()

                    event.editComponents().setSuccessEmbed("The queue has been cleared!")
                        .queue(null) {
                            event.channel.sendSuccess("The queue has been cleared!").queue()
                        }
                }
                "exit" -> event.message.delete().queue(null) {}
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }
}