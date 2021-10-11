package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.GuildMusicManager
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.jetbrains.kotlin.utils.addToStdlib.cast

import java.util.concurrent.ConcurrentLinkedQueue

class SelectCommand : Command {
    override val name = "select"
    override val description = "Selects another track from the queue and plays it"
    override val usages = setOf(setOf("song"))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The number of the song to play", true)
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val number = args?.toIntOrNull() ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        select(number, audioPlayer, event.guild, event.member ?: return)
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val number = event.getOption("song")?.asString?.toIntOrNull()
            ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        select(number, audioPlayer, event.guild ?: return, event.member ?: return, event)
    }

    private fun select(
        number: Int,
        audioPlayer: GuildMusicManager,
        guild: Guild,
        member: Member,
        ifFromSlashCommandEvent: SlashCommandEvent? = null
    ) {
        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (member.voiceState?.channel == guild.selfMember.voiceState?.channel) {
                val queue = audioPlayer.scheduler.queue.toMutableList()
                val track = queue.getOrNull(number - 1)
                    ?: throw CommandException("You have specified a wrong number!")

                queue -= track

                track.userData = track.userData.cast<TrackUserData>().copy(ifFromSlashCommand = ifFromSlashCommandEvent)

                audioPlayer.player.startTrack(track, false)
                audioPlayer.scheduler.queue = ConcurrentLinkedQueue(queue)
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("The current queue is empty!")
    }
}