package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.RequiredAttributes
import io.ileukocyte.hibernum.utils.getPerspectiveApiProbability

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class UrbanCommand : TextCommand {
    override val name = "urban"
    override val description = "Sends an Urban Dictionary definition of the specified term (executes faster in a NSFW channel)"
    override val aliases = setOf("ud", "urbandictionary")
    override val cooldown = 5L
    override val usages = setOf(setOf("term".toClassicTextUsage()))
    override val options = setOf(
        OptionData(OptionType.STRING, "term", "A word or a phrase to define", true))

    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    private val client = Immutable.HTTP_CLIENT.config {
        install(ContentNegotiation) { json(jsonSerializer) }
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val query = args ?: throw NoArgumentsException

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

        val deferred = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = "Looking for a non-NSFW definition\u2026"

            footer { text = "The command will execute faster in a NSFW channel!" }
        }.takeUnless { isNSFW }?.await()

        val embed = try {
            urbanEmbed(event.jda, lookForDefinition(client, query, isNSFW))
        } catch (e: Exception) {
            defaultEmbed(e.message ?: "An exception has occurred!", EmbedType.FAILURE)
        }

        deferred?.editMessageEmbeds(embed)?.queue({}) {
            event.channel.sendMessageEmbeds(embed).queue()
        } ?: event.channel.sendMessageEmbeds(embed).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val query = event.getOption("term")?.asString ?: return

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

        val deferred = event.deferReply().takeIf { isNSFW }?.await()
            ?: event.replyEmbed {
                color = Immutable.SUCCESS
                description = "Looking for a non-NSFW definition\u2026"

                footer { text = "The command will execute faster in a NSFW channel!" }
            }.await()

        val embed = try {
            urbanEmbed(event.jda, lookForDefinition(client, query, isNSFW))
        } catch (e: Exception) {
            defaultEmbed(e.message ?: "An exception has occurred!", EmbedType.FAILURE)
        }

        deferred.editOriginalEmbeds(embed).queue(null) {
            event.channel.sendMessageEmbeds(embed).queue()
        }
    }

    private suspend fun lookForDefinition(client: HttpClient, query: String, isNSFWChannel: Boolean): Term {
        val api = "http://api.urbandictionary.com/v0/define"

        val urbanRequest = client
            .get(api) { parameter("term", query) }
            .apply {
                if (status != HttpStatusCode.OK) {
                    throw CommandException("Urban Dictionary is not available at the moment!")
                }
            }.body<JsonObject>()

        val response = urbanRequest["list"]
            ?.jsonArray
            ?.takeUnless { it.isEmpty() }
            ?.take(10)

        val result = response?.firstOrNull()?.jsonObject
            ?: throw CommandException("No definition has been found by the query!")

        var term = jsonSerializer.decodeFromJsonElement<Term>(result)

        if (!isNSFWChannel) {
            val hasFoundNsfw = arrayOf(term.definition, term.example)
                .filter { it.isNotEmpty() }
                .mapNotNull {
                    try {
                        getPerspectiveApiProbability(client, it, RequiredAttributes.SEXUALLY_EXPLICIT)
                    } catch (_: IllegalStateException) {
                        null
                    }
                }.any { it >= 0.9f }

            if (hasFoundNsfw) {
                val nonNsfwResult = response
                    .filterIsInstance<JsonObject>()
                    .filter { it != result }
                    .firstOrNull {
                        arrayOf(it["definition"], it["example"])
                            .mapNotNull { e ->
                                val property = e?.jsonPrimitive?.content?.takeUnless { s -> s.isEmpty() }

                                property?.let { content ->
                                    try {
                                        getPerspectiveApiProbability(
                                            client,
                                            content,
                                            RequiredAttributes.SEXUALLY_EXPLICIT,
                                        )
                                    } catch (_: IllegalStateException) {
                                        null
                                    }
                                }
                            }.none { p -> p >= 0.9f }
                    } ?: throw CommandException(
                        "No definition that does not contain any sexually explicit content has been found for the query!",
                        footer = "Try using the command in a channel that is intended for NSFW bot commands!",
                    )

                term = jsonSerializer.decodeFromJsonElement(nonNsfwResult)
            }
        }

        with(term) {
            val linkRegex = Regex("\\[([^]]+)]")

            for (match in linkRegex.findAll(definition)) {
                val params = listOf(("term" to match.value.removePrefix("[").removeSuffix("]")))
                    .formUrlEncode()

                definition = definition
                    .replace(match.value, "${match.value}(https://www.urbandictionary.com/define.php?$params)")
            }

            for (match in linkRegex.findAll(example)) {
                val params = listOf(("term" to match.value.removePrefix("[").removeSuffix("]")))
                    .formUrlEncode()

                example = example
                    .replace(match.value, "${match.value}(https://www.urbandictionary.com/define.php?$params)")
            }
        }

        return term
    }

    private fun urbanEmbed(jda: JDA, term: Term) = buildEmbed {
        color = Immutable.SUCCESS
        timestamp = term.dateAdded.toJavaInstant()

        author {
            name = term.word
            url = term.url
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        field {
            title = "Definition"
            description = term.definition.limitTo(1024)
        }

        term.example.limitTo(1024).takeUnless { it.isEmpty() }?.let {
            field {
                title = "Example"
                description = it
            }
        }

        field {
            title = "Author"
            description = term.author
        }

        field {
            title = "Likes"
            description = "\uD83D\uDC4D \u2014 ${term.likes}"
        }

        field {
            title = "Dislikes"
            description = "\uD83D\uDC4E \u2014 ${term.dislikes}"
        }
    }

    @Serializable
    private data class Term(
        val word: String,
        val author: String,
        var definition: String,
        var example: String,
        @SerialName("thumbs_up") val likes: Int,
        @SerialName("thumbs_down") val dislikes: Int,
        @SerialName("written_on") val dateAdded: Instant,
    ) {
        val url = "https://www.urbandictionary.com/define.php?" + listOf(("term" to "word")).formUrlEncode()
    }
}