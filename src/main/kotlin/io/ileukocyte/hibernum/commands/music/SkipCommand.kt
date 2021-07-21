package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

import org.jetbrains.kotlin.utils.addToStdlib.cast

class SkipCommand : Command {
    override val name = "skip"
    override val description = "Skips to the next song in the queue"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                audioPlayer.player.playingTrack.userData.cast<TrackUserData>().announcement?.delete()?.queue({}) {}

                audioPlayer.scheduler.nextTrack()

                val description = "Playback has been successfully stopped!".takeIf { audioPlayer.player.playingTrack === null }

                description?.let { event.channel.sendSuccess(it).queue() }
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("No track is currently playing!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == guild.selfMember.voiceState?.channel) {
                val deferred = event.deferReply().await()

                audioPlayer.player.playingTrack.userData.cast<TrackUserData>().announcement?.delete()?.queue({}) {}

                audioPlayer.scheduler.nextTrack()

                val description = "Playback has been successfully stopped!".takeIf { audioPlayer.player.playingTrack === null }

                description?.let { deferred.editOriginalEmbeds(defaultEmbed(it, EmbedType.SUCCESS)).queue() }
                    ?: deferred.deleteOriginal().queue()
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("No track is currently playing!")
    }
}