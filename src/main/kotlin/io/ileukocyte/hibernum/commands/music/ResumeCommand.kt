package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class ResumeCommand : TextOnlyCommand {
    override val name = "resume"
    override val description = "Resume the music after a pause"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel) {
                event.channel.sendFailure("You are not connected to the required voice channel!").queue()
            } else {
                if (audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = false

                    event.channel.sendSuccess("The track has been successfully resumed!").queue()
                } else event.channel.sendFailure("The track is already playing!").queue()
            }
        } else event.channel.sendFailure("No track is currently playing!").queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (audioPlayer.player.isPaused) {
                audioPlayer.player.isPaused = false

                event.replySuccess("The track has been successfully resumed!").queue()
            } else event.replyFailure("The track is already playing!")
                .setEphemeral(true)
                .queue()
        } else event.replyFailure("No track is currently playing!")
            .setEphemeral(true)
            .queue()
    }
}