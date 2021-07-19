package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class PauseCommand : Command {
    override val name = "pause"
    override val description = "Temporarily pauses the music"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel) {
                event.channel.sendFailure("You are not connected to the required voice channel!").queue()
            } else {
                if (!audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = true

                    event.channel.sendSuccess("The track has been successfully paused!").queue()
                } else event.channel.sendFailure("The track is already paused!").queue()
            }
        } else event.channel.sendFailure("No track is currently playing!").queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (!audioPlayer.player.isPaused) {
                audioPlayer.player.isPaused = true

                event.replySuccess("The track has been successfully paused!").queue()
            } else event.replyFailure("The track is already paused!")
                .setEphemeral(true)
                .queue()
        } else event.replyFailure("No track is currently playing!")
            .setEphemeral(true)
            .queue()
    }
}