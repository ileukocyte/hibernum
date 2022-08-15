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

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.*

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
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
        OptionData(OptionType.ATTACHMENT, "attachment", "The media file to play or a queue JSON file"),
    )
    override val usages = setOf(
        defaultUsageGroupOf("media link/search query"),
        defaultUsageGroupOf("media file"),
        defaultUsageGroupOf("queue JSON file"),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            val channel = it.takeUnless { vc -> event.guild.selfMember.voiceState?.channel == vc }

            val queue = if (event.message.attachments.none { a -> a.fileExtension == "json" }) {
                val urls = setOf(args).filterNotNull().takeUnless { s -> s.isEmpty() }
                    ?: event.message.attachments.map { a -> a.proxyUrl }.takeUnless { l -> l.isEmpty() }
                    ?: throw NoArgumentsException

                HibernumQueue(
                    urls.map { u -> HibernumTrack(u, event.author, null) }.toSet(),
                    player = null,
                )
            } else {
                event.message.attachments.first { a -> a.fileExtension == "json" }.proxy.download().await().use { s ->
                    val json = Json.parseToJsonElement(String(s.readBytes())).jsonObject

                    handleQueueJson(
                        json = json,
                        guild = event.guild,
                        fallbackRequester = event.author,
                    )
                }
            }

            val ytplay = CommandHandler.firstIsInstance<YouTubePlayCommand>().name

            event.guild.audioPlayer?.let { musicManager ->
                if (channel?.canJoinFromAnother(event.member ?: return) == false) {
                    throw CommandException("The bot cannot leave another voice channel " +
                            "unless it is playing no track, you have the permission to move members, " +
                            "or the current voice channel is empty!")
                }

                channel?.let { event.guild.audioManager.openAudioConnection(channel) }

                queue.player?.let { player ->
                    musicManager.player.isPaused = player.isPaused
                    //musicManager.player.volume = player.volume
                    musicManager.scheduler.loopMode = player.loopMode
                }

                if (queue.tracks.size > 1) {
                    val items = queue.tracks.mapNotNull { track ->
                        try {
                            PLAYER_MANAGER.loadItemAsync(musicManager, track.url) to (track.thumbnail to track.requester)
                        } catch (_: FriendlyException) {
                            null
                        }
                    }.takeUnless { l -> l.isEmpty() } ?: throw CommandException("No track cannot be loaded!")
                    val map = items.toMap()

                    val tracks = map.keys.filterIsInstance<AudioTrack>()
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
                            thumbnail = map[track]?.first ?: thumbnail,
                            requester = map[track]?.second ?: event.author,
                            channel = event.guildChannel,
                            isFirstToPlay = musicManager.player.playingTrack === null,
                        )

                        musicManager.scheduler += track
                    }

                    event.channel.sendSuccess("The custom playlist has been added to the queue!")
                        .queue()
                } else {
                    try {
                        val track = queue.tracks.first()

                        when (val item = PLAYER_MANAGER.loadItemAsync(musicManager, track.url)) {
                            is AudioTrack -> {
                                val thumbnail = YOUTUBE_LINK_REGEX.find(item.info.uri)?.groups?.get(3)?.value
                                    ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                item.userData = TrackUserData(
                                    track.requester,
                                    event.guildChannel,
                                    track.thumbnail ?: thumbnail,
                                    announceQueueing = musicManager.player.playingTrack !== null,
                                    isFirstToPlay = musicManager.player.playingTrack === null,
                                )

                                musicManager.scheduler += item
                            }
                            is AudioPlaylist -> {
                                val playlistName = if (!item.isSearchResult) {
                                    track.url.maskedLink(
                                        item.name
                                            .replace('[', '(')
                                            .replace(']', ')')
                                    )
                                } else {
                                    item.name.surroundWith('"')
                                }

                                for (playlistTrack in item.tracks) {
                                    if (event.guild.selfMember.voiceState?.inAudioChannel() == false) {
                                        musicManager.stop()

                                        return
                                    }

                                    val thumbnail = YOUTUBE_LINK_REGEX.find(playlistTrack.info.uri)?.groups?.get(3)?.value
                                        ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                    playlistTrack.userData = TrackUserData(
                                        event.author,
                                        event.guildChannel,
                                        thumbnail,
                                        isFirstToPlay = musicManager.player.playingTrack === null,
                                    )

                                    musicManager.scheduler += playlistTrack
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

            val attachment = event.getOption("attachment")?.asAttachment
            val queue = if (attachment?.fileExtension != "json") {
                val url = event.getOption("attachment")?.asAttachment?.proxyUrl
                    ?: event.getOption("query")?.asString
                    ?: throw NoArgumentsException

                HibernumQueue(setOf(
                    HibernumTrack(
                        url = url,
                        requester = event.user,
                        thumbnail = null,
                    )
                ), null)
            } else {
                attachment.proxy.download().await().use { s ->
                    val json = Json.parseToJsonElement(String(s.readBytes())).jsonObject

                    handleQueueJson(
                        json = json,
                        guild = guild,
                        fallbackRequester = event.user,
                    )
                }
            }

            val ytplay = CommandHandler.firstIsInstance<YouTubePlayCommand>().name

            guild.audioPlayer?.let { musicManager ->
                if (channel?.canJoinFromAnother(event.member ?: return) == false) {
                    throw CommandException("The bot cannot leave another voice channel " +
                            "unless it is playing no track, you have the permission to move members, " +
                            "or the current voice channel is empty!")
                }

                val deferred = event.deferReply().await()

                channel?.let { guild.audioManager.openAudioConnection(channel) }

                queue.player?.let { player ->
                    musicManager.player.isPaused = player.isPaused
                    //musicManager.player.volume = player.volume
                    musicManager.scheduler.loopMode = player.loopMode
                }

                if (queue.tracks.size > 1) {
                    val items = queue.tracks.mapNotNull { track ->
                        try {
                            PLAYER_MANAGER.loadItemAsync(musicManager, track.url) to (track.thumbnail to track.requester)
                        } catch (_: FriendlyException) {
                            null
                        }
                    }.takeUnless { l -> l.isEmpty() } ?: throw CommandException("No track cannot be loaded!")
                    val map = items.toMap()

                    val tracks = map.keys.filterIsInstance<AudioTrack>()
                    val playlist = BasicAudioPlaylist(
                        "Custom Playlist",
                        tracks,
                        tracks.first(),
                        false,
                    )

                    for (track in playlist.tracks) {
                        if (guild.selfMember.voiceState?.inAudioChannel() == false) {
                            musicManager.stop()

                            deferred.deleteOriginal().queue(null) {}

                            return
                        }

                        val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                            ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                        track.userData = TrackUserData(
                            thumbnail = map[track]?.first ?: thumbnail,
                            requester = map[track]?.second ?: event.user,
                            channel = event.guildChannel,
                            isFirstToPlay = musicManager.player.playingTrack === null,
                        )

                        musicManager.scheduler += track
                    }

                    deferred.setSuccessEmbed("The custom playlist has been added to the queue!").queue(null) {
                        event.channel.sendSuccess("The custom playlist has been added to the queue!").queue()
                    }
                } else {
                    try {
                        val track = queue.tracks.first()

                        when (val item = PLAYER_MANAGER.loadItemAsync(musicManager, track.url)) {
                            is AudioTrack -> {
                                val thumbnail = YOUTUBE_LINK_REGEX.find(item.info.uri)?.groups?.get(3)?.value
                                    ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                item.userData = TrackUserData(
                                    track.requester,
                                    event.guildChannel,
                                    track.thumbnail ?: thumbnail,
                                    announceQueueing = musicManager.player.playingTrack !== null,
                                    isFirstToPlay = musicManager.player.playingTrack === null,
                                    ifFromSlashCommand = deferred,
                                )

                                musicManager.scheduler += item
                            }

                            is AudioPlaylist -> {
                                val playlistName = if (!item.isSearchResult) {
                                    track.url.maskedLink(
                                        item.name
                                            .replace('[', '(')
                                            .replace(']', ')')
                                    )
                                } else {
                                    item.name.surroundWith('"')
                                }

                                val embed = defaultEmbed(
                                    desc = "The $playlistName playlist has been added to the queue!",
                                    type = EmbedType.SUCCESS,
                                )

                                for (playlistTrack in item.tracks) {
                                    if (guild.selfMember.voiceState?.inAudioChannel() == false) {
                                        musicManager.stop()

                                        deferred.deleteOriginal().queue(null) {}

                                        return
                                    }

                                    val thumbnail = YOUTUBE_LINK_REGEX.find(playlistTrack.info.uri)?.groups?.get(3)?.value
                                        ?.let { id -> "https://i3.ytimg.com/vi/$id/hqdefault.jpg" }

                                    playlistTrack.userData = TrackUserData(
                                        event.user,
                                        event.guildChannel,
                                        thumbnail,
                                        isFirstToPlay = musicManager.player.playingTrack === null,
                                    )

                                    musicManager.scheduler += playlistTrack
                                }

                                deferred.editOriginalEmbeds(embed).queue(null) {
                                    event.channel.sendMessageEmbeds(embed).queue()
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
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    private suspend fun handleQueueJson(
        json: JsonObject,
        guild: Guild,
        fallbackRequester: User,
    ): HibernumQueue {
        val exception = CommandException("The provided queue file is invalid!")

        val player = json["player"]?.jsonObject ?: throw exception

        //val volume = min(100, max(0, player["volume"]?.jsonPrimitive?.intOrNull ?: 100))
        val isPaused = player["is_paused"]?.jsonPrimitive?.booleanOrNull ?: false
        val loopMode = player["looping_mode"]?.jsonPrimitive?.contentOrNull?.let {
            try {
                LoopMode.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: LoopMode.DISABLED

        suspend fun handleTrack(json: JsonObject) = HibernumTrack(
            json["url"]?.jsonPrimitive?.content ?: throw exception,
            try {
                guild.retrieveMemberById(json["requester_id"]?.jsonPrimitive?.long ?: throw exception)
                    .await()
                    .user
            } catch (_: ErrorResponseException) {
                fallbackRequester
            },
            json["thumbnail"]?.jsonPrimitive?.contentOrNull,
        )

        val current = json["current_track"]?.jsonObject ?: throw exception
        val queue = json["queue"]?.jsonArray ?: throw exception

        return HibernumQueue(
            setOf(
                handleTrack(current),
                *queue.map { handleTrack(it.jsonObject) }.toTypedArray(),
            ),
            HibernumPlayer(isPaused, loopMode),
        )
    }

    private data class HibernumQueue(
        val tracks: Set<HibernumTrack>,
        val player: HibernumPlayer?,
    )

    private data class HibernumTrack(
        val url: String,
        val requester: User,
        val thumbnail: String?,
    )

    private data class HibernumPlayer(
        val isPaused: Boolean,
        //val volume: Int,
        val loopMode: LoopMode,
    )
}