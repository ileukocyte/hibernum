package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist

import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.defaultUsageGroupOf
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
        defaultUsageGroupOf("query"),
        defaultUsageGroupOf("file"),
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

                if (urls.size > 1) {
                    val items = urls.mapNotNull { url ->
                        try {
                            PLAYER_MANAGER.loadItemAsync(musicManager, url)
                        } catch (_: FriendlyException) {
                            null
                        }
                    }.takeUnless { l -> l.isEmpty() } ?: throw CommandException("No track cannot be loaded!")

                    val tracks = items.filterIsInstance<AudioTrack>()
                    val playlist = BasicAudioPlaylist(
                        "Custom Playlist",
                        tracks,
                        tracks.first(),
                        false,
                    )

                    for (track in playlist.tracks) {
                        if (event.guild.selfMember.voiceState?.inAudioChannel() == false) {
                            musicManager.stop()

                            return
                        }

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

                    event.channel.sendSuccess("The custom playlist has been added to the queue!")
                        .queue()
                } else {
                    try {
                        when (val item = PLAYER_MANAGER.loadItemAsync(musicManager, urls.first())) {
                            is AudioTrack -> {
                                val thumbnail = YOUTUBE_LINK_REGEX.find(item.info.uri)?.groups?.get(3)?.value
                                    ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                item.userData = TrackUserData(
                                    event.author,
                                    event.guildChannel,
                                    thumbnail,
                                    announceQueueing = musicManager.player.playingTrack !== null,
                                    isFirstToPlay = musicManager.player.playingTrack === null,
                                )

                                musicManager.scheduler += item
                            }
                            is AudioPlaylist -> {
                                val playlistName = if (!item.isSearchResult) {
                                    urls.first().maskedLink(
                                        item.name
                                            .replace('[', '(')
                                            .replace(']', ')')
                                    )
                                } else {
                                    item.name.surroundWith('"')
                                }

                                for (track in item.tracks) {
                                    if (event.guild.selfMember.voiceState?.inAudioChannel() == false) {
                                        musicManager.stop()

                                        return
                                    }

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

                                event.channel.sendSuccess(
                                    "The $playlistName playlist " +
                                            "has been added to the queue!"
                                ).queue()
                            }
                            null -> event.channel.sendFailure("No results have been found by the query!") {
                                text = "Try using the \"$ytplay\" command instead!"
                            }.queue()
                        }
                    } catch (_: FriendlyException) {
                        event.channel.sendFailure("The track cannot be played!").queue()
                    }
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

                try {
                    when (val item = PLAYER_MANAGER.loadItemAsync(musicManager, url)) {
                        is AudioTrack -> {
                            val thumbnail = YOUTUBE_LINK_REGEX.find(item.info.uri)?.groups?.get(3)?.value
                                ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                            item.userData = TrackUserData(
                                event.user,
                                event.guildChannel,
                                thumbnail,
                                announceQueueing = musicManager.player.playingTrack !== null,
                                isFirstToPlay = musicManager.player.playingTrack === null,
                                ifFromSlashCommand = deferred,
                            )

                            musicManager.scheduler += item
                        }
                        is AudioPlaylist -> {
                            val playlistName = if (!item.isSearchResult) {
                                url.maskedLink(item.name
                                    .replace('[', '(')
                                    .replace(']', ')'))
                            } else {
                                item.name.surroundWith('"')
                            }

                            val embed = defaultEmbed(
                                desc = "The $playlistName playlist has been added to the queue!",
                                type = EmbedType.SUCCESS,
                            )

                            deferred.editOriginalEmbeds(embed).queue(null) {
                                event.channel.sendMessageEmbeds(embed).queue()
                            }

                            for (track in item.tracks) {
                                if (guild.selfMember.voiceState?.inAudioChannel() == false) {
                                    musicManager.stop()

                                    return
                                }

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
                        null -> deferred.setFailureEmbed("No results have been found by the query!") {
                            text = "Try using the \"$ytplay\" command instead!"
                        }.queue(null) {
                            event.channel.sendFailure("No results have been found by the query!") {
                                text = "Try using the \"$ytplay\" command instead!"
                            }.queue()
                        }
                    }
                } catch (_: FriendlyException) {
                    deferred.setFailureEmbed("The track cannot be played!").queue(null) {
                        event.channel.sendFailure("The track cannot be played!").queue()
                    }
                }
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }
}