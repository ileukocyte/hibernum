package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.GuildMusicManager
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.getProgressBar
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.capitalizeAll
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.utils.asDuration

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class PlayingCommand : Command {
    override val name = "playing"
    override val description = "Shows information about the track that is currently playing"
    override val aliases = setOf("np", "nowplaying", "playingnow")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            event.channel.sendMessageEmbeds(playingEmbed(event.jda, audioPlayer)).queue()
        } else event.channel.sendFailure("No track is currently playing!").queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        if (audioPlayer.player.playingTrack !== null) {
            event.replyEmbeds(playingEmbed(event.jda, audioPlayer)).queue()
        } else event.replyFailure("No track is currently playing!")
            .setEphemeral(true)
            .queue()
    }

    private fun playingEmbed(jda: JDA, musicManager: GuildMusicManager) = buildEmbed {
        val track = musicManager.player.playingTrack
        val requester = (track.userData as? Long)?.let { jda.getUserById(it) }

        color = Immutable.SUCCESS
        thumbnail = jda.selfUser.effectiveAvatarUrl

        field {
            title = "Playing Now"
            description = "**[${track.info.title}](${track.info.uri})**"
        }

        requester?.let {
            field {
                title = "Track Requester"
                description = it.asMention
            }
        }

        field {
            title = "Looping Mode"
            description = musicManager.scheduler.loopMode.name.capitalizeAll()
        }

        field {
            title = "Volume"
            description = "${musicManager.player.volume}%"
        }

        field {
            val timeline = getProgressBar(
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