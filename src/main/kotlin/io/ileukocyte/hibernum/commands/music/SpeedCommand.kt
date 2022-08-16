package io.ileukocyte.hibernum.commands.music

import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import kotlin.math.absoluteValue

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class SpeedCommand : SlashOnlyCommand {
    override val name = "speed"
    override val description = "Enables the speed and pitch filters on the player"
    override val fullDescription = "$description (or resets them in case no options are provided)"
    override val aliases = setOf("pitch")
    override val options = setOf(
        OptionData(OptionType.NUMBER, "speed", "The speed multiplier")
            .addChoice("0.25x", 0.25)
            .addChoice("0.5x", 0.5)
            .addChoice("0.75x", 0.75)
            .addChoice("1x", 1.0)
            .addChoice("1.25x", 1.25)
            .addChoice("1.5x", 1.5)
            .addChoice("1.75x", 1.75)
            .addChoice("2x", 2.0),
        OptionData(
            OptionType.NUMBER,
            "pitch",
            "The semitone offset (from –12 through +12, set to 0 for resetting)",
        ).setRequiredRange(-12, 12),
    )
    override val cooldown = 7L

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (Immutable.ENABLE_AUDIO_FILTERS_HANDLING || event.user.isBotDeveloper) {
            val audioPlayer = event.guild?.audioPlayer ?: return

            if (audioPlayer.player.playingTrack !== null) {
                if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                    if (audioPlayer.player.playingTrack.info.isStream) {
                        throw CommandException("No filters can be applied to a live stream!")
                    }

                    val speed = event.getOption("speed")?.asDouble
                    val pitch = event.getOption("pitch")?.asDouble

                    if (speed === null && pitch === null) {
                        audioPlayer.scheduler.pitchOffset.set(0.0)
                        audioPlayer.scheduler.speedRate.set(1.0)

                        audioPlayer.player.setFilterFactory(null)

                        event.replySuccess("The audio filters have been disabled!").queue()

                        return
                    }

                    if (speed !== null) {
                        audioPlayer.scheduler.speedRate.set(speed)
                    }

                    if (pitch !== null) {
                        audioPlayer.scheduler.pitchOffset.set(pitch)
                    }

                    audioPlayer.player.setFilterFactory { _, format, output ->
                        val filter = TimescalePcmAudioFilter(output, format.channelCount, format.sampleRate)

                        filter.speed = audioPlayer.scheduler.speedRate.get()
                        filter.setPitchSemiTones(audioPlayer.scheduler.pitchOffset.get().toDouble())

                        listOf(filter)
                    }

                    val pitchFormat = audioPlayer.scheduler.pitchOffset.get().let {
                        "${it.toDecimalFormat("+0.##;-0.##")} semitone"
                            .singularOrPlural(it.absoluteValue)
                            .applyIf(it == 0.0) { drop(1) }
                    }

                    event.replySuccess(
                        "The speed has been set to ${audioPlayer.scheduler.speedRate.get().toDecimalFormat("0.##x")} " +
                                "and the pitch offset has been set to $pitchFormat!"
                    ).queue()
                } else {
                    throw CommandException("You are not connected to the required voice channel!")
                }
            } else {
                throw CommandException("No track is currently playing!")
            }
        } else {
            throw CommandException("Audio filters cannot be used at the moment due to technical reasons!")
        }
    }
}