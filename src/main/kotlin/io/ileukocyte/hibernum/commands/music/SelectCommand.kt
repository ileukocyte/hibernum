package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.GuildMusicManager
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.customUserData
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.defaultUsageGroupOf
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.setFailureEmbed

import java.util.concurrent.ConcurrentLinkedQueue

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class SelectCommand : TextCommand {
    override val name = "select"
    override val description = "Selects another track from the queue and plays it"
    override val usages = setOf(defaultUsageGroupOf("song"))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The number of the song to play", true))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val number = args?.toIntOrNull() ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild.audioPlayer ?: return

        select(number, audioPlayer, event.guild, event.member ?: return)
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()

        val number = event.getOption("song")?.asString?.toIntOrNull()
            ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild?.audioPlayer ?: return

        select(number, audioPlayer, event.guild ?: return, event.member ?: return, deferred)
    }

    private suspend fun select(
        number: Int,
        audioPlayer: GuildMusicManager,
        guild: Guild,
        member: Member,
        ifFromSlashCommandEvent: InteractionHook? = null,
    ) {
        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (member.voiceState?.channel == guild.selfMember.voiceState?.channel) {
                audioPlayer.player.playingTrack.customUserData.announcement?.delete()?.queue({}) {}

                val queue = audioPlayer.scheduler.queue.toMutableList()
                val track = queue.getOrNull(number.dec())

                if (track === null) {
                    val error = "You have specified a wrong number!"

                    ifFromSlashCommandEvent?.let {
                        try {
                            it.setFailureEmbed(error).await()

                            return
                        } catch (_: ErrorResponseException) {
                            throw CommandException(error)
                        }
                    } ?: throw CommandException(error)
                }

                queue -= track

                track.userData = track.customUserData.copy(ifFromSlashCommand = ifFromSlashCommandEvent)

                audioPlayer.player.startTrack(track, false)
                audioPlayer.scheduler.queue = ConcurrentLinkedQueue(queue)
            } else {
                val error = "You are not connected to the required voice channel!"

                ifFromSlashCommandEvent
                    ?.setFailureEmbed(error)
                    ?.queue(null) { throw CommandException(error) }
                    ?: throw CommandException(error)
            }
        } else {
            val error = "The current queue is empty!"

            ifFromSlashCommandEvent
                ?.setFailureEmbed(error)
                ?.queue(null) { throw CommandException(error) }
                ?: throw CommandException(error)
        }
    }
}