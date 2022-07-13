package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class PauseCommand : TextCommand {
    override val name = "pause"
    override val description = "Temporarily pauses the music"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                if (!audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = true

                    event.channel.sendSuccess("The track has been paused!").queue()
                } else {
                    throw CommandException("The track is already paused!")
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == guild.selfMember.voiceState?.channel) {
                if (!audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = true

                    event.replySuccess("The track has been paused!").queue()
                } else {
                    throw CommandException("The track is already paused!")
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }
}