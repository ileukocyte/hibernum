package io.ileukocyte.hibernum.commands.utility

import com.google.api.services.youtube.model.Video

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import java.time.Instant

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageEmbed.DESCRIPTION_MAX_LENGTH
import net.dv8tion.jda.api.entities.User
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

class YouTubeCommand : TextCommand {
    override val name = "youtube"
    override val description = "Searches a YouTube video by the provided query and sends some information about one"
    override val aliases = setOf("yt", "yts", "ytsearch")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A link or a search term", true))
    override val usages = setOf(setOf("query".toClassicTextUsage()))
    override val cooldown = 5L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if ((args ?: throw NoArgumentsException) matches YOUTUBE_LINK_REGEX) {
            val video = suspendCoroutine<Video?> {
                val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails", "statistics"))

                list.id = listOf(YOUTUBE_LINK_REGEX.find(args)?.groups?.get(3)?.value)
                list.key = Immutable.YOUTUBE_API_KEY

                it.resume(list.execute().items.firstOrNull())
            } ?: throw CommandException("No results have been found by the query!")

            event.channel.sendMessageEmbeds(videoEmbed(video)).queue()
        } else {
            sendMenu(args, event.author, event.channel)
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()

        val query = event.getOption("query")?.asString ?: return

        if (query matches YOUTUBE_LINK_REGEX) {
            val video = suspendCoroutine {
                val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails", "statistics"))

                list.id = listOf(YOUTUBE_LINK_REGEX.find(query)?.groups?.get(3)?.value)
                list.key = Immutable.YOUTUBE_API_KEY

                it.resume(list.execute().items.firstOrNull())
            }

            if (video === null) {
                try {
                    deferred.setFailureEmbed("No results have been found by the query!").await()

                    return
                } catch (_: ErrorResponseException) {
                    throw CommandException("No results have been found by the query!")
                }
            }

            try {
                deferred.editOriginalEmbeds(videoEmbed(video)).await()
            } catch (_: ErrorResponseException) {
                event.replyEmbeds(videoEmbed(video)).queue()
            }
        } else {
            sendMenu(query, event.user, event.channel, deferred)
        }
    }

    override suspend fun invoke(event: SelectMenuInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            val deferred = event.deferEdit().await()

            if (event.selectedOptions.firstOrNull()?.value == "exit") {
                deferred.deleteOriginal().queue()

                return
            }

            if (id.last() == "videos") {
                val video = suspendCoroutine<Video?> {
                    val list = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails", "statistics"))

                    list.id = listOf(event.selectedOptions.firstOrNull()?.value ?: return@suspendCoroutine)
                    list.key = Immutable.YOUTUBE_API_KEY

                    it.resume(list.execute().items.firstOrNull())
                } ?: return

                try {
                    deferred.editOriginalComponents()
                        .setEmbeds(videoEmbed(video))
                        .await()
                } catch (_: Exception) {
                    event.channel.sendMessageEmbeds(videoEmbed(video))
                        .queue()
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun videoEmbed(video: Video) = buildEmbed {
        val pfp by lazy {
            val list = YOUTUBE.channels().list(listOf("snippet"))

            list.id = listOf(video.snippet.channelId)
            list.key = Immutable.YOUTUBE_API_KEY

            list.execute().items.firstOrNull()
                ?.snippet
                ?.thumbnails
                ?.default
                ?.url
        }

        image = video.snippet.thumbnails?.let {
            it.maxres?.url
                ?: it.standard?.url
                ?: it.high?.url
                ?: it.medium?.url
                ?: it.default?.url
        }

        pfp?.let { color = getDominantColorByImageUrl(it) }

        description = video.snippet.description.takeUnless { it.isEmpty() }
            ?.limitTo(DESCRIPTION_MAX_LENGTH)
            ?: "No description provided"

        title {
            title = video.snippet.title.limitTo(MessageEmbed.TITLE_MAX_LENGTH)
            url = "https://youtu.be/${video.id}"
        }

        video.statistics?.let { stats ->
            field {
                title = "Views"
                description = stats.viewCount?.toLong()?.toDecimalFormat("#,###")?.let {
                    "\uD83D\uDC41 \u2014 $it"
                } ?: "Unavailable"
                isInline = true
            }

            field {
                title = "Likes"
                description = stats.likeCount?.toLong()?.toDecimalFormat("#,###")?.let {
                    "\uD83D\uDC4D \u2014 $it"
                } ?: "Unavailable"
                isInline = true
            }

            field {
                title = "Comments"
                description = stats.commentCount?.toLong()?.toDecimalFormat("#,###")?.let {
                    "\uD83D\uDCAC \u2014 $it"
                } ?: "Unavailable"
                isInline = true
            }
        }

        field {
            title = "Duration"
            description = asDuration(video.contentDetails.durationInMillis)
            isInline = true
        }

        author {
            name = video.snippet.channelTitle
            iconUrl = pfp
            url = "https://www.youtube.com/channel/${video.snippet.channelId}"
        }

        timestamp = Instant.ofEpochMilli(video.snippet.publishedAt.value)

        footer { text = "Upload Date" }
    }

    private suspend fun sendMenu(
        query: String,
        author: User,
        channel: MessageChannel,
        ifFromSlashCommand: InteractionHook? = null,
    ) {
        val videos = searchVideos(query)

        if (videos.isEmpty()) {
            val error = "No results have been found by the query!"

            ifFromSlashCommand?.let {
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
                    "${it.snippet.channelTitle} - ${asDuration(it.contentDetails.durationInMillis)}"
                        .limitTo(SelectOption.DESCRIPTION_MAX_LENGTH)
                )
            }

            SelectMenu.create("$interactionName-${author.idLong}-videos")
                .addOptions(
                    *options.toTypedArray(),
                    SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
                ).build()
        }

        val embed = buildEmbed {
            color = Immutable.SUCCESS
            description = "Select the video you want to check details for!"
        }

        ifFromSlashCommand?.let {
            try {
                it.editOriginalEmbeds(embed).setActionRow(menu).await()
            } catch (_: ErrorResponseException) {
                channel.sendMessageEmbeds(embed).setActionRow(menu).queue()
            }
        } ?: channel.sendMessageEmbeds(embed).setActionRow(menu).queue()
    }
}