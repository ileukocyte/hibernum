package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.audio.MusicContext
import io.ileukocyte.hibernum.audio.PLAYER_MANAGER
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class PlayCommand : Command {
    override val name = "play"
    override val description = "Plays the specified media in a voice channel"
    override val aliases = setOf("p")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A link or a search term", true))
    override val usages = setOf("query")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> event.guild.selfMember.voiceState?.channel == vc }
            val url = args ?: throw NoArgumentsException

            event.guild.audioPlayer?.let { musicManager ->
                channel?.let { event.guild.audioManager.openAudioConnection(channel) }

                PLAYER_MANAGER.loadItemOrdered(musicManager, url, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        CoroutineScope(MusicContext).launch {
                            event.channel.sendSuccess("[${track.info.title}](${track.info.uri}) " +
                                    "has been successfully added to queue!").queue()

                            track.userData = TrackUserData(event.author, event.channel)
                            musicManager.scheduler += track
                        }
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        event.channel.sendSuccess("${if (!playlist.isSearchResult) "[${playlist.name}]($url)" else playlist.name} " +
                                    "has been successfully added to queue!").queue()

                            for (track in playlist.tracks) {
                                track.userData = TrackUserData(event.author, event.channel)
                                musicManager.scheduler += track
                            }
                    }

                    override fun noMatches() =
                        throw CommandException("No results have been found by the query!")

                    override fun loadFailed(exception: FriendlyException) {
                        exception.printStackTrace()

                        throw CommandException("The track is unable to be played!")
                    }
                })
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return

        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> guild.selfMember.voiceState?.channel == vc }
            val url = event.getOption("query")?.asString ?: return@invoke

            guild.audioPlayer?.let { musicManager ->
                channel?.let { guild.audioManager.openAudioConnection(channel) }

                PLAYER_MANAGER.loadItemOrdered(musicManager, url, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        event.replySuccess("[${track.info.title}](${track.info.uri}) " +
                                "has been successfully added to queue!").queue()

                        track.userData = TrackUserData(event.user, event.channel)
                        musicManager.scheduler += track
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        event.replySuccess(
                            "${if (!playlist.isSearchResult) "[${playlist.name}]($url)" else playlist.name} " +
                                    "has been successfully added to queue!").queue()

                        for (track in playlist.tracks) {
                            track.userData = TrackUserData(event.user, event.channel)
                            musicManager.scheduler += track
                        }
                    }

                    override fun noMatches() =
                        throw CommandException("No results have been found by the query!")

                    override fun loadFailed(exception: FriendlyException) =
                        throw CommandException("The track is unable to be played!")
                })
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }
}