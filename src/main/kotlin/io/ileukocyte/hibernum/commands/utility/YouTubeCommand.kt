package io.ileukocyte.hibernum.commands.utility

import com.google.api.services.youtube.model.Video

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.utils.*

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.ExperimentalTime

class YouTubeCommand : Command {
    override val name = "youtube"
    override val description = "Searches a YouTube video by the provided query and sends some information about one"
    override val aliases = setOf("yt", "yts", "ytsearch")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A link or a search term", true)
    )
    override val usages = setOf("query")
    override val cooldown = 5L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if ((args ?: throw NoArgumentsException) matches YOUTUBE_LINK_REGEX) {
            val video = suspendCoroutine<Video?> {
                val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails"))

                list.id = listOf(YOUTUBE_LINK_REGEX.find(args)?.groups?.get(3)?.value)
                list.key = Immutable.YOUTUBE_API_KEY

                it.resume(list.execute().items.firstOrNull())
            } ?: throw CommandException("No results have been found by the query!")

            event.channel.sendMessageEmbeds(videoEmbed(video)).queue()
        } else {
            sendMenu(args, event.author, event.channel)
        }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val query = event.getOption("query")?.asString ?: return

        if (query matches YOUTUBE_LINK_REGEX) {
            val video = suspendCoroutine<Video?> {
                val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails"))

                list.id = listOf(YOUTUBE_LINK_REGEX.find(query)?.groups?.get(3)?.value)
                list.key = Immutable.YOUTUBE_API_KEY

                it.resume(list.execute().items.firstOrNull())
            } ?: throw CommandException("No results have been found by the query!")

            event.replyEmbeds(videoEmbed(video)).queue()
        } else {
            sendMenu(query, event.user, event.textChannel, event)
        }
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (event.selectedOptions?.firstOrNull()?.value == "exit") {
                event.message.delete().queue()
                return
            }

            if (id.last() == "videos") {
                val video = suspendCoroutine<Video?> {
                    val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails"))

                    list.id = listOf(event.selectedOptions?.firstOrNull()?.value ?: return@suspendCoroutine)
                    list.key = Immutable.YOUTUBE_API_KEY

                    it.resume(list.execute().items.firstOrNull())
                } ?: return

                event.editComponents().setEmbeds(videoEmbed(video, event.user.takeIf { id.getOrNull(1) == "slash" })).queue({}) {
                    event.channel.sendMessageEmbeds(videoEmbed(video, event.user.takeIf { id.getOrNull(1) == "slash" })).queue()
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    @OptIn(ExperimentalTime::class)
    private fun videoEmbed(video: Video, author: User? = null) = buildEmbed {
        color = Immutable.SUCCESS
        image = video.snippet.thumbnails?.let {
            it.maxres.url ?: it.standard.url ?: it.high.url ?: it.medium.url ?: it.default.url
        }
        description =
            video.snippet.description.takeUnless { it.isEmpty() }?.limitTo(4096) ?: "No description provided"

        title {
            title = video.snippet.title
            url = "https://youtu.be/${video.id}"
        }

        field {
            title = "Duration"
            description = asDuration(video.contentDetails.durationInMillis)
            isInline = true
        }

        author {
            name = video.snippet.channelTitle
            url = "https://www.youtube.com/channel/${video.snippet.channelId}"
        }

        author?.let {
            footer {
                text = "Requested by ${it.asTag}"
                iconUrl = it.effectiveAvatarUrl
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun sendMenu(
        query: String,
        author: User,
        textChannel: TextChannel,
        ifFromSlashCommand: SlashCommandEvent? = null
    ) {
        val videos = searchVideos(query)

        if (videos.isEmpty())
            throw CommandException("No results have been found by the query!")

        val menu by lazy {
            val options = videos.map {
                SelectOption.of(
                    it.snippet.title.limitTo(100),
                    it.id
                ).withDescription(
                    "${it.snippet.channelTitle} - ${asDuration(it.contentDetails.durationInMillis)}".limitTo(100)
                )
            }

            SelectionMenu.create("$name-${author.idLong}${"-slash".takeIf { ifFromSlashCommand !== null }.orEmpty()}-videos")
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
    }
}