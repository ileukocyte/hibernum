package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.*

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.defaultUsageGroupOf
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.extensions.EmbedType
import io.ileukocyte.hibernum.utils.*

import kotlinx.coroutines.withContext

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class YouTubePlayCommand : TextCommand {
    override val name = "ytplay"
    override val description = "Searches a YouTube video or playlist by the provided query and plays it in a voice channel"
    override val aliases = setOf("yp", "ytp", "youtube-play")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A search term or a YouTube link", true),
        OptionData(OptionType.STRING, "kind", "A kind of result (videos or playlists)")
            .addChoice("Videos", "videos")
            .addChoice("Playlists", "playlists"),
    )
    override val usages = setOf(defaultUsageGroupOf("query"))
    override val cooldown = 5L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        sendMenu(
            event.member ?: return,
            event.channel as GuildMessageChannel,
            args ?: throw NoArgumentsException,
        )
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()

        sendMenu(
            event.member ?: return,
            event.channel as GuildMessageChannel,
            event.getOption("query")?.asString ?: return,
            deferred,
            event.getOption("kind")?.asString ?: "videos",
        )
    }

    override suspend fun invoke(event: SelectMenuInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            val deferred = event.deferEdit().await()

            if (event.selectedOptions.firstOrNull()?.value == "exit") {
                deferred.deleteOriginal().queue()

                return
            }

            val entity = event.selectedOptions.firstOrNull()
                ?.value
                ?.applyIf(id.last() == "playlists") { "https://www.youtube.com/playlist?list=$this" }
                ?: return

            play(entity, event.channel as GuildMessageChannel, event.user, id.last() == "videos", deferred)
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun sendMenu(
        member: Member,
        textChannel: GuildMessageChannel,
        query: String,
        ifFromAnInteraction: InteractionHook? = null,
        kind: String = "videos",
    ) {
        member.voiceState?.channel?.let { vc ->
            val channel = vc.takeUnless { textChannel.guild.selfMember.voiceState?.channel == vc }

            if (channel?.canJoinFromAnother(member) == false) {
                val error = "The bot cannot leave another voice channel unless " +
                        "it is playing no track, you have the permission to move members, " +
                        "or the current voice channel is empty!"

                ifFromAnInteraction?.let {
                    try {
                        it.setFailureEmbed(error).await()

                        return
                    } catch (_: ErrorResponseException) {
                        throw CommandException(error)
                    }
                } ?: throw CommandException(error)
            }

            channel?.let { textChannel.guild.audioManager.openAudioConnection(channel) }

            if (query matches YOUTUBE_LINK_REGEX) {
                play(query, textChannel, member.user, false, ifFromAnInteraction)

                return
            }

            if (kind == "videos") {
                val videos = withContext(MusicContext) { searchVideos(query) }

                if (videos.isEmpty()) {
                    val error = "No results have been found by the query!"

                    ifFromAnInteraction?.let {
                        try {
                            it.setFailureEmbed(error).await()

                            return
                        } catch (_: ErrorResponseException) {
                            throw CommandException(error)
                        }
                    } ?: throw CommandException(error)
                }

                val menu by lazy {
                    val options = videos.map {
                        SelectOption.of(
                            it.snippet.title.limitTo(SelectOption.LABEL_MAX_LENGTH),
                            it.id,
                        ).withDescription(
                            "${it.snippet.channelTitle} \u2022 ${asDuration(it.contentDetails.durationInMillis)}"
                                .limitTo(SelectOption.DESCRIPTION_MAX_LENGTH)
                        )
                    }

                    SelectMenu.create("$interactionName-${member.user.idLong}-videos")
                        .addOptions(
                            *options.toTypedArray(),
                            SelectOption.of("Exit", "exit")
                                .withEmoji(Emoji.fromUnicode("\u274C")),
                        ).build()
                }

                val embed = buildEmbed {
                    color = Immutable.SUCCESS
                    description = "Select the video you want to play!"
                }

                ifFromAnInteraction?.let {
                    it.editOriginalEmbeds(embed).setActionRow(menu).queue(null) {
                        textChannel.sendMessageEmbeds(embed).setActionRow(menu).queue()
                    }
                } ?: textChannel.sendMessageEmbeds(embed).setActionRow(menu).queue()
            } else {
                val playlists = withContext(MusicContext) { searchPlaylists(query) }

                if (playlists.isEmpty()) {
                    val error = "No results have been found by the query!"

                    ifFromAnInteraction?.let {
                        try {
                            it.setFailureEmbed(error).await()

                            return
                        } catch (_: ErrorResponseException) {
                            throw CommandException(error)
                        }
                    } ?: throw CommandException(error)
                }

                val menu by lazy {
                    val options = playlists.map {
                        val count = it.contentDetails.itemCount
                            .run { "$this " + "video".singularOrPlural(this) }

                        SelectOption.of(
                            it.snippet.title.limitTo(SelectOption.LABEL_MAX_LENGTH),
                            it.id,
                        ).withDescription(
                            "${it.snippet.channelTitle} \u2022 $count"
                                .limitTo(SelectOption.DESCRIPTION_MAX_LENGTH)
                        )
                    }

                    SelectMenu.create("$interactionName-${member.user.idLong}-playlists")
                        .addOptions(
                            *options.toTypedArray(),
                            SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
                        ).build()
                }

                val embed = buildEmbed {
                    color = Immutable.SUCCESS
                    description = "Select the playlist you want to play!"
                }

                ifFromAnInteraction?.let {
                    it.editOriginalEmbeds(embed).setActionRow(menu).queue(null) {
                        textChannel.sendMessageEmbeds(embed).setActionRow(menu).queue()
                    }
                } ?: textChannel.sendMessageEmbeds(embed).setActionRow(menu).queue()
            }
        } ?: ifFromAnInteraction?.let {
            try {
                it.setFailureEmbed("You are not connected to a voice channel!").await()

                return
            } catch (_: ErrorResponseException) {
                throw CommandException("You are not connected to a voice channel!")
            }
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    private suspend fun play(
        query: String,
        channel: GuildMessageChannel,
        user: User,
        isId: Boolean,
        ifFromSlashCommand: InteractionHook? = null,
    ) {
        val musicManager = channel.guild.audioPlayer ?: return

        try {
            when (val item = PLAYER_MANAGER.loadItemAsync(
                musicManager,
                "https://www.youtube.com/watch?v=".takeIf { isId }.orEmpty() + query,
            )) {
                is AudioTrack -> {
                    val id = YOUTUBE_LINK_REGEX.find(query)?.groups?.get(3)?.value ?: query

                    item.userData = TrackUserData(
                        user,
                        channel,
                        "https://i3.ytimg.com/vi/$id/hqdefault.jpg",
                        announceQueueing = musicManager.player.playingTrack !== null,
                        isFirstToPlay = musicManager.player.playingTrack === null,
                        ifFromSlashCommand = ifFromSlashCommand,
                    )

                    musicManager.scheduler += item
                }
                is AudioPlaylist -> {
                    for (track in item.tracks) {
                        if (channel.guild.selfMember.voiceState?.inAudioChannel() == false) {
                            musicManager.stop()

                            return
                        }

                        val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                            ?.let { "https://i3.ytimg.com/vi/$it/hqdefault.jpg" }

                        track.userData = TrackUserData(
                            user,
                            channel,
                            thumbnail,
                            isFirstToPlay = musicManager.player.playingTrack === null,
                        )

                        musicManager.scheduler += track
                    }

                    val embed = defaultEmbed(
                        desc = "The [${item.name}]($query) playlist has been added to the queue!",
                        type = EmbedType.SUCCESS,
                    )

                    ifFromSlashCommand?.let {
                        it.editOriginalComponents().setEmbeds(embed).queue(null) {
                            channel.sendMessageEmbeds(embed).queue()
                        }
                    } ?: channel.sendMessageEmbeds(embed).queue()
                }
                null -> {
                    val embed = defaultEmbed(
                        desc = "No results have been found by the query!",
                        type = EmbedType.FAILURE,
                    )

                    ifFromSlashCommand?.let {
                        it.editOriginalComponents().setEmbeds(embed).queue(null) {
                            channel.sendMessageEmbeds(embed).queue()
                        }
                    } ?: channel.sendMessageEmbeds(embed).queue()
                }
            }
        } catch (_: FriendlyException) {
            val embed = defaultEmbed(
                desc = "The track cannot be played!",
                type = EmbedType.FAILURE,
            )

            ifFromSlashCommand?.let {
                it.editOriginalComponents().setEmbeds(embed).queue(null) {
                    channel.sendMessageEmbeds(embed).queue()
                }
            } ?: channel.sendMessageEmbeds(embed).queue()
        }
    }
}