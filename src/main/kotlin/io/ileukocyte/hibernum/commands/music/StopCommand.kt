package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class StopCommand : TextCommand {
    override val name = "stop"
    override val description = "Stops the song that is currently playing and clears the queue"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (event.guild.audioPlayer?.player?.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val description = "Are you sure you want the bot to stop playing music and clear the queue?"
                val buttons = setOf(
                    Button.danger("$interactionName-${event.author.idLong}-stop", "Yes"),
                    Button.secondary("$interactionName-${event.author.idLong}-exit", "No"),
                )

                event.channel.sendConfirmation(description).setActionRow(buttons).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.guild?.audioPlayer?.player?.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val description = "Are you sure you want the bot to stop playing music and clear the queue?"
                val buttons = setOf(
                    Button.danger("$interactionName-${event.user.idLong}-stop", "Yes"),
                    Button.secondary("$interactionName-${event.user.idLong}-exit", "No"),
                )

                event.replyConfirmation(description).addActionRow(buttons).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "stop" -> {
                    val embed = defaultEmbed("The playback has been stopped!", EmbedType.SUCCESS)

                    event.guild?.audioPlayer?.stop()

                    event.editComponents().setEmbeds(embed).queue(null) {
                        event.channel.sendMessageEmbeds(embed).queue()
                    }

                    id[1].toLongOrNull()?.let { playerMessageId ->
                        event.channel.retrieveMessageById(playerMessageId).queue({ player ->
                            player.editMessageComponents().queue(null) {}
                        }) {}
                    }
                }
                "exit" -> event.message.delete().queue(null) {}
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }
}