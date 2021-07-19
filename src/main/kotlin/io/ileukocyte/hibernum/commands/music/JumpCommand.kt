package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.hibernum.utils.TIME_CODE_REGEX
import io.ileukocyte.hibernum.utils.timeCodeToMillis

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class JumpCommand : Command {
    override val name = "jump"
    override val description = "Jumps to the specified time while playing the track"
    override val usages = setOf("time code")
    override val options = setOf(
        OptionData(OptionType.STRING, "time", "The time to jump to", true)
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (args ?: throw NoArgumentsException).takeIf { it matches TIME_CODE_REGEX }
                ?: throw CommandException("You have entered an argument of a wrong format!")

            if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel) {
                throw CommandException("You are not connected to the required voice channel!")
            } else {
                val millis = timeCodeToMillis(time)

                if (millis !in 0..audioPlayer.player.playingTrack.duration)
                    throw CommandException("You have specified a wrong time code for the track!")

                audioPlayer.player.playingTrack.position = millis

                event.channel.sendSuccess("Successfully jumped to the specified time!").queue()
            }
        } else throw CommandException("No track is currently playing!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            val time = (event.getOption("time") ?.asString?: throw NoArgumentsException)
                .takeIf { it matches TIME_CODE_REGEX }
                ?: throw CommandException("You have entered an argument of a wrong format!")

            if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel) {
                throw CommandException("You are not connected to the required voice channel!")
            } else {
                val millis = timeCodeToMillis(time)

                if (millis !in 0..audioPlayer.player.playingTrack.duration)
                    throw CommandException("You have specified a wrong time code for the track!")

                audioPlayer.player.playingTrack.position = millis

                event.replySuccess("Successfully jumped to the specified time!").queue()
            }
        } else throw CommandException("No track is currently playing!")
    }
}

