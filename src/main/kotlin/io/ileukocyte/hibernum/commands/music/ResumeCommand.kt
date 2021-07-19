package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class ResumeCommand : Command {
    override val name = "resume"
    override val description = "Resume the music after a pause"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                if (audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = false

                    event.channel.sendSuccess("The track has been successfully resumed!").queue()
                } else throw CommandException("The track is already playing!")
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("No track is currently playing!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == guild.selfMember.voiceState?.channel) {
                if (audioPlayer.player.isPaused) {
                    audioPlayer.player.isPaused = false

                    event.replySuccess("The track has been successfully resumed!").queue()
                } else throw CommandException("The track is already playing!")
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("No track is currently playing!")
    }
}