package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class RestartCommand : TextCommand {
    override val name = "restart"
    override val description = "Restarts the track that is currently playing"
    override val aliases = setOf("replay")

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                if (audioPlayer.player.playingTrack.info.isStream) {
                    throw CommandException("The track cannot be restarted since it is recognized as a stream!")
                }

                audioPlayer.player.playingTrack.position = 0

                event.channel.sendSuccess("The track has been restarted!").queue()
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
                if (audioPlayer.player.playingTrack.info.isStream) {
                    throw CommandException("The track cannot be restarted since it is recognized as a stream!")
                }

                audioPlayer.player.playingTrack.position = 0

                event.replySuccess("The track has been restarted!").queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }
}