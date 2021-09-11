package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.audio.PLAYER_MANAGER
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.hibernum.utils.YOUTUBE_LINK_REGEX

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
    override val usages = setOf("query", "file")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> event.guild.selfMember.voiceState?.channel == vc }
            val urls = setOf(args).filterNotNull().takeUnless { s -> s.isEmpty() }
                ?: event.message.attachments.map { a -> a.url }.takeUnless { l -> l.isEmpty() }
                ?: throw NoArgumentsException

            event.guild.audioPlayer?.let { musicManager ->
                channel?.let { event.guild.audioManager.openAudioConnection(channel) }

                for (url in urls) {
                    PLAYER_MANAGER.loadItemOrdered(musicManager, url, object : AudioLoadResultHandler {
                        override fun trackLoaded(track: AudioTrack) {
                            val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                            track.userData = TrackUserData(
                                event.author,
                                event.channel,
                                thumbnail,
                                announceQueued = musicManager.player.playingTrack !== null,
                                firstTrackPlaying = musicManager.player.playingTrack === null
                            )
                            musicManager.scheduler += track
                        }

                        override fun playlistLoaded(playlist: AudioPlaylist) {
                            for (track in playlist.tracks) {
                                val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                    ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                track.userData = TrackUserData(event.author, event.channel, thumbnail, firstTrackPlaying = musicManager.player.playingTrack === null)
                                musicManager.scheduler += track
                            }

                            event.channel.sendSuccess(
                                "${if (!playlist.isSearchResult) "[${playlist.name}]($url)" else "\"${playlist.name}\""} playlist " +
                                        "has been successfully added to the queue!"
                            ).queue()
                        }

                        override fun noMatches() =
                            event.channel.sendFailure("No results have been found by the query!") {
                                text = "Try using the \"ytplay\" command instead!"
                            }.queue()

                        override fun loadFailed(exception: FriendlyException) =
                            event.channel.sendFailure("The track cannot be played!").queue()
                    })
                }
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
                        val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                            ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                        track.userData = TrackUserData(
                            event.user,
                            event.channel,
                            thumbnail,
                            announceQueued = musicManager.player.playingTrack !== null,
                            firstTrackPlaying = musicManager.player.playingTrack === null,
                            ifFromSlashCommand = event
                        )
                        musicManager.scheduler += track
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        event.replySuccess(
                            "${if (!playlist.isSearchResult) "[${playlist.name}]($url)" else "\"${playlist.name}\""} playlist " +
                                    "has been successfully added to the queue!").queue()

                        for (track in playlist.tracks) {
                            val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                            track.userData = TrackUserData(event.user, event.channel, thumbnail, firstTrackPlaying = musicManager.player.playingTrack === null)
                            musicManager.scheduler += track
                        }
                    }

                    override fun noMatches() =
                        event.replyFailure("No results have been found by the query!") {
                            text = "Try using the \"ytplay\" command instead!"
                        }.queue()

                    override fun loadFailed(exception: FriendlyException) =
                        event.replyFailure("The track cannot be played!").queue()
                })
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }
}