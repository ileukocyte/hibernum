package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.jetbrains.kotlin.utils.addToStdlib.cast

class RequeueCommand : TextCommand {
    override val name = "requeue"
    override val description = "Adds the specified (or currently playing) song to the end of the queue"
    override val aliases = setOf("re-add", "readd", "re-queue")
    override val usages = setOf(setOf("song".toClassicTextUsage()))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The number of the song to readd"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val number = args?.let {
            it.toIntOrNull() ?: throw CommandException("You have specified a wrong number!")
        }

        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val track = number?.let {
                    audioPlayer.scheduler.queue.toList().getOrNull(it.dec())
                        ?: throw CommandException("You have specified a wrong number!")
                } ?: audioPlayer.player.playingTrack
                    ?: throw CommandException("Somehow no track appears to be currently playing!")

                audioPlayer.scheduler += track.makeClone().apply {
                    userData = track.userData.cast<TrackUserData>()
                        .copy(announceQueueing = true)
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val track = event.getOption("song")?.asInt?.let {
                    audioPlayer.scheduler.queue.toList().getOrNull(it.dec())
                        ?: throw CommandException("You have specified a wrong number!")
                } ?: audioPlayer.player.playingTrack
                    ?: throw CommandException("Somehow no track appears to be currently playing!")

                audioPlayer.scheduler += track.makeClone().apply {
                    userData = track.userData.cast<TrackUserData>().copy(
                        announceQueueing = true,
                        ifFromSlashCommand = event.deferReply().await(),
                    )
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }
}