package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel
import net.dv8tion.jda.api.events.interaction.GenericAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class WikipediaCommand : SlashOnlyCommand {
    override val name = "wikipedia"
    override val description = "Searches a Wikipedia article by the provided query and sends some information about one"
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A search term", true)
            .setAutoComplete(true))
    override val cooldown = 7L

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val id = event.getOption("query")?.asString?.toIntOrNull()
            ?: throw CommandException("You have provided invalid arguments!")

        val deferred = event.deferReply().await()

        val isNSFW = event.channel.let { channel ->
            when {
                channel is IAgeRestrictedChannel -> channel.isNSFW
                channel.type.isThread -> channel.asThreadChannel()
                    .parentChannel
                    .asStandardGuildMessageChannel()
                    .isNSFW
                else -> false
            }
        }

        val article = getArticleInfo(id) ?: run {
            try {
                deferred.setFailureEmbed("No articles have been found by the query!").await()

                return
            } catch (_: ErrorResponseException) {
                throw CommandException("No articles have been found by the query!")
            }
        }

        if (!isNSFW && article.description.isNotEmpty()) {
            val nsfwProb = getPerspectiveApiProbability(
                client = WIKIPEDIA_HTTP_CLIENT,
                comment = article.description,
                mode = RequiredAttributes.SEXUALLY_EXPLICIT,
            )

            if (nsfwProb >= 0.9f) {
                try {
                    deferred.setFailureEmbed("The selected article cannot be display in a non-NSFW channel!")
                        .await()

                    return
                } catch (_: ErrorResponseException) {
                    throw CommandException("The selected article cannot be display in a non-NSFW channel!")
                }
            }
        }

        val embed = buildEmbed {
            var authorIcon = event.jda.selfUser.effectiveAvatarUrl
            var embedColor = Immutable.SUCCESS

            article.thumbnail?.let {
                authorIcon = it
                embedColor = getDominantColorByImageUrl(it)

                image = it
            }

            description = article.description.ifEmpty { "No description provided" }
                .limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

            color = embedColor

            author {
                name = article.title.limitTo(MessageEmbed.AUTHOR_MAX_LENGTH)
                url = "https://en.wikipedia.org/?curid=${article.id}"
                iconUrl = authorIcon
            }
        }

        deferred.editOriginalEmbeds(embed).queue(null) {
            event.channel.sendMessageEmbeds(embed).queue()
        }
    }

    override suspend fun invoke(event: GenericAutoCompleteInteractionEvent) {
        val interaction = event.interaction as CommandAutoCompleteInteraction

        interaction.getOption("query")?.asString?.let { query ->
            if (query.isNotEmpty()) {
                searchArticles(query, 10).let {
                    event.replyChoices(it.map { (t, i) ->
                        Command.Choice(t.limitTo(Command.Choice.MAX_NAME_LENGTH), i)
                    }).queue()
                }
            } else {
                event.replyChoiceStrings().queue()
            }
        }
    }
}