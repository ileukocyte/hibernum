package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.customUserData
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionType

class SkipCommand : TextCommand {
    override val name = "skip"
    override val description = "Skips to the next song in the queue"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val announcement = audioPlayer.player.playingTrack.customUserData.announcement

                announcement?.takeUnless {
                    val interaction = it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND }

                    interaction !== null && interaction.name != "skip"
                }?.delete()?.queue(null) {}

                audioPlayer.scheduler.nextTrack(newAnnouncementChannel = event.guildChannel)

                val description = "The playback has been stopped!"
                    .takeIf { audioPlayer.player.playingTrack === null }

                description?.let { event.channel.sendSuccess(it).queue() }
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
                val deferred = event.deferReply().await()

                val announcement = audioPlayer.player.playingTrack.customUserData.announcement

                announcement?.takeUnless {
                    val interaction = it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND }

                    interaction !== null && interaction.name != "skip"
                }?.delete()?.queue(null) {}

                audioPlayer.scheduler.nextTrack(deferred, event.guildChannel)

                val description = "The playback has been stopped!"
                    .takeIf { audioPlayer.player.playingTrack === null }

                description?.let {
                    deferred.setSuccessEmbed(it).queue(null) { _ ->
                        event.channel.sendSuccess(it).queue()
                    }
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }
}