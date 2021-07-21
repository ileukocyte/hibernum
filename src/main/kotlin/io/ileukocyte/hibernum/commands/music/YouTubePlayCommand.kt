package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.*

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.MusicContext
import io.ileukocyte.hibernum.audio.PLAYER_MANAGER
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import kotlinx.coroutines.withContext

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu

class YouTubePlayCommand : Command {
    override val name = "ytplay"
    override val description = "Plays the specified YouTube video in a voice channel"
    override val aliases = setOf("yp", "ytp", "youtubeplay")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A link or a search term", true))
    override val usages = setOf("query")
    override val cooldown = 5L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        sendMenu(
            event.member ?: return,
            event.channel,
            args ?: throw NoArgumentsException
        )
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        sendMenu(
            event.member ?: return,
            event.textChannel,
            event.getOption("query")?.asString ?: return,
            event
        )
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (event.selectedOptions?.firstOrNull()?.value == "exit") {
                event.message?.delete()?.queue()

                return
            }

            if (id.last() == "videos") {
                event.message?.delete()?.queue()

                val videoUrl = event.selectedOptions?.firstOrNull()?.value ?: return

                play(videoUrl, event.textChannel, event.user, true)
            }
        }
    }

    private suspend fun sendMenu(
        member: Member,
        textChannel: TextChannel,
        query: String,
        ifFromSlashCommand: SlashCommandEvent? = null
    ) {
        member.voiceState?.channel?.let { vc ->
            val channel = vc.takeUnless { textChannel.guild.selfMember.voiceState?.channel == vc }

            channel?.let { textChannel.guild.audioManager.openAudioConnection(channel) }

            if (query matches YOUTUBE_LINK_REGEX) {
                play(query, textChannel, member.user, false, ifFromSlashCommand)

                return
            }

            val videos = withContext(MusicContext) { searchVideos(query) }

            if (videos.isEmpty())
                throw CommandException("No results have been found by the query!")

            val menu by lazy {
                val options = videos.map {
                    SelectOption.of(
                        it.snippet.title.take(24).run { if (length == 24) "$this\u2026" else this },
                        it.id
                    ).withDescription("${it.snippet.channelTitle} - ${asDuration(it.contentDetails.durationInMillis)}")
                }

                SelectionMenu.create("$name-${member.user.idLong}-videos")
                    .addOptions(
                        *options.toTypedArray(),
                        SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
                    ).build()
            }

            val embed = buildEmbed {
                color = Immutable.SUCCESS
                description = "Select a video you want to play!"
            }

            ifFromSlashCommand?.replyEmbeds(embed)?.addActionRow(menu)?.queue()
                ?: textChannel.sendMessageEmbeds(embed).setActionRow(menu).queue()
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    private fun play(query: String, channel: TextChannel, user: User, isId: Boolean, ifFromSlashCommand: SlashCommandEvent? = null) {
        val musicManager = channel.guild.audioPlayer ?: return

        PLAYER_MANAGER.loadItemOrdered(
            musicManager,
            "https://www.youtube.com/watch?v=".takeIf { isId }.orEmpty() + query,
            object : AudioLoadResultHandler {
                override fun trackLoaded(track: AudioTrack) {
                    val id = YOUTUBE_LINK_REGEX.find(query)?.groups?.get(3)?.value ?: query

                    track.userData = TrackUserData(
                        user,
                        channel,
                        "https://i3.ytimg.com/vi/$id/hqdefault.jpg",
                        announceQueued = musicManager.player.playingTrack !== null,
                        firstTrackPlaying = musicManager.player.playingTrack === null,
                        ifFromSlashCommand = ifFromSlashCommand
                    )
                    musicManager.scheduler += track
                }

                override fun playlistLoaded(playlist: AudioPlaylist) {
                    ifFromSlashCommand?.replySuccess( "[${playlist.name}]($query) playlist " +
                            "has been successfully added to the queue!")?.queue()
                        ?: channel.sendSuccess("${if (!playlist.isSearchResult) "[${playlist.name}](https://www.youtube.com/watch?v=$id)" else playlist.name} " +
                                "has been successfully added to the queue!").queue()

                    for (track in playlist.tracks) {
                        val thumbnail = YOUTUBE_LINK_REGEX.find(track.info.uri)?.groups?.get(3)?.value
                            ?.let { "https://i3.ytimg.com/vi/$it/hqdefault.jpg" }

                        track.userData = TrackUserData(user, channel, thumbnail, firstTrackPlaying = musicManager.player.playingTrack === null)
                        musicManager.scheduler += track
                    }
                }

                override fun noMatches() =
                    ifFromSlashCommand?.replyFailure("No results have been found by the query!")?.queue()
                        ?: channel.sendFailure("No results have been found by the query!").queue()

                override fun loadFailed(exception: FriendlyException) {
                    //exception.printStackTrace()

                    ifFromSlashCommand?.replyFailure("The track cannot be played!")?.queue()
                        ?: channel.sendFailure("The track cannot be played!").queue()
                }
            }
        )
    }
}