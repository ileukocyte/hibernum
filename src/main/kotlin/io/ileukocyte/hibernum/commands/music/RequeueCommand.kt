package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.customUserData
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class RequeueCommand : TextCommand {
    override val name = "requeue"
    override val description = "Adds the specified (or currently playing) song to the end of the queue"
    override val aliases = setOf("re-add", "readd", "re-queue")
    override val usages = setOf(usageGroupOf("song".toClassicTextUsage()))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The number of the song to readd"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val number = args?.let {
            it.toIntOrNull() ?: throw CommandException("You have specified a wrong number!")
        }

        val audioPlayer = event.guild.audioPlayer ?: return

        if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
            if (audioPlayer.player.playingTrack !== null) {
                val track = number?.let {
                    audioPlayer.scheduler.queue.toList().getOrNull(it.dec())
                        ?: throw CommandException("You have specified a wrong number!")
                } ?: audioPlayer.player.playingTrack ?: return

                audioPlayer.scheduler += track.makeClone().apply {
                    userData = track.customUserData.copy(
                        announceQueueing = true,
                        user = event.author,
                    )
                }
            } else {
                throw CommandException("No track is currently playing!")
            }
        } else {
            throw CommandException("You are not connected to the required voice channel!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return

        if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
            if (audioPlayer.player.playingTrack !== null) {
                val track = event.getOption("song")?.asInt?.let {
                    audioPlayer.scheduler.queue.toList().getOrNull(it.dec())
                        ?: throw CommandException("You have specified a wrong number!")
                } ?: audioPlayer.player.playingTrack ?: return

                audioPlayer.scheduler += track.makeClone().apply {
                    userData = track.customUserData.copy(
                        announceQueueing = true,
                        ifFromSlashCommand = event.deferReply().await(),
                        user = event.user,
                    )
                }
            } else {
                throw CommandException("No track is currently playing!")
            }
        } else {
            throw CommandException("You are not connected to the required voice channel!")
        }
    }
}