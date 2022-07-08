package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.GuildMusicManager
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.getEmbedProgressBar
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.utils.asDuration

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

import org.jetbrains.kotlin.utils.addToStdlib.cast

class NowPlayingCommand : Command {
    override val name = "nowplaying"
    override val description = "Shows information about the track that is currently playing"
    override val aliases = setOf("np", "now", "playing", "playingnow", "playing-now")

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        event.channel.sendMessageEmbeds(playingEmbed(event.jda, audioPlayer, track)).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        event.replyEmbeds(playingEmbed(event.jda, audioPlayer, track)).queue()
    }

    private fun playingEmbed(jda: JDA, musicManager: GuildMusicManager, track: AudioTrack) = buildEmbed {
        val trackData = track.userData.cast<TrackUserData>()

        color = Immutable.SUCCESS
        thumbnail = trackData.thumbnail ?: jda.selfUser.effectiveAvatarUrl

        field {
            title = "Playing Now"
            description = "**[${track.info.title}](${track.info.uri})**"
        }

        field {
            title = "Track Requester"
            description = trackData.user.asMention
        }

        field {
            title = "Looping Mode"
            description = musicManager.scheduler.loopMode.toString()
            isInline = true
        }

        field {
            title = "Volume"
            description = "${musicManager.player.volume}%"
            isInline = true
        }

        field {
            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration
            )

            title = "Duration"
            description = "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        author {
            name = "HiberPlayer"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }
    }
}