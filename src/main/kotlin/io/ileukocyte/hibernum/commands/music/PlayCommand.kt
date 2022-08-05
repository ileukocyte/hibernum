package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.audio.PLAYER_MANAGER
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.canJoinFromAnother
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.YOUTUBE_LINK_REGEX

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class PlayCommand : TextCommand {
    override val name = "play"
    override val description = "Plays the provided media in a voice channel"
    override val fullDescription = description +
            "\n\n**Available search prefixes**:" +
            "\n\u2022 `ytsearch:` (YouTube)" +
            "\n\u2022 `scsearch:` (SoundCloud)"
    override val aliases = setOf("p")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A link or a search term"),
        OptionData(OptionType.ATTACHMENT, "attachment", "The media file to play"),
    )
    override val usages = setOf(
        setOf("query".toClassicTextUsage()),
        setOf("file".toClassicTextUsage()),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> event.guild.selfMember.voiceState?.channel == vc }
            val urls = setOf(args).filterNotNull().takeUnless { s -> s.isEmpty() }
                ?: event.message.attachments.map { a -> a.proxyUrl }.takeUnless { l -> l.isEmpty() }
                ?: throw NoArgumentsException

            val ytplay = CommandHandler.firstIsInstance<YouTubePlayCommand>().name

            event.guild.audioPlayer?.let { musicManager ->
                if (channel?.canJoinFromAnother(event.member ?: return) == false) {
                    throw CommandException("The bot cannot leave another voice channel " +
                            "unless it is playing no track, you have the permission to move members, " +
                            "or the current voice channel is empty!")
                }

                channel?.let { event.guild.audioManager.openAudioConnection(channel) }

                for (url in urls) {
                    PLAYER_MANAGER.loadItemOrdered(musicManager, url, object : AudioLoadResultHandler {
                        override fun trackLoaded(track: AudioTrack) {
                            val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                            track.userData = TrackUserData(
                                event.author,
                                event.guildChannel,
                                thumbnail,
                                announceQueueing = musicManager.player.playingTrack !== null,
                                isFirstToPlay = musicManager.player.playingTrack === null,
                            )

                            musicManager.scheduler += track
                        }

                        override fun playlistLoaded(playlist: AudioPlaylist) {
                            val playlistName = if (!playlist.isSearchResult) {
                                url.maskedLink(playlist.name
                                    .replace('[', '(')
                                    .replace(']', ')'))
                            } else {
                                playlist.name.surroundWith('"')
                            }

                            for (track in playlist.tracks) {
                                val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                    ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                track.userData = TrackUserData(
                                    event.author,
                                    event.guildChannel,
                                    thumbnail,
                                    isFirstToPlay = musicManager.player.playingTrack === null,
                                )

                                musicManager.scheduler += track
                            }

                            event.channel.sendSuccess("The $playlistName playlist " +
                                    "has been added to the queue!").queue()
                        }

                        override fun noMatches() =
                            event.channel.sendFailure("No results have been found by the query!") {
                                text = "Try using the \"$ytplay\" command instead!"
                            }.queue()

                        override fun loadFailed(exception: FriendlyException) =
                            event.channel.sendFailure("The track cannot be played!").queue()
                    })
                }
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return

        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> guild.selfMember.voiceState?.channel == vc }
            val url = event.getOption("attachment")?.asAttachment?.proxyUrl
                ?: event.getOption("query")?.asString
                ?: throw NoArgumentsException

            val ytplay = CommandHandler.firstIsInstance<YouTubePlayCommand>().name

            val deferred = event.deferReply().await()

            guild.audioPlayer?.let { musicManager ->
                if (channel?.canJoinFromAnother(event.member ?: return) == false) {
                    throw CommandException("The bot cannot leave another voice channel " +
                            "unless it is playing no track, you have the permission to move members, " +
                            "or the current voice channel is empty!")
                }

                channel?.let { guild.audioManager.openAudioConnection(channel) }

                PLAYER_MANAGER.loadItemOrdered(musicManager, url, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                            ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                        track.userData = TrackUserData(
                            event.user,
                            event.guildChannel,
                            thumbnail,
                            announceQueueing = musicManager.player.playingTrack !== null,
                            isFirstToPlay = musicManager.player.playingTrack === null,
                            ifFromSlashCommand = deferred,
                        )

                        musicManager.scheduler += track
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        val playlistName = if (!playlist.isSearchResult) {
                            url.maskedLink(playlist.name
                                .replace('[', '(')
                                .replace(']', ')'))
                        } else {
                            playlist.name.surroundWith('"')
                        }

                        val embed = defaultEmbed(
                            desc = "The $playlistName playlist has been added to the queue!",
                            type = EmbedType.SUCCESS,
                        )

                        deferred.editOriginalEmbeds(embed).queue(null) {
                            event.channel.sendMessageEmbeds(embed).queue()
                        }

                        for (track in playlist.tracks) {
                            val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                                ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                            track.userData = TrackUserData(
                                event.user,
                                event.guildChannel,
                                thumbnail,
                                isFirstToPlay = musicManager.player.playingTrack === null,
                            )

                            musicManager.scheduler += track
                        }
                    }

                    override fun noMatches() =
                        deferred.setFailureEmbed("No results have been found by the query!") {
                            text = "Try using the \"$ytplay\" command instead!"
                        }.queue(null) {
                            event.channel.sendFailure("No results have been found by the query!") {
                                text = "Try using the \"$ytplay\" command instead!"
                            }.queue()
                        }

                    override fun loadFailed(exception: FriendlyException) =
                        deferred.setFailureEmbed("The track cannot be played!").queue(null) {
                            event.channel.sendFailure("The track cannot be played!").queue()
                        }
                })
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }
}