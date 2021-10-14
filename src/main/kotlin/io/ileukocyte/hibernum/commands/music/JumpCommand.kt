package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.hibernum.utils.TIME_CODE_REGEX
import io.ileukocyte.hibernum.utils.timeCodeToMillis

import kotlin.math.max

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class JumpCommand : Command {
    override val name = "jump"
    override val description = "Jumps to the specified time while playing the track"
    override val aliases = setOf("seek")
    override val usages = setOf(setOf("[rewind:/fast-forward:]time code"))
    override val options = setOf(
        OptionData(
            OptionType.STRING,
            "time",
            "The time to jump to (you can apply the prefixes \"rewind:\" or \"fast-forward:\" (e.g. rewind:1:00))",
            true,
        )
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (args ?: throw NoArgumentsException).takeIf {
                it.removePrefix("rewind:").removePrefix("fast-forward:") matches TIME_CODE_REGEX
            } ?: throw CommandException("You have provided an argument of a wrong format!")

            if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel)
                throw CommandException("You are not connected to the required voice channel!")

            if (audioPlayer.player.playingTrack.info.isStream)
                throw CommandException("The track cannot be sought since it is recognized as a stream!")

            val millis = timeCodeToMillis(time.removePrefix("rewind:").removePrefix("fast-forward:"))

            if (!time.startsWith("rewind:")) {
                if (time.startsWith("fast-forward:")) {
                    if (audioPlayer.player.playingTrack.position + millis > audioPlayer.player.playingTrack.duration)
                        throw CommandException("You have exceeded the track duration!")
                } else {
                    if (millis !in 0..audioPlayer.player.playingTrack.duration)
                        throw CommandException("You have provided a wrong time code for the track!")
                }
            }

            when {
                time.startsWith("rewind:") ->
                    audioPlayer.player.playingTrack.position =
                        max(0, audioPlayer.player.playingTrack.position - millis)
                time.startsWith("fast-forward:") ->
                    audioPlayer.player.playingTrack.position += millis
                else ->
                    audioPlayer.player.playingTrack.position = millis
            }

            event.channel.sendSuccess("Successfully jumped to the specified time!").queue()
        } else throw CommandException("No track is currently playing!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (event.getOption("time")?.asString ?: throw NoArgumentsException)
                .lowercase()
                .takeIf {
                    it.removePrefix("rewind:").removePrefix("fast-forward:") matches TIME_CODE_REGEX
                } ?: throw CommandException("You have provided an argument of a wrong format!")

            if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel)
                throw CommandException("You are not connected to the required voice channel!")

            if (audioPlayer.player.playingTrack.info.isStream)
                throw CommandException("The track cannot be sought since it is recognized as a stream!")

            val millis = timeCodeToMillis(time.removePrefix("rewind:").removePrefix("fast-forward:"))

            if (!time.startsWith("rewind:")) {
                if (time.startsWith("fast-forward:")) {
                    if (audioPlayer.player.playingTrack.position + millis > audioPlayer.player.playingTrack.duration)
                        throw CommandException("You have exceeded the track duration!")
                } else {
                    if (millis !in 0..audioPlayer.player.playingTrack.duration)
                        throw CommandException("You have provided a wrong time code for the track!")
                }
            }

            when {
                time.startsWith("rewind:") ->
                    audioPlayer.player.playingTrack.position =
                        max(0, audioPlayer.player.playingTrack.position - millis)
                time.startsWith("fast-forward:") ->
                    audioPlayer.player.playingTrack.position += millis
                else ->
                    audioPlayer.player.playingTrack.position = millis
            }

            event.replySuccess("Successfully jumped to the specified time!").queue()
        } else throw CommandException("No track is currently playing!")
    }
}

