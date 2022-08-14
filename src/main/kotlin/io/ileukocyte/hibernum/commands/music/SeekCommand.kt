package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.defaultUsageGroupOf
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.hibernum.utils.TIME_CODE_REGEX
import io.ileukocyte.hibernum.utils.durationToMillis
import io.ileukocyte.hibernum.utils.timeCodeToMillis

import kotlin.math.max

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class SeekCommand : TextCommand {
    override val name = "seek"
    override val description = "Jumps to the specified time while playing the track"
    override val aliases = setOf("jump")
    override val usages = setOf(defaultUsageGroupOf("[r(ewind):/f(ast-forward):]time code"))
    override val options = setOf(
        OptionData(
            OptionType.STRING,
            "time",
            "The time to jump to (e.g. 1:30 (or 1m30s) sets the exact time)",
            true,
        ),
        OptionData(OptionType.STRING, "mode", "The direction to jump to (backwards/forwards)")
            .addChoices(
                Choice("Rewind", "r"),
                Choice("Fast Forward", "f"),
            ),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (args ?: throw NoArgumentsException).takeIf {
                val noPrefixes = it
                    .removePrefix("rewind:")
                    .removePrefix("r:")
                    .removePrefix("fast-forward:")
                    .removePrefix("f:")

                noPrefixes matches TIME_CODE_REGEX
                        || "([1-9]\\d*)([smh])".toRegex().containsMatchIn(noPrefixes)
            } ?: throw CommandException("You have provided an argument of a wrong format!")

            if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel) {
                throw CommandException("You are not connected to the required voice channel!")
            }

            if (audioPlayer.player.playingTrack.info.isStream) {
                throw CommandException("The track cannot be sought since it is recognized as a stream!")
            }

            val millis = time
                .removePrefix("rewind:")
                .removePrefix("r:")
                .removePrefix("fast-forward:")
                .removePrefix("f:")
                .let {
                    if (it matches TIME_CODE_REGEX) {
                        timeCodeToMillis(it)
                    } else {
                        durationToMillis(it)
                    }
                }

            if (!time.startsWith("rewind:") && !time.startsWith("r:")) {
                if (time.startsWith("fast-forward:") || time.startsWith("f:")) {
                    if (audioPlayer.player.playingTrack.position + millis > audioPlayer.player.playingTrack.duration) {
                        throw CommandException("You have exceeded the track duration!")
                    }
                } else {
                    if (millis !in 0..audioPlayer.player.playingTrack.duration) {
                        throw CommandException("You have provided a wrong time code for the track!")
                    }
                }
            }

            when {
                time.startsWith("rewind:") || time.startsWith("r:") ->
                    audioPlayer.player.playingTrack.position =
                        max(0, audioPlayer.player.playingTrack.position - millis)
                time.startsWith("fast-forward:") || time.startsWith("f:") ->
                    audioPlayer.player.playingTrack.position += millis
                else ->
                    audioPlayer.player.playingTrack.position = millis
            }

            event.channel.sendSuccess("Successfully jumped to the specified time!").queue()
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (event.getOption("time")?.asString ?: throw NoArgumentsException)
                .lowercase()
                .takeIf { it matches TIME_CODE_REGEX || "([1-9]\\d*)([smh])".toRegex().containsMatchIn(it) }
                ?: throw CommandException("You have provided an argument of a wrong format!")
            val mode = event.getOption("mode")?.asString.orEmpty()

            if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel) {
                throw CommandException("You are not connected to the required voice channel!")
            }

            if (audioPlayer.player.playingTrack.info.isStream) {
                throw CommandException("The track cannot be sought since it is recognized as a stream!")
            }

            val millis = if (time matches TIME_CODE_REGEX) {
                timeCodeToMillis(time)
            } else {
                durationToMillis(time)
            }

            if (mode != "r") {
                if (mode == "f") {
                    if (audioPlayer.player.playingTrack.position + millis > audioPlayer.player.playingTrack.duration) {
                        throw CommandException("You have exceeded the track duration!")
                    }
                } else {
                    if (millis !in 0..audioPlayer.player.playingTrack.duration) {
                        throw CommandException("You have provided a wrong time code for the track!")
                    }
                }
            }

            when (mode) {
                "r" -> audioPlayer.player.playingTrack.position =
                    max(0, audioPlayer.player.playingTrack.position - millis)
                "f" -> audioPlayer.player.playingTrack.position += millis
                else -> audioPlayer.player.playingTrack.position = millis
            }

            event.replySuccess("Successfully jumped to the specified time!").queue()
        } else {
            throw CommandException("No track is currently playing!")
        }
    }
}

